package com.swag.swagtags;

import com.swag.swagtags.commands.TagCommand;
import com.swag.swagtags.database.DatabaseManager;
import com.swag.swagtags.gui.JobTagGUI;
import com.swag.swagtags.gui.TagApprovalGUI;
import com.swag.swagtags.gui.TagBuyConfirmGUI;
import com.swag.swagtags.gui.TagListGUI;
import com.swag.swagtags.gui.TagPlaceholder;
import com.swag.swagtags.gui.TagWithdrawGUI;
import com.swag.swagtags.models.LoanedTag;
import com.swag.swagtags.models.PendingTag;
import com.swag.swagtags.models.Tag;
import com.swag.swagtags.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class TagPlugin extends JavaPlugin implements Listener {
    private static TagPlugin instance;

    private Map<UUID, PendingTag> pendingCustomTags = new ConcurrentHashMap<>();
    private Map<String, Tag> allTags = new ConcurrentHashMap<>();
    private Map<UUID, List<String>> playerOwnedTags = new ConcurrentHashMap<>();
    private Map<UUID, String> playerEquippedTag = new ConcurrentHashMap<>();
    // Bug 1: use ConcurrentHashMap for thread safety
    private Map<UUID, Integer> playerCredits = new ConcurrentHashMap<>();
    private final Map<UUID, String> adminOverrideSuffix = new ConcurrentHashMap<>();

    // Loan System
    private final Map<String, LoanedTag> activeLoans = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerEquippedLoanedTag = new ConcurrentHashMap<>();

    // ── SwagAPI service references ─────────────────────────────────────────────
    private com.SwagDev.SwagAPI.api.IDatabaseService dbService;
    private DatabaseManager databaseManager;

    private TagApprovalGUI tagApprovalGUI;
    private TagListGUI tagListGUI;
    private TagBuyConfirmGUI tagBuyConfirmGUI;
    private JobTagGUI jobTagGUI;
    private TagWithdrawGUI tagWithdrawGUI;

    public static TagPlugin getInstance() { return instance; }
    public TagApprovalGUI getTagApprovalGUI() { return tagApprovalGUI; }
    public TagListGUI getTagListGUI() { return tagListGUI; }
    public TagBuyConfirmGUI getTagBuyConfirmGUI() { return tagBuyConfirmGUI; }
    public JobTagGUI getJobTagGUI() { return jobTagGUI; }
    public TagWithdrawGUI getTagWithdrawGUI() { return tagWithdrawGUI; }

    private static final int CURRENT_CONFIG_VERSION = 3;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        migrateConfig();

        // ── Step 1: Hook SwagAPI (must be first) — SwagTags now has a hard dependency on
        // SwagAPI's shared database service for all persistence (tags, loans, pending
        // approvals, credits). If it isn't present, disable rather than fall back to YAML.
        if (!hookSwagAPI()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.databaseManager = new DatabaseManager(this, dbService);
        this.databaseManager.connect();

        this.tagApprovalGUI = new TagApprovalGUI(this);
        this.tagListGUI = new TagListGUI(this);
        this.tagBuyConfirmGUI = new TagBuyConfirmGUI(this);

        TagCommand tagCommand = new TagCommand(this);
        getCommand("tag").setExecutor(tagCommand);
        getCommand("tag").setTabCompleter(tagCommand);

        this.jobTagGUI = new JobTagGUI(this);
        this.tagWithdrawGUI = new TagWithdrawGUI(this);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(this.tagApprovalGUI, this);
        getServer().getPluginManager().registerEvents(this.tagListGUI, this);
        getServer().getPluginManager().registerEvents(this.tagBuyConfirmGUI, this);
        getServer().getPluginManager().registerEvents(this.jobTagGUI, this);
        getServer().getPluginManager().registerEvents(this.tagWithdrawGUI, this);

        loadAllTags();
        loadAllEquippedTags();
        loadAllPlayerCredits();
        loadAllLoans();
        loadAllPendingTags();

        Bukkit.getScheduler().runTaskTimer(this, this::checkLoanExpiry, 20L * 60, 20L * 60);
        Bukkit.getScheduler().runTaskTimer(this, this::checkPendingTagExpiry, 20L * 60 * 30, 20L * 60 * 30);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new TagPlaceholder(this).register();
        }

        getLogger().info("SwagTags enabled! TAB plugin will handle display via %swagtags_tag%");
    }

    @Override
    public void onDisable() {
        // MIGRATED: persistence is now granular — every mutation (equip, delete, loan,
        // pending approval, credit change) already upserts/deletes its own row via
        // DatabaseManager at the time it happens, so no bulk "save everything" pass is
        // needed on shutdown. Those writes are dispatched async (dbService.executeAsync),
        // but SwagAPI's IDatabaseService falls back to running them synchronously if SwagAPI
        // itself is already disabled by the time a write fires, so nothing is silently
        // dropped during a normal shutdown. The SwagAPI-owned connection pool is closed by
        // SwagAPI itself, not here.
        getLogger().info("SwagTags disabled.");
    }

    /**
     * Hooks SwagAPI's shared IDatabaseService. This is now a hard requirement for SwagTags
     * (see the SwagJobsPlugin#hookSwagAPI pattern this mirrors) — if SwagAPI isn't loaded,
     * SwagTags disables itself rather than silently falling back to YAML flatfiles.
     */
    private boolean hookSwagAPI() {
        org.bukkit.plugin.ServicesManager sm = getServer().getServicesManager();

        org.bukkit.plugin.RegisteredServiceProvider<com.SwagDev.SwagAPI.api.IDatabaseService> dbProv =
                sm.getRegistration(com.SwagDev.SwagAPI.api.IDatabaseService.class);
        if (dbProv == null) {
            getLogger().severe("SwagAPI IDatabaseService not found! Is SwagAPI loaded? Disabling.");
            return false;
        }
        dbService = dbProv.getProvider();
        getLogger().info("Hooked SwagAPI IDatabaseService.");
        return true;
    }

    // ========== Config Migration ==========

    private void migrateConfig() {
        int version = getConfig().getInt("config-version", 0);
        if (version >= CURRENT_CONFIG_VERSION) return;

        if (version < 1) {
            // v0 → v1: establish all baseline keys so existing installs pick up new defaults
            setIfAbsent("tags.default", "");
            setIfAbsent("tags.format", "{PREFIX}{PLAYER}{SUFFIX}");
            setIfAbsent("tags.permission-error", "&cYou don't have permission to use this tag.");
            setIfAbsent("credits.enabled", true);
            setIfAbsent("credits.price_per_tag", 10);
            setIfAbsent("credits.starting_credits", 0);
            setIfAbsent("credits.insufficient_credits_msg",
                    "&cYou need {PRICE} credits to buy a custom tag. You have {BALANCE}.");
            setIfAbsent("credits.purchase_success_msg",
                    "&aYou bought a custom tag for {PRICE} credits! You now have {BALANCE} credits.");
            setIfAbsent("credits.refund_percentage", 50);
            setIfAbsent("validation.enabled", true);
            setIfAbsent("validation.min_length", 2);
            setIfAbsent("validation.allow_colors", true);
            setIfAbsent("validation.allow_special_chars", true);
            setIfAbsent("validation.blocked_chars", new java.util.ArrayList<>());
            setIfAbsent("validation.blacklist", java.util.Arrays.asList("badword", "inappropriate"));
            setIfAbsent("validation.too_long_msg", "&cTag is too long! Maximum {MAX} characters.");
            setIfAbsent("validation.too_short_msg", "&cTag is too short! Minimum {MIN} characters.");
            setIfAbsent("validation.blocked_char_msg", "&cTag contains blocked characters!");
            setIfAbsent("validation.blacklist_msg", "&cThat tag contains inappropriate content.");
            setIfAbsent("validation.already_owned_msg", "&cYou already own this exact tag!");
            setIfAbsent("preset_tags.enabled", false);
            setIfAbsent("preset_tags.unlock_message", "&aYou unlocked a new tag: {TAG}");
            getLogger().info("Config migrated to version 1.");
        }

        if (version < 2) {
            setIfAbsent("credits.pending_expiry_days", 7);
            getLogger().info("Config migrated to version 2.");
        }

        if (version < 3) {
            setIfAbsent("withdraw.enabled", true);
            getLogger().info("Config migrated to version 3.");
        }

        // Add future migrations here:
        // if (version < 4) { setIfAbsent("some.new_key", defaultValue); ... }

        getConfig().set("config-version", CURRENT_CONFIG_VERSION);
        saveConfig();
    }

    /** Sets a config key only if it doesn't already exist (preserves existing admin values). */
    private void setIfAbsent(String path, Object value) {
        if (!getConfig().contains(path)) {
            getConfig().set(path, value);
        }
    }

    // ========== Tag Management ==========

    public String getActiveSuffix(UUID uuid) {
        if (adminOverrideSuffix.containsKey(uuid)) {
            return adminOverrideSuffix.get(uuid);
        }

        String loanedTagId = playerEquippedLoanedTag.get(uuid);
        if (loanedTagId != null) {
            LoanedTag loan = activeLoans.get(loanedTagId);
            if (loan != null && !loan.isExpired() && loan.getLoanedToUUID().equals(uuid)) {
                Tag tag = allTags.get(loanedTagId);
                if (tag != null) return tag.getSuffix();
            }
            playerEquippedLoanedTag.remove(uuid);
        }

        String equippedTagId = playerEquippedTag.get(uuid);
        if (equippedTagId == null) return "";
        if (isTagLoaned(equippedTagId)) return "";

        Tag tag = allTags.get(equippedTagId);
        return tag != null ? tag.getSuffix() : "";
    }

    public Tag getActiveTag(UUID uuid) {
        String equippedTagId = playerEquippedTag.get(uuid);
        if (equippedTagId == null) return null;
        return allTags.get(equippedTagId);
    }

    public List<Tag> getPlayerTags(UUID uuid) {
        List<String> tagIds = playerOwnedTags.getOrDefault(uuid, new ArrayList<>());
        return tagIds.stream()
                .map(allTags::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public Map<String, Tag> getTags() {
        return new HashMap<>(allTags);
    }

    public Tag createTagForPlayer(UUID uuid, String suffix, Tag.TagType type) {
        Tag tag = new Tag(uuid, suffix, type);
        while (allTags.containsKey(tag.getId())) {
            // Extremely unlikely (8-hex-char ID space), but never silently overwrite an existing tag.
            tag = new Tag(uuid, suffix, type);
        }
        allTags.put(tag.getId(), tag);
        playerOwnedTags.computeIfAbsent(uuid, k -> new ArrayList<>()).add(tag.getId());
        databaseManager.upsertTag(tag);
        return tag;
    }

    public Tag createTagForPlayer(UUID uuid, String suffix) {
        return createTagForPlayer(uuid, suffix, Tag.TagType.CUSTOM);
    }

    public boolean equipTag(UUID uuid, String tagId) {
        Tag tag = allTags.get(tagId);
        if (tag == null) return false;
        if (!tag.getOwnerUUID().equals(uuid)) return false;
        if (isTagLoaned(tagId)) return false;

        playerEquippedTag.put(uuid, tagId);
        databaseManager.setEquippedTag(uuid, tagId);
        return true;
    }

    public void unequipTag(UUID uuid) {
        playerEquippedTag.remove(uuid);
        databaseManager.removeEquippedTag(uuid);
    }

    public boolean deleteTag(UUID uuid, String tagId, boolean refundCredits) {
        Tag tag = allTags.get(tagId);
        if (tag == null) return false;
        if (!tag.getOwnerUUID().equals(uuid)) return false;
        if (isTagLoaned(tagId)) return false;

        allTags.remove(tagId);

        List<String> owned = playerOwnedTags.get(uuid);
        if (owned != null) owned.remove(tagId);

        boolean wasEquipped = tagId.equals(playerEquippedTag.get(uuid));
        if (wasEquipped) {
            playerEquippedTag.remove(uuid);
        }

        if (refundCredits && isCreditsEnabled() && tag.getType() == Tag.TagType.CUSTOM) {
            int refundPercent = getConfig().getInt("credits.refund_percentage", 50);
            int refundAmount = (getPricePerTag() * refundPercent) / 100;
            addPlayerCredits(uuid, refundAmount);
        }

        databaseManager.deleteTag(tagId);
        if (wasEquipped) {
            databaseManager.removeEquippedTag(uuid);
        }
        return true;
    }

    /**
     * Converts a CUSTOM tag into a tradeable item by removing it from the owner's
     * account (no credit refund — the value now lives in the returned Tag's data,
     * which the caller encodes into an item). Returns null if the tag isn't eligible.
     */
    public Tag withdrawTag(UUID uuid, String tagId) {
        Tag tag = allTags.get(tagId);
        if (tag == null) return null;
        if (!tag.getOwnerUUID().equals(uuid)) return null;
        if (tag.getType() != Tag.TagType.CUSTOM) return null;
        if (isTagLoaned(tagId)) return null;

        allTags.remove(tagId);

        List<String> owned = playerOwnedTags.get(uuid);
        if (owned != null) owned.remove(tagId);

        if (tagId.equals(playerEquippedTag.get(uuid))) {
            playerEquippedTag.remove(uuid);
            databaseManager.removeEquippedTag(uuid);
        }

        databaseManager.deleteTag(tagId);
        return tag;
    }

    public boolean playerOwnsTagWithSuffix(UUID uuid, String suffix) {
        return getPlayerTags(uuid).stream()
                .anyMatch(tag -> tag.getSuffix().equalsIgnoreCase(suffix));
    }

    public int getCustomTagCount(UUID uuid) {
        return (int) getPlayerTags(uuid).stream()
                .filter(tag -> tag.getType() == Tag.TagType.CUSTOM)
                .count();
    }

    // ========== Loan System ==========

    public boolean loanTag(UUID ownerUUID, String tagId, UUID targetUUID, long durationMs) {
        Tag tag = allTags.get(tagId);
        if (tag == null) return false;
        if (!tag.getOwnerUUID().equals(ownerUUID)) return false;
        if (isTagLoaned(tagId)) return false;
        if (ownerUUID.equals(targetUUID)) return false;

        long expiryTime = System.currentTimeMillis() + durationMs;
        LoanedTag loan = new LoanedTag(tagId, ownerUUID, targetUUID, expiryTime);
        activeLoans.put(tagId, loan);

        if (tagId.equals(playerEquippedTag.get(ownerUUID))) {
            playerEquippedTag.remove(ownerUUID);
            databaseManager.removeEquippedTag(ownerUUID);
        }

        databaseManager.upsertLoan(loan);
        return true;
    }

    public boolean returnLoan(String tagId, boolean notify) {
        LoanedTag loan = activeLoans.remove(tagId);
        if (loan == null) return false;

        UUID recipientUUID = loan.getLoanedToUUID();
        if (tagId.equals(playerEquippedLoanedTag.get(recipientUUID))) {
            playerEquippedLoanedTag.remove(recipientUUID);
        }

        databaseManager.deleteLoan(tagId);

        if (notify) {
            Tag tag = allTags.get(tagId);
            // Bug 2: use ColorUtil.processColors instead of ChatColor.translateAlternateColorCodes
            String preview = tag != null
                    ? ColorUtil.processColors("[" + tag.getSuffix() + "]")
                    : "[unknown tag]";

            Player owner = Bukkit.getPlayer(loan.getOwnerUUID());
            if (owner != null) {
                owner.sendMessage(ChatColor.GREEN + "✓ Your loaned tag has been returned: " + preview);
            }

            Player recipient = Bukkit.getPlayer(loan.getLoanedToUUID());
            if (recipient != null) {
                recipient.sendMessage(ChatColor.YELLOW + "The loaned tag " + preview + ChatColor.YELLOW + " has been returned to its owner.");
            }
        }

        return true;
    }

    public LoanedTag getLoanForTag(String tagId) {
        return activeLoans.get(tagId);
    }

    public boolean isTagLoaned(String tagId) {
        LoanedTag loan = activeLoans.get(tagId);
        if (loan == null) return false;
        if (loan.isExpired()) {
            returnLoan(tagId, true);
            return false;
        }
        return true;
    }

    public List<LoanedTag> getLoansForRecipient(UUID uuid) {
        return activeLoans.values().stream()
                .filter(loan -> loan.getLoanedToUUID().equals(uuid) && !loan.isExpired())
                .collect(Collectors.toList());
    }

    public boolean equipLoanedTag(UUID recipientUUID, String tagId) {
        LoanedTag loan = activeLoans.get(tagId);
        if (loan == null || loan.isExpired()) return false;
        if (!loan.getLoanedToUUID().equals(recipientUUID)) return false;

        playerEquippedLoanedTag.put(recipientUUID, tagId);
        return true;
    }

    public void unequipLoanedTag(UUID recipientUUID) {
        playerEquippedLoanedTag.remove(recipientUUID);
    }

    public String getEquippedLoanedTagId(UUID uuid) {
        return playerEquippedLoanedTag.get(uuid);
    }

    /** Feature B: Returns an unmodifiable view of all active loans. */
    public Map<String, LoanedTag> getAllActiveLoans() {
        return Collections.unmodifiableMap(activeLoans);
    }

    private void checkLoanExpiry() {
        List<String> expired = activeLoans.entrySet().stream()
                .filter(e -> e.getValue().isExpired())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (String tagId : expired) {
            returnLoan(tagId, true);
        }
    }

    public static String formatDuration(long ms) {
        if (ms <= 0) return "Expired";
        long seconds = ms / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m " + (seconds % 60) + "s";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h " + (minutes % 60) + "m";
        long days = hours / 24;
        return days + "d " + (hours % 24) + "h";
    }

    // ========== Tag Validation ==========

    public String validateTag(String suffix) {
        if (!getConfig().getBoolean("validation.enabled", true)) return null;

        String stripped = ColorUtil.stripAllColors(suffix);
        int minLength = getConfig().getInt("validation.min_length", 2);

        if (stripped.length() < minLength) {
            return getConfig().getString("validation.too_short_msg", "&cTag is too short!")
                    .replace("{MIN}", String.valueOf(minLength));
        }

        if (!getConfig().getBoolean("validation.allow_special_chars", true)) {
            if (!stripped.matches("[a-zA-Z0-9 ]*")) {
                return getConfig().getString("validation.blocked_char_msg", "&cTag contains blocked characters!");
            }
        }

        List<String> blockedChars = getConfig().getStringList("validation.blocked_chars");
        for (String blocked : blockedChars) {
            if (stripped.contains(blocked)) {
                return getConfig().getString("validation.blocked_char_msg", "&cTag contains blocked characters!");
            }
        }

        List<String> blacklist = getConfig().getStringList("validation.blacklist");
        String lowerSuffix = stripped.toLowerCase();
        for (String word : blacklist) {
            if (lowerSuffix.contains(word.toLowerCase())) {
                return getConfig().getString("validation.blacklist_msg", "&cThat tag contains inappropriate content.");
            }
        }

        String colorError = ColorUtil.validateColors(suffix);
        if (colorError != null) {
            return colorError;
        }

        return null;
    }

    // ========== Legacy/Compatibility ==========

    public void updatePlayerTag(Player player) { }

    public void setPlayerTag(UUID uuid, String suffix) {
        getPlayerTags(uuid).stream()
                .filter(tag -> tag.getSuffix().equals(suffix))
                .findFirst()
                .ifPresent(tag -> equipTag(uuid, tag.getId()));
    }

    // ========== Getters/Setters ==========

    // ========== Pending Tag Approvals ==========

    /** Returns an unmodifiable snapshot of all pending custom-tag requests. */
    public Map<UUID, PendingTag> getPendingCustomTags() {
        return Collections.unmodifiableMap(pendingCustomTags);
    }

    public PendingTag getPendingTag(UUID uuid) {
        return pendingCustomTags.get(uuid);
    }

    public boolean hasPendingTag(UUID uuid) {
        return pendingCustomTags.containsKey(uuid);
    }

    /** Submit a suffix for admin approval, escrowing the credits already charged for it. */
    public void submitPendingTag(UUID uuid, String suffix, int creditsEscrowed) {
        PendingTag pending = new PendingTag(suffix, creditsEscrowed);
        pendingCustomTags.put(uuid, pending);
        databaseManager.upsertPendingTag(uuid, pending);
    }

    /** Removes and returns the pending request for a player (approve/deny/expire all funnel through here). */
    public PendingTag removePendingTag(UUID uuid) {
        PendingTag removed = pendingCustomTags.remove(uuid);
        if (removed != null) {
            databaseManager.deletePendingTag(uuid);
        }
        return removed;
    }

    private void checkPendingTagExpiry() {
        int expiryDays = getConfig().getInt("credits.pending_expiry_days", 7);
        long expiryMs = expiryDays * 24L * 60 * 60 * 1000;

        List<UUID> expired = pendingCustomTags.entrySet().stream()
                .filter(e -> System.currentTimeMillis() - e.getValue().getTimestamp() > expiryMs)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        for (UUID uuid : expired) {
            PendingTag pending = removePendingTag(uuid);
            if (pending == null) continue;

            if (pending.getCreditsEscrowed() > 0) {
                addPlayerCredits(uuid, pending.getCreditsEscrowed());
            }

            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                String preview = ColorUtil.processColors("[" + pending.getSuffix() + "]");
                player.sendMessage(ChatColor.YELLOW + "Your pending tag request " + preview + ChatColor.YELLOW
                        + " expired after " + expiryDays + " days with no admin response.");
                if (pending.getCreditsEscrowed() > 0) {
                    player.sendMessage(ChatColor.GRAY + "Refunded " + ChatColor.GOLD
                            + pending.getCreditsEscrowed() + " credits" + ChatColor.GRAY + ".");
                }
            }
        }
    }

    // ========== Getters/Setters ==========

    public int getPricePerTag() { return getConfig().getInt("credits.price_per_tag", 10); }
    public boolean isCreditsEnabled() { return getConfig().getBoolean("credits.enabled", true); }
    public boolean isWithdrawEnabled() { return getConfig().getBoolean("withdraw.enabled", true); }

    public int getPlayerCredits(UUID uuid) {
        Integer cached = playerCredits.get(uuid);
        if (cached != null) return cached;

        Integer loaded = databaseManager.loadPlayerCredits(uuid);
        int credits = loaded != null ? loaded : getConfig().getInt("credits.starting_credits", 0);
        playerCredits.put(uuid, credits);
        return credits;
    }

    public void setPlayerCredits(UUID uuid, int credits) {
        playerCredits.put(uuid, credits);
        databaseManager.setPlayerCredits(uuid, credits);
    }

    public void addPlayerCredits(UUID uuid, int amount) {
        setPlayerCredits(uuid, getPlayerCredits(uuid) + amount);
    }

    public void removePlayerCredits(UUID uuid, int amount) {
        setPlayerCredits(uuid, Math.max(0, getPlayerCredits(uuid) - amount));
    }

    public void clearAdminOverride(UUID uuid) {
        adminOverrideSuffix.remove(uuid);
    }

    // ========== Event Handlers ==========

    /** Feature C: Clean up loaned tag state when a player disconnects. */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playerEquippedLoanedTag.remove(uuid);
    }

    /**
     * Warms the credits cache for players who weren't picked up by the bulk startup load
     * (i.e. players who have never had a swagtags_credits row before). Without this, the
     * very first {@link #getPlayerCredits(UUID)} call for a brand-new player would fall
     * through to a synchronous DB read on whatever thread triggered it (typically the main
     * thread, e.g. opening the buy-tag GUI). Doing the lookup here instead, off the main
     * thread via {@code queryAsync}, means that path is only ever hit as a narrow fallback.
     */
    @EventHandler
    public void onPlayerJoinWarmCredits(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (playerCredits.containsKey(uuid)) return; // already present from the startup bulk load

        dbService.queryAsync(() -> databaseManager.loadPlayerCredits(uuid))
                .thenAccept(loaded -> Bukkit.getScheduler().runTask(this, () -> {
                    int credits = loaded != null ? loaded : getConfig().getInt("credits.starting_credits", 0);
                    playerCredits.putIfAbsent(uuid, credits);
                }));
    }

    // ========== Persistence ==========
    // MIGRATED: all persistence now flows through DatabaseManager (SwagAPI shared database)
    // instead of ad-hoc YAML flatfiles. These methods only populate the in-memory maps at
    // startup — every mutation elsewhere in this class writes straight through to the
    // database at the time it happens (see equipTag/deleteTag/loanTag/etc. above).

    private void loadAllTags() {
        allTags.putAll(databaseManager.loadAllTags());
        for (Tag tag : allTags.values()) {
            playerOwnedTags.computeIfAbsent(tag.getOwnerUUID(), k -> new ArrayList<>()).add(tag.getId());
        }
        getLogger().info("Loaded " + allTags.size() + " tags");
    }

    private void loadAllEquippedTags() {
        playerEquippedTag.putAll(databaseManager.loadAllEquippedTags());
        getLogger().info("Loaded " + playerEquippedTag.size() + " equipped tags");
    }

    private void loadAllLoans() {
        Map<String, LoanedTag> loaded = databaseManager.loadAllLoans();
        int activeCount = 0;
        for (Map.Entry<String, LoanedTag> entry : loaded.entrySet()) {
            LoanedTag loan = entry.getValue();
            // Preserve original behaviour: only non-expired loans are loaded into memory.
            // Expired rows are left in the database and cleaned up the next time
            // checkLoanExpiry()/returnLoan() processes that tag id.
            if (!loan.isExpired()) {
                activeLoans.put(entry.getKey(), loan);
                activeCount++;
            }
        }
        getLogger().info("Loaded " + activeCount + " active tag loans");
    }

    private void loadAllPendingTags() {
        pendingCustomTags.putAll(databaseManager.loadAllPendingTags());
        getLogger().info("Loaded " + pendingCustomTags.size() + " pending tag requests");
    }

    private void loadAllPlayerCredits() {
        playerCredits.putAll(databaseManager.loadAllPlayerCredits());
        getLogger().info("Loaded credits for " + playerCredits.size() + " players");
    }
}
