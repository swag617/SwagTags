package com.swag.swagtags.gui;

import com.swag.swagtags.TagPlugin;
import com.swag.swagtags.models.Tag;
import com.swag.swagtags.util.ColorUtil;
import com.swag.swagjobs.SwagJobsPlugin;
import com.swag.swagjobs.manager.TagManager;
import com.swag.swagjobs.model.Job;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two-level GUI: job selection -> prestige tier selection -> grant tag.
 *
 * Level 1 (27 slots): pick a job or choose custom suffix.
 * Level 2 (27 slots): pick a prestige tier (Novice/Expert/Master) for the chosen job.
 *
 * Preset-tier grants are delegated entirely to SwagJobs' own TagManager#grantPresetTag,
 * so the actual tag text stays defined in exactly one place (SwagJobs), instead of being
 * re-derived here from tier numbers that SwagJobs no longer exposes.
 *
 * All inventory operations run on the main thread.
 * AsyncPlayerChatEvent handler dispatches back to the main thread before
 * calling any Bukkit API.
 */
public class JobTagGUI implements Listener {

    private static final String JOB_GUI_TITLE  = ChatColor.DARK_GREEN + "Give Job Tag";
    private static final String TIER_GUI_TITLE = ChatColor.DARK_GREEN + "Select Prestige Tier";

    private final TagPlugin plugin;

    /** admin UUID -> target UUID (player who will receive the tag) */
    private final Map<UUID, UUID> pendingGive = new ConcurrentHashMap<>();

    /** admin UUID -> job selected at level 1 (used while navigating to level 2) */
    private final Map<UUID, Job> selectedJob = new ConcurrentHashMap<>();

    /**
     * admin UUID -> (slot -> value string).
     * Value is one of: "MINER", "LUMBERJACK", ... (job enum name), "CUSTOM",
     * or a tier name ("novice"/"expert"/"master", stored at level 2).
     */
    private final Map<UUID, Map<Integer, String>> adminSlotMap = new ConcurrentHashMap<>();

    /** admin UUID -> target UUID for custom-chat-input flow */
    private final Map<UUID, UUID> customTagAdmins = new ConcurrentHashMap<>();

    public JobTagGUI(TagPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    //  Public API
    // -------------------------------------------------------------------------

    /** Returns true if SwagJobs is currently loaded on this server. */
    public static boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("SwagJobs") != null;
    }

    /**
     * Open the Level-1 job selection GUI for an admin granting a tag to targetUUID.
     * Must be called from the main thread.
     */
    public void openJobSelection(Player admin, UUID targetUUID) {
        SwagJobsPlugin sjp = SwagJobsPlugin.getInstance();
        if (sjp == null) {
            admin.sendMessage(ChatColor.RED + "SwagJobs is not available.");
            return;
        }

        pendingGive.put(admin.getUniqueId(), targetUUID);

        Inventory inv = Bukkit.createInventory(null, 27, JOB_GUI_TITLE);
        Map<Integer, String> slotMap = new HashMap<>();

        // Row 0 and row 2: black glass pane filler
        ItemStack filler = makeFiller();
        for (int i = 0; i < 9; i++)  inv.setItem(i, filler);
        for (int i = 18; i < 27; i++) inv.setItem(i, filler);

        // Slot 4: target player skull
        String targetName = Bukkit.getOfflinePlayer(targetUUID).getName();
        if (targetName == null) targetName = targetUUID.toString();
        inv.setItem(4, makeSkull(targetUUID, targetName));

        // Slots 9-18: one per job (10 jobs fit exactly in 10 slots)
        TagManager tm = sjp.getTagManager();
        String[] jobNames = tm.getJobNames();
        int slot = 9;
        for (String jobName : jobNames) {
            if (slot > 18) break;
            Job job = tm.parseJob(jobName);
            if (job == null) continue;
            inv.setItem(slot, makeJobItem(job));
            slotMap.put(slot, job.name());
            slot++;
        }

        // Slot 22: custom tag button
        ItemStack customItem = new ItemStack(Material.BOOK);
        ItemMeta customMeta = customItem.getItemMeta();
        if (customMeta != null) {
            customMeta.setDisplayName(ChatColor.YELLOW + "Custom Tag");
            customMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Give any custom suffix",
                    ChatColor.GRAY + "Type it in chat after clicking"
            ));
            customItem.setItemMeta(customMeta);
        }
        inv.setItem(22, customItem);
        slotMap.put(22, "CUSTOM");

        adminSlotMap.put(admin.getUniqueId(), slotMap);
        admin.openInventory(inv);
    }

    /**
     * Open the Level-2 prestige tier selection GUI (Novice / Expert / Master).
     * Must be called from the main thread.
     */
    public void openTierSelection(Player admin, Job job) {
        UUID targetUUID = pendingGive.get(admin.getUniqueId());
        if (targetUUID == null) return;

        SwagJobsPlugin sjp = SwagJobsPlugin.getInstance();
        if (sjp == null) {
            admin.sendMessage(ChatColor.RED + "SwagJobs is not available.");
            return;
        }

        String[] tierNames = sjp.getTagManager().getTierNames();
        if (tierNames == null || tierNames.length == 0) {
            admin.sendMessage(ChatColor.RED + "No prestige tiers configured.");
            return;
        }

        selectedJob.put(admin.getUniqueId(), job);

        Inventory inv = Bukkit.createInventory(null, 27, TIER_GUI_TITLE);
        Map<Integer, String> slotMap = new HashMap<>();

        // Fill all slots with filler first
        ItemStack filler = makeFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // Slot 0: back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.GRAY + "← Back");
            back.setItemMeta(backMeta);
        }
        inv.setItem(0, back);

        // Slot 4: job icon
        inv.setItem(4, makeJobItem(job));

        // Row 2 (slots 9-17): one button per tier, evenly spaced
        int slot = 11;
        for (String tier : tierNames) {
            if (slot > 15) break;

            String tierLabel = tier.substring(0, 1).toUpperCase() + tier.substring(1);
            ItemStack tierItem = new ItemStack(Material.NAME_TAG);
            ItemMeta tierMeta = tierItem.getItemMeta();
            if (tierMeta != null) {
                tierMeta.setDisplayName(ChatColor.GOLD + tierLabel);
                tierMeta.setLore(Arrays.asList(
                        ChatColor.GRAY + "Grants this player the",
                        ChatColor.GRAY + tierLabel + " tier tag for this job.",
                        "",
                        ChatColor.GREEN + "Click to grant"
                ));
                tierItem.setItemMeta(tierMeta);
            }
            inv.setItem(slot, tierItem);
            slotMap.put(slot, tier);
            slot += 2;
        }

        // Slot 22: info — who we're granting to
        String targetName = Bukkit.getOfflinePlayer(targetUUID).getName();
        if (targetName == null) targetName = targetUUID.toString();
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ChatColor.YELLOW + "Granting to: " + ChatColor.WHITE + targetName);
            info.setItemMeta(infoMeta);
        }
        inv.setItem(22, info);

        adminSlotMap.put(admin.getUniqueId(), slotMap);
        admin.openInventory(inv);
    }

    // -------------------------------------------------------------------------
    //  Event Handlers
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player admin = (Player) event.getWhoClicked();
        UUID adminId = admin.getUniqueId();

        String title = event.getView().getTitle();
        boolean isJobGui  = JOB_GUI_TITLE.equals(title);
        boolean isTierGui = TIER_GUI_TITLE.equals(title);
        if (!isJobGui && !isTierGui) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null
                || event.getCurrentItem().getType() == Material.AIR
                || event.getCurrentItem().getType() == Material.BLACK_STAINED_GLASS_PANE) {
            return;
        }

        int clickedSlot = event.getRawSlot();
        Map<Integer, String> slotMap = adminSlotMap.get(adminId);
        if (slotMap == null) return;

        // ── Level-1: job selection GUI ──
        if (isJobGui) {
            String value = slotMap.get(clickedSlot);
            if (value == null) return;

            if ("CUSTOM".equals(value)) {
                // Enter chat-input mode for a custom suffix
                adminSlotMap.remove(adminId);
                UUID targetUUID = pendingGive.get(adminId);
                if (targetUUID == null) return;
                customTagAdmins.put(adminId, targetUUID);
                admin.closeInventory();
                admin.sendMessage(ChatColor.YELLOW + "Type the tag suffix in chat. Type 'cancel' to abort.");
                return;
            }

            // Parse the job and open tier selection
            Job job;
            try {
                job = Job.valueOf(value);
            } catch (IllegalArgumentException e) {
                admin.sendMessage(ChatColor.RED + "Unknown job: " + value);
                return;
            }
            openTierSelection(admin, job);
            return;
        }

        // ── Level-2: tier selection GUI ──
        if (isTierGui) {
            // Back button
            if (clickedSlot == 0) {
                selectedJob.remove(adminId);
                UUID targetUUID = pendingGive.get(adminId);
                if (targetUUID != null) {
                    openJobSelection(admin, targetUUID);
                }
                return;
            }

            String tier = slotMap.get(clickedSlot);
            if (tier == null) return;

            UUID targetUUID = pendingGive.get(adminId);
            Job job = selectedJob.get(adminId);
            if (targetUUID == null || job == null) return;

            grantPresetTag(admin, targetUUID, job, tier);
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player admin = event.getPlayer();
        UUID adminId = admin.getUniqueId();

        if (!customTagAdmins.containsKey(adminId)) return;

        event.setCancelled(true);
        String message = event.getMessage().trim();
        UUID targetUUID = customTagAdmins.remove(adminId);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                pendingGive.remove(adminId);
                admin.sendMessage(ChatColor.GRAY + "Custom tag input cancelled.");
                return;
            }

            String error = plugin.validateTag(message);
            if (error != null) {
                admin.sendMessage(ChatColor.translateAlternateColorCodes('&', error));
                // Re-add so admin can try again without re-opening the GUI
                customTagAdmins.put(adminId, targetUUID);
                admin.sendMessage(ChatColor.YELLOW + "Type the tag suffix in chat. Type 'cancel' to abort.");
                return;
            }

            grantCustomTag(admin, targetUUID, message);
        });
    }

    /** Clears any in-flight give-tag state if an admin disconnects mid-flow, so a later
     *  unrelated chat message from them can't be silently captured as a tag suffix. */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingGive.remove(uuid);
        selectedJob.remove(uuid);
        adminSlotMap.remove(uuid);
        customTagAdmins.remove(uuid);
    }

    // -------------------------------------------------------------------------
    //  Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Grant a job-preset tier tag by delegating to SwagJobs' own TagManager, so the tag
     * text stays a single source of truth. Must be called from the main thread.
     */
    private void grantPresetTag(Player admin, UUID targetUUID, Job job, String tier) {
        admin.closeInventory();
        pendingGive.remove(admin.getUniqueId());
        selectedJob.remove(admin.getUniqueId());
        adminSlotMap.remove(admin.getUniqueId());

        SwagJobsPlugin sjp = SwagJobsPlugin.getInstance();
        if (sjp == null) {
            admin.sendMessage(ChatColor.RED + "SwagJobs is not available.");
            return;
        }

        String targetName = Bukkit.getOfflinePlayer(targetUUID).getName();
        if (targetName == null) targetName = targetUUID.toString();

        boolean success = sjp.getTagManager().grantPresetTag(targetUUID, job, tier);
        if (!success) {
            admin.sendMessage(ChatColor.RED + "Failed to grant tag — " + targetName
                    + " may already own that tier's tag. Check console for details.");
            return;
        }

        String jobDisplay = ChatColor.translateAlternateColorCodes('&', job.getDisplayName());
        String tierLabel = tier.substring(0, 1).toUpperCase() + tier.substring(1);
        admin.sendMessage(ChatColor.GREEN + "✓ Granted " + jobDisplay + ChatColor.GREEN + " "
                + tierLabel + " tag to " + targetName);

        Player target = Bukkit.getPlayer(targetUUID);
        if (target != null) {
            target.sendMessage(ChatColor.GREEN + "✓ An admin gave you a tag! Use /tag to equip it!");
        }
    }

    /**
     * Grant a free-form custom tag directly through SwagTags (not job-related).
     * Must be called from the main thread.
     */
    private void grantCustomTag(Player admin, UUID targetUUID, String suffix) {
        plugin.createTagForPlayer(targetUUID, suffix, Tag.TagType.ADMIN_GIVEN);
        String preview = ColorUtil.processColors("[" + suffix + "]");
        String targetName = Bukkit.getOfflinePlayer(targetUUID).getName();
        if (targetName == null) targetName = targetUUID.toString();

        admin.sendMessage(ChatColor.GREEN + "✓ Granted tag " + preview + ChatColor.GREEN + " to " + targetName);
        Player target = Bukkit.getPlayer(targetUUID);
        if (target != null) {
            target.sendMessage(ChatColor.GREEN + "✓ An admin gave you a tag: " + preview);
            target.sendMessage(ChatColor.GRAY + "Use /tag to equip it!");
        }
    }

    private ItemStack makeFiller() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack makeJobItem(Job job) {
        ItemStack item = new ItemStack(job.getIcon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', job.getDisplayName()));
            meta.setLore(Collections.singletonList(ChatColor.GRAY + "Click to see prestige tags"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeSkull(UUID targetUUID, String targetName) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(targetUUID));
            skullMeta.setDisplayName(ChatColor.YELLOW + "Giving tag to: " + ChatColor.WHITE + targetName);
            skullMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Click a job to browse its tags"));
            skull.setItemMeta(skullMeta);
        }
        return skull;
    }
}
