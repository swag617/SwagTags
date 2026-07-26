package com.swag.swagtags.database;

import com.swag.swagtags.TagPlugin;
import com.swag.swagtags.models.LoanedTag;
import com.swag.swagtags.models.PendingTag;
import com.swag.swagtags.models.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Persists all SwagTags data (custom/preset tags, equipped tags, active loans,
 * pending approval requests, and player credit balances) to SwagAPI's shared
 * database service, replacing the previous ad-hoc YAML flatfile storage.
 * <p>
 * Connections are borrowed from SwagAPI's shared HikariCP pool via
 * {@code dbService.getConnection()} and returned (closed) after each use —
 * this class does not own a connection or pool itself.
 * <p>
 * <b>Threading:</b> every per-row write (upsert/delete on a single tag, loan,
 * pending request, equipped-tag row, or credit balance) is dispatched via
 * {@code dbService.executeAsync(...)} so the JDBC round-trip never happens on
 * the calling thread (in practice, always the main thread — see the call
 * sites in {@link TagPlugin}). Callers update their in-memory state
 * synchronously before calling these methods, so the async persistence is a
 * true fire-and-forget: gameplay never waits on it. None of these write
 * bodies touch the Bukkit API, so no hop back to the main thread is needed
 * after they complete.
 * <p>
 * The bulk {@code loadAllX()} reads are the exception — they run
 * synchronously on the calling thread. They are only ever called once, from
 * {@code TagPlugin#onEnable()} during server startup (the same phase that
 * already blocks on {@link #connect()}/table creation), so this is a bounded,
 * one-time cost rather than an ongoing per-action one.
 * <p>
 * A small per-row lock (see {@link #lockFor(String)}) protects the
 * update-then-insert-if-absent pattern used by every upsert/delete pair
 * against a specific row (e.g. {@code upsertTag}/{@code deleteTag} both lock
 * on {@code "tag:" + tagId}). That sequence is not atomic on its own — two
 * concurrent writers touching the same row could both see zero rows affected
 * by the UPDATE and both attempt the INSERT, causing a duplicate-key failure
 * or a lost update. Locking is scoped to the individual row's key (not a
 * single plugin-wide lock) so unrelated rows/players are never serialized
 * against each other; HikariCP's pool already handles safe concurrent
 * connection use, so no broader locking is needed.
 */
public class DatabaseManager {
    private final TagPlugin plugin;
    private final com.SwagDev.SwagAPI.api.IDatabaseService dbService;

    /** Per-row locks, created lazily and keyed by "<table>:<id>" — see class javadoc. */
    private final Map<String, Object> rowLocks = new ConcurrentHashMap<>();

    public DatabaseManager(TagPlugin plugin, com.SwagDev.SwagAPI.api.IDatabaseService dbService) {
        this.plugin = plugin;
        this.dbService = dbService;
    }

    private Object lockFor(String key) {
        return rowLocks.computeIfAbsent(key, k -> new Object());
    }

    /**
     * Creates tables (if absent) on the shared database, then runs the one-time
     * legacy YAML import (see {@link #importLegacyYamlIfPresent()}).
     */
    public void connect() {
        try {
            createTables();
            plugin.getLogger().info("SwagTags: successfully connected to the SwagAPI shared database.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "SwagTags: could not create tables on the shared database!", e);
            return;
        }

        importLegacyYamlIfPresent();
    }

    /**
     * No-op — the connection pool is owned and closed by SwagAPI, not this plugin.
     * Retained so the shutdown call site still compiles / reads clearly.
     */
    public void close() {
        // Pool lifecycle belongs to SwagAPI.
    }

    private Connection getConnection() throws SQLException {
        return dbService.getConnection();
    }

    private void createTables() throws SQLException {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("CREATE TABLE IF NOT EXISTS swagtags_tags (" +
                    "id VARCHAR(32) PRIMARY KEY," +
                    "owner_uuid VARCHAR(36) NOT NULL," +
                    "suffix VARCHAR(255) NOT NULL," +
                    "type VARCHAR(16) NOT NULL DEFAULT 'CUSTOM'," +
                    "created_at BIGINT NOT NULL)");

            statement.execute("CREATE INDEX IF NOT EXISTS idx_swagtags_tags_owner ON swagtags_tags(owner_uuid)");

            statement.execute("CREATE TABLE IF NOT EXISTS swagtags_equipped (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "tag_id VARCHAR(32) NOT NULL)");

            statement.execute("CREATE TABLE IF NOT EXISTS swagtags_loans (" +
                    "tag_id VARCHAR(32) PRIMARY KEY," +
                    "owner_uuid VARCHAR(36) NOT NULL," +
                    "loaned_to_uuid VARCHAR(36) NOT NULL," +
                    "expiry BIGINT NOT NULL)");

            statement.execute("CREATE TABLE IF NOT EXISTS swagtags_pending (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "suffix VARCHAR(255) NOT NULL," +
                    "credits_escrowed INTEGER NOT NULL DEFAULT 0," +
                    "timestamp BIGINT NOT NULL)");

            statement.execute("CREATE TABLE IF NOT EXISTS swagtags_credits (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "credits INTEGER NOT NULL DEFAULT 0)");
        }
    }

    // ========================================================================================
    // Tags
    // ========================================================================================

    /** Loads every tag row into a map keyed by tag id, mirroring the old tags.yml load. */
    public Map<String, Tag> loadAllTags() {
        Map<String, Tag> result = new LinkedHashMap<>();
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT id, owner_uuid, suffix, type, created_at FROM swagtags_tags")) {
            while (rs.next()) {
                try {
                    String id = rs.getString("id");
                    UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                    String suffix = rs.getString("suffix");
                    Tag.TagType type = Tag.TagType.valueOf(rs.getString("type"));
                    long created = rs.getLong("created_at");
                    result.put(id, new Tag(id, owner, suffix, type, created));
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to parse tag row: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load tags from database", e);
        }
        return result;
    }

    /**
     * Inserts or updates a single tag row (update-then-insert-if-absent, portable across
     * SQLite/MySQL). Dispatched async — see class javadoc; callers must not rely on this
     * having completed by the time the call returns.
     */
    public void upsertTag(Tag tag) {
        dbService.executeAsync(() -> {
            synchronized (lockFor("tag:" + tag.getId())) {
                try (Connection connection = getConnection()) {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE swagtags_tags SET owner_uuid = ?, suffix = ?, type = ?, created_at = ? WHERE id = ?")) {
                        update.setString(1, tag.getOwnerUUID().toString());
                        update.setString(2, tag.getSuffix());
                        update.setString(3, tag.getType().name());
                        update.setLong(4, tag.getCreatedTimestamp());
                        update.setString(5, tag.getId());
                        if (update.executeUpdate() > 0) return;
                    }
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO swagtags_tags (id, owner_uuid, suffix, type, created_at) VALUES (?, ?, ?, ?, ?)")) {
                        insert.setString(1, tag.getId());
                        insert.setString(2, tag.getOwnerUUID().toString());
                        insert.setString(3, tag.getSuffix());
                        insert.setString(4, tag.getType().name());
                        insert.setLong(5, tag.getCreatedTimestamp());
                        insert.executeUpdate();
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to save tag " + tag.getId(), e);
                }
            }
        });
    }

    public void deleteTag(String tagId) {
        dbService.executeAsync(() -> {
            synchronized (lockFor("tag:" + tagId)) {
                try (Connection connection = getConnection();
                     PreparedStatement ps = connection.prepareStatement("DELETE FROM swagtags_tags WHERE id = ?")) {
                    ps.setString(1, tagId);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to delete tag " + tagId, e);
                }
            }
        });
    }

    // ========================================================================================
    // Equipped tags
    // ========================================================================================

    public Map<UUID, String> loadAllEquippedTags() {
        Map<UUID, String> result = new HashMap<>();
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT uuid, tag_id FROM swagtags_equipped")) {
            while (rs.next()) {
                try {
                    result.put(UUID.fromString(rs.getString("uuid")), rs.getString("tag_id"));
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to parse equipped-tag row: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load equipped tags from database", e);
        }
        return result;
    }

    public void setEquippedTag(UUID uuid, String tagId) {
        dbService.executeAsync(() -> {
            synchronized (lockFor("equipped:" + uuid)) {
                try (Connection connection = getConnection()) {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE swagtags_equipped SET tag_id = ? WHERE uuid = ?")) {
                        update.setString(1, tagId);
                        update.setString(2, uuid.toString());
                        if (update.executeUpdate() > 0) return;
                    }
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO swagtags_equipped (uuid, tag_id) VALUES (?, ?)")) {
                        insert.setString(1, uuid.toString());
                        insert.setString(2, tagId);
                        insert.executeUpdate();
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to set equipped tag for " + uuid, e);
                }
            }
        });
    }

    public void removeEquippedTag(UUID uuid) {
        dbService.executeAsync(() -> {
            synchronized (lockFor("equipped:" + uuid)) {
                try (Connection connection = getConnection();
                     PreparedStatement ps = connection.prepareStatement("DELETE FROM swagtags_equipped WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to remove equipped tag for " + uuid, e);
                }
            }
        });
    }

    // ========================================================================================
    // Loans
    // ========================================================================================

    public Map<String, LoanedTag> loadAllLoans() {
        Map<String, LoanedTag> result = new HashMap<>();
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT tag_id, owner_uuid, loaned_to_uuid, expiry FROM swagtags_loans")) {
            while (rs.next()) {
                try {
                    String tagId = rs.getString("tag_id");
                    UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                    UUID loanedTo = UUID.fromString(rs.getString("loaned_to_uuid"));
                    long expiry = rs.getLong("expiry");
                    result.put(tagId, new LoanedTag(tagId, owner, loanedTo, expiry));
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to parse loan row: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load loans from database", e);
        }
        return result;
    }

    public void upsertLoan(LoanedTag loan) {
        dbService.executeAsync(() -> {
            synchronized (lockFor("loan:" + loan.getTagId())) {
                try (Connection connection = getConnection()) {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE swagtags_loans SET owner_uuid = ?, loaned_to_uuid = ?, expiry = ? WHERE tag_id = ?")) {
                        update.setString(1, loan.getOwnerUUID().toString());
                        update.setString(2, loan.getLoanedToUUID().toString());
                        update.setLong(3, loan.getExpiryTime());
                        update.setString(4, loan.getTagId());
                        if (update.executeUpdate() > 0) return;
                    }
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO swagtags_loans (tag_id, owner_uuid, loaned_to_uuid, expiry) VALUES (?, ?, ?, ?)")) {
                        insert.setString(1, loan.getTagId());
                        insert.setString(2, loan.getOwnerUUID().toString());
                        insert.setString(3, loan.getLoanedToUUID().toString());
                        insert.setLong(4, loan.getExpiryTime());
                        insert.executeUpdate();
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to save loan for tag " + loan.getTagId(), e);
                }
            }
        });
    }

    public void deleteLoan(String tagId) {
        dbService.executeAsync(() -> {
            synchronized (lockFor("loan:" + tagId)) {
                try (Connection connection = getConnection();
                     PreparedStatement ps = connection.prepareStatement("DELETE FROM swagtags_loans WHERE tag_id = ?")) {
                    ps.setString(1, tagId);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to delete loan for tag " + tagId, e);
                }
            }
        });
    }

    // ========================================================================================
    // Pending custom-tag approvals
    // ========================================================================================

    public Map<UUID, PendingTag> loadAllPendingTags() {
        Map<UUID, PendingTag> result = new HashMap<>();
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT uuid, suffix, credits_escrowed, timestamp FROM swagtags_pending")) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String suffix = rs.getString("suffix");
                    int credits = rs.getInt("credits_escrowed");
                    long timestamp = rs.getLong("timestamp");
                    result.put(uuid, new PendingTag(suffix, credits, timestamp));
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to parse pending-tag row: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load pending tags from database", e);
        }
        return result;
    }

    public void upsertPendingTag(UUID uuid, PendingTag pending) {
        dbService.executeAsync(() -> {
            synchronized (lockFor("pending:" + uuid)) {
                try (Connection connection = getConnection()) {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE swagtags_pending SET suffix = ?, credits_escrowed = ?, timestamp = ? WHERE uuid = ?")) {
                        update.setString(1, pending.getSuffix());
                        update.setInt(2, pending.getCreditsEscrowed());
                        update.setLong(3, pending.getTimestamp());
                        update.setString(4, uuid.toString());
                        if (update.executeUpdate() > 0) return;
                    }
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO swagtags_pending (uuid, suffix, credits_escrowed, timestamp) VALUES (?, ?, ?, ?)")) {
                        insert.setString(1, uuid.toString());
                        insert.setString(2, pending.getSuffix());
                        insert.setInt(3, pending.getCreditsEscrowed());
                        insert.setLong(4, pending.getTimestamp());
                        insert.executeUpdate();
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to save pending tag for " + uuid, e);
                }
            }
        });
    }

    public void deletePendingTag(UUID uuid) {
        dbService.executeAsync(() -> {
            synchronized (lockFor("pending:" + uuid)) {
                try (Connection connection = getConnection();
                     PreparedStatement ps = connection.prepareStatement("DELETE FROM swagtags_pending WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to delete pending tag for " + uuid, e);
                }
            }
        });
    }

    // ========================================================================================
    // Player credits
    // ========================================================================================

    /** Bulk-loads every known credit balance, mirroring the old playerdata/*.yml startup scan. */
    public Map<UUID, Integer> loadAllPlayerCredits() {
        Map<UUID, Integer> result = new HashMap<>();
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT uuid, credits FROM swagtags_credits")) {
            while (rs.next()) {
                try {
                    result.put(UUID.fromString(rs.getString("uuid")), rs.getInt("credits"));
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to parse credits row: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player credits from database", e);
        }
        return result;
    }

    /**
     * Returns the stored credit balance for a player, or {@code null} if they have no row yet.
     * This is a plain synchronous read with no locking (reads have no read-modify-write hazard).
     * Callers that need this off the main thread (e.g. the join-time cache warm-up in
     * {@code TagPlugin}) are expected to wrap the call in {@code dbService.queryAsync(...)}
     * themselves; the handful of unavoidable synchronous fallback reads (e.g. a brand-new
     * player's very first credits check, before the join warm-up completes) call it directly.
     */
    public Integer loadPlayerCredits(UUID uuid) {
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT credits FROM swagtags_credits WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("credits");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load credits for " + uuid, e);
        }
        return null;
    }

    public void setPlayerCredits(UUID uuid, int credits) {
        dbService.executeAsync(() -> {
            synchronized (lockFor("credits:" + uuid)) {
                try (Connection connection = getConnection()) {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE swagtags_credits SET credits = ? WHERE uuid = ?")) {
                        update.setInt(1, credits);
                        update.setString(2, uuid.toString());
                        if (update.executeUpdate() > 0) return;
                    }
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO swagtags_credits (uuid, credits) VALUES (?, ?)")) {
                        insert.setString(1, uuid.toString());
                        insert.setInt(2, credits);
                        insert.executeUpdate();
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to save credits for " + uuid, e);
                }
            }
        });
    }

    // ========================================================================================
    // Legacy YAML → SwagAPI database migration (one-time, on first startup after this update)
    // ========================================================================================

    /**
     * Detects the legacy flatfiles this plugin used before the SwagAPI migration
     * (tags.yml, equipped.yml, loans.yml, pending.yml, playerdata/*.yml) and, if found,
     * imports their contents into the shared database. Existing rows in the shared database
     * are never overwritten (insert-if-absent), so this is safe to re-run.
     * <p>
     * Successfully imported files are renamed with an ".imported" suffix (and the
     * playerdata folder likewise) rather than deleted, so nothing is destroyed if a
     * problem is discovered after the fact — see the class-level migration note in
     * TagPlugin for the rollback path.
     */
    private void importLegacyYamlIfPresent() {
        File dataFolder = plugin.getDataFolder();
        boolean mysql = dbService.isMySQL();

        importLegacyTagsYaml(new File(dataFolder, "tags.yml"), mysql);
        importLegacyEquippedYaml(new File(dataFolder, "equipped.yml"), mysql);
        importLegacyLoansYaml(new File(dataFolder, "loans.yml"), mysql);
        importLegacyPendingYaml(new File(dataFolder, "pending.yml"), mysql);
        importLegacyPlayerDataFolder(new File(dataFolder, "playerdata"), mysql);
    }

    private void importLegacyTagsYaml(File file, boolean mysql) {
        if (!file.isFile()) return;
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        java.util.Set<String> keys = config.getKeys(false);
        if (keys.isEmpty()) {
            renameImported(file);
            return;
        }

        int imported = 0;
        String insertSql = (mysql ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ")
                + "swagtags_tags (id, owner_uuid, suffix, type, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(insertSql)) {
            for (String tagId : keys) {
                ConfigurationSection section = config.getConfigurationSection(tagId);
                if (section == null) continue;
                try {
                    ps.setString(1, tagId);
                    ps.setString(2, UUID.fromString(section.getString("owner")).toString());
                    ps.setString(3, section.getString("suffix"));
                    ps.setString(4, section.getString("type", "CUSTOM"));
                    ps.setLong(5, section.getLong("created", System.currentTimeMillis()));
                    imported += ps.executeUpdate();
                } catch (Exception e) {
                    plugin.getLogger().warning("Legacy import: failed to import tag '" + tagId + "': " + e.getMessage());
                }
            }
            plugin.getLogger().info("Legacy import: imported " + imported + " tag(s) from tags.yml.");
            renameImported(file);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Legacy import of tags.yml failed. The file has been left in place so you can retry.", e);
        }
    }

    private void importLegacyEquippedYaml(File file, boolean mysql) {
        if (!file.isFile()) return;
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        java.util.Set<String> keys = config.getKeys(false);
        if (keys.isEmpty()) {
            renameImported(file);
            return;
        }

        int imported = 0;
        String insertSql = (mysql ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ")
                + "swagtags_equipped (uuid, tag_id) VALUES (?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(insertSql)) {
            for (String key : keys) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String tagId = config.getString(key);
                    ps.setString(1, uuid.toString());
                    ps.setString(2, tagId);
                    imported += ps.executeUpdate();
                } catch (Exception e) {
                    plugin.getLogger().warning("Legacy import: failed to import equipped tag for '" + key + "': " + e.getMessage());
                }
            }
            plugin.getLogger().info("Legacy import: imported " + imported + " equipped-tag row(s) from equipped.yml.");
            renameImported(file);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Legacy import of equipped.yml failed. The file has been left in place so you can retry.", e);
        }
    }

    private void importLegacyLoansYaml(File file, boolean mysql) {
        if (!file.isFile()) return;
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        java.util.Set<String> keys = config.getKeys(false);
        if (keys.isEmpty()) {
            renameImported(file);
            return;
        }

        int imported = 0;
        String insertSql = (mysql ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ")
                + "swagtags_loans (tag_id, owner_uuid, loaned_to_uuid, expiry) VALUES (?, ?, ?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(insertSql)) {
            for (String tagId : keys) {
                ConfigurationSection section = config.getConfigurationSection(tagId);
                if (section == null) continue;
                try {
                    ps.setString(1, tagId);
                    ps.setString(2, UUID.fromString(section.getString("owner")).toString());
                    ps.setString(3, UUID.fromString(section.getString("loanedTo")).toString());
                    ps.setLong(4, section.getLong("expiry"));
                    imported += ps.executeUpdate();
                } catch (Exception e) {
                    plugin.getLogger().warning("Legacy import: failed to import loan for tag '" + tagId + "': " + e.getMessage());
                }
            }
            plugin.getLogger().info("Legacy import: imported " + imported + " loan(s) from loans.yml.");
            renameImported(file);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Legacy import of loans.yml failed. The file has been left in place so you can retry.", e);
        }
    }

    private void importLegacyPendingYaml(File file, boolean mysql) {
        if (!file.isFile()) return;
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        java.util.Set<String> keys = config.getKeys(false);
        if (keys.isEmpty()) {
            renameImported(file);
            return;
        }

        int imported = 0;
        String insertSql = (mysql ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ")
                + "swagtags_pending (uuid, suffix, credits_escrowed, timestamp) VALUES (?, ?, ?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(insertSql)) {
            for (String key : keys) {
                ConfigurationSection section = config.getConfigurationSection(key);
                if (section == null) continue;
                try {
                    UUID uuid = UUID.fromString(key);
                    ps.setString(1, uuid.toString());
                    ps.setString(2, section.getString("suffix"));
                    ps.setInt(3, section.getInt("creditsEscrowed", 0));
                    ps.setLong(4, section.getLong("timestamp", System.currentTimeMillis()));
                    imported += ps.executeUpdate();
                } catch (Exception e) {
                    plugin.getLogger().warning("Legacy import: failed to import pending tag for '" + key + "': " + e.getMessage());
                }
            }
            plugin.getLogger().info("Legacy import: imported " + imported + " pending tag request(s) from pending.yml.");
            renameImported(file);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Legacy import of pending.yml failed. The file has been left in place so you can retry.", e);
        }
    }

    private void importLegacyPlayerDataFolder(File folder, boolean mysql) {
        if (!folder.isDirectory()) return;
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            // Nothing to import — still fine to leave an empty folder in place, no rename needed.
            return;
        }

        int imported = 0;
        String insertSql = (mysql ? "INSERT IGNORE INTO " : "INSERT OR IGNORE INTO ")
                + "swagtags_credits (uuid, credits) VALUES (?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(insertSql)) {
            for (File file : files) {
                try {
                    UUID uuid = UUID.fromString(file.getName().replace(".yml", ""));
                    int credits = YamlConfiguration.loadConfiguration(file).getInt("credits", 0);
                    ps.setString(1, uuid.toString());
                    ps.setInt(2, credits);
                    imported += ps.executeUpdate();
                } catch (Exception e) {
                    plugin.getLogger().warning("Legacy import: failed to import player credits from '" + file.getName() + "': " + e.getMessage());
                }
            }
            plugin.getLogger().info("Legacy import: imported " + imported + " player credit balance(s) from playerdata/.");

            File renamed = new File(folder.getParentFile(), "playerdata.imported");
            if (folder.renameTo(renamed)) {
                plugin.getLogger().info("Renamed 'playerdata' to 'playerdata.imported' so it won't be imported again.");
            } else {
                plugin.getLogger().warning("Legacy import of playerdata/ succeeded but the folder could not be renamed. " +
                        "Please rename or remove it manually to prevent re-importing on next restart.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Legacy import of playerdata/ failed. The folder has been left in place so you can retry.", e);
        }
    }

    /** Renames a successfully-imported legacy file to "<name>.imported" so it isn't re-imported. */
    private void renameImported(File file) {
        File renamed = new File(file.getParentFile(), file.getName() + ".imported");
        if (!file.renameTo(renamed)) {
            plugin.getLogger().warning("Legacy import of '" + file.getName() + "' succeeded but the file could not be renamed. " +
                    "Please rename or remove it manually to prevent re-importing on next restart.");
        }
    }
}
