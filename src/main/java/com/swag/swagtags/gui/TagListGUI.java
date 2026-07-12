package com.swag.swagtags.gui;

import com.swag.swagtags.TagPlugin;
import com.swag.swagtags.models.LoanedTag;
import com.swag.swagtags.models.Tag;
import com.swag.swagtags.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TagListGUI implements Listener {
    private final TagPlugin plugin;

    // slot -> tagId  (owned tags use bare tagId, loaned-to-player tags use "LOANED:<tagId>")
    // Bug 3: use ConcurrentHashMap to avoid leak issues
    private final Map<UUID, Map<Integer, String>> playerSlotMap = new ConcurrentHashMap<>();

    // Feature D: track pending delete confirmations (uuid -> slot awaiting confirmation)
    private final Map<UUID, Integer> pendingDeletes = new ConcurrentHashMap<>();

    // Feature E: active search filters (uuid -> filter string)
    private final Map<UUID, String> playerFilters = new ConcurrentHashMap<>();

    // Feature E: players currently prompted to type a search term in chat
    private final Set<UUID> filteringPlayers = Collections.synchronizedSet(new HashSet<>());

    // Feature E: players for whom open() is about to reopen the GUI after chat input
    // Used to suppress filter-clear in onInventoryClose during the reopen transition
    private final Set<UUID> reopeningForFilter = Collections.synchronizedSet(new HashSet<>());

    public TagListGUI(TagPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.GOLD + "Your Tags");

        Map<Integer, String> slotMap = new HashMap<>();
        playerSlotMap.put(player.getUniqueId(), slotMap);

        List<Tag> playerTags = plugin.getPlayerTags(player.getUniqueId());
        Tag currentTag = plugin.getActiveTag(player.getUniqueId());
        String currentTagId = currentTag != null ? currentTag.getId() : null;
        String equippedLoanedTagId = plugin.getEquippedLoanedTagId(player.getUniqueId());

        // Feature E: read active filter for this player
        String activeFilter = playerFilters.get(player.getUniqueId());

        // === Owned tags (slots 0-44) ===
        int slot = 0;
        int tagNumber = 1;
        for (Tag tag : playerTags) {
            if (slot >= 44) break;

            // Feature E: skip tags that don't match the active filter
            if (activeFilter != null && !activeFilter.isEmpty()) {
                String stripped = ColorUtil.stripAllColors(tag.getSuffix()).toLowerCase();
                if (!stripped.contains(activeFilter.toLowerCase())) {
                    tagNumber++;
                    continue;
                }
            }

            LoanedTag existingLoan = plugin.getLoanForTag(tag.getId());
            boolean isLoanedOut = existingLoan != null;
            boolean isEquipped = tag.getId().equals(currentTagId);
            String suffix = ColorUtil.processColors(tag.getSuffix());

            ItemStack item;
            ItemMeta meta;

            if (isLoanedOut) {
                item = new ItemStack(Material.CLOCK);
                meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.DARK_GRAY + "[On Loan] " + suffix);

                String loanedToName = Bukkit.getOfflinePlayer(existingLoan.getLoanedToUUID()).getName();
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Preview: " + ChatColor.GRAY + "[" + suffix + ChatColor.GRAY + "]");
                lore.add(ChatColor.GRAY + "Type: " + ChatColor.WHITE + formatTagType(tag.getType()));
                lore.add("");
                lore.add(ChatColor.YELLOW + "On loan to: " + ChatColor.WHITE + (loanedToName != null ? loanedToName : "Unknown"));
                lore.add(ChatColor.GOLD + "Returns in: " + TagPlugin.formatDuration(existingLoan.getRemainingMs()));
                lore.add("");
                lore.add(ChatColor.GRAY + "Use /tag loanreturn " + tagNumber + " to end early");
                meta.setLore(lore);

            } else if (isEquipped) {
                item = new ItemStack(Material.NAME_TAG);
                meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.GREEN + "✔ " + suffix + ChatColor.GRAY + " #" + tagNumber);

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Preview: " + ChatColor.GRAY + "[" + suffix + ChatColor.GRAY + "]");
                lore.add(ChatColor.GRAY + "Type: " + ChatColor.WHITE + formatTagType(tag.getType()));
                lore.add("");
                lore.add(ChatColor.GREEN + "✔ Currently equipped!");
                lore.add(ChatColor.GRAY + "Right-click to unequip");
                meta.setLore(lore);

            } else {
                item = new ItemStack(Material.NAME_TAG);
                meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.YELLOW + suffix + ChatColor.GRAY + " #" + tagNumber);

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Preview: " + ChatColor.GRAY + "[" + suffix + ChatColor.GRAY + "]");
                lore.add(ChatColor.GRAY + "Type: " + ChatColor.WHITE + formatTagType(tag.getType()));
                lore.add("");
                lore.add(ChatColor.GRAY + "Left-click to equip");
                lore.add(ChatColor.GRAY + "Right-click to delete");
                meta.setLore(lore);
            }

            item.setItemMeta(meta);
            inv.setItem(slot, item);
            slotMap.put(slot, tag.getId());
            slot++;
            tagNumber++;
        }

        // === Tags loaned TO this player ===
        List<LoanedTag> loansForMe = plugin.getLoansForRecipient(player.getUniqueId());
        if (!loansForMe.isEmpty() && slot < 44) {
            ItemStack sep = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta sepMeta = sep.getItemMeta();
            sepMeta.setDisplayName(ChatColor.GRAY + "--- Loaned to You ---");
            sep.setItemMeta(sepMeta);
            inv.setItem(slot, sep);
            slot++;

            for (LoanedTag loan : loansForMe) {
                if (slot >= 44) break;

                Tag loanedTag = plugin.getTags().get(loan.getTagId());
                if (loanedTag == null) continue;

                String loanedSuffix = ColorUtil.processColors(loanedTag.getSuffix());
                String ownerName = Bukkit.getOfflinePlayer(loan.getOwnerUUID()).getName();
                boolean loanEquipped = loan.getTagId().equals(equippedLoanedTagId);

                ItemStack item = new ItemStack(Material.CLOCK);
                ItemMeta meta = item.getItemMeta();

                if (loanEquipped) {
                    meta.setDisplayName(ChatColor.GREEN + "✔ " + loanedSuffix + ChatColor.AQUA + " [Loaned]");
                } else {
                    meta.setDisplayName(ChatColor.AQUA + loanedSuffix + ChatColor.AQUA + " [Loaned]");
                }

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Preview: [" + loanedSuffix + ChatColor.GRAY + "]");
                lore.add(ChatColor.GRAY + "Loaned by: " + ChatColor.WHITE + (ownerName != null ? ownerName : "Unknown"));
                lore.add(ChatColor.GOLD + "Expires in: " + TagPlugin.formatDuration(loan.getRemainingMs()));
                lore.add("");
                if (loanEquipped) {
                    lore.add(ChatColor.GREEN + "✔ Currently equipped!");
                    lore.add(ChatColor.GRAY + "Right-click to unequip");
                } else {
                    lore.add(ChatColor.GRAY + "Left-click to equip");
                }
                meta.setLore(lore);
                item.setItemMeta(meta);

                inv.setItem(slot, item);
                slotMap.put(slot, "LOANED:" + loan.getTagId());
                slot++;
            }
        }

        // === Info panel at bottom (row 6) ===

        // Feature E: Search button at slot 45
        ItemStack searchItem = new ItemStack(Material.COMPASS);
        ItemMeta searchMeta = searchItem.getItemMeta();
        if (activeFilter != null && !activeFilter.isEmpty()) {
            searchMeta.setDisplayName(ChatColor.AQUA + "Search: " + ChatColor.WHITE + activeFilter);
            searchMeta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Click to change filter",
                    ChatColor.RED + "Right-click to clear filter"
            ));
        } else {
            searchMeta.setDisplayName(ChatColor.AQUA + "Search Tags");
            searchMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Click to filter by name"));
        }
        searchItem.setItemMeta(searchMeta);
        inv.setItem(45, searchItem);
        slotMap.put(45, "SEARCH");

        // Credits display at slot 48
        ItemStack creditsItem = new ItemStack(Material.SUNFLOWER);
        ItemMeta creditsMeta = creditsItem.getItemMeta();
        creditsMeta.setDisplayName(ChatColor.GOLD + "Your Credits");
        int credits = plugin.getPlayerCredits(player.getUniqueId());
        int price = plugin.getPricePerTag();
        boolean creditsEnabled = plugin.isCreditsEnabled();
        creditsMeta.setLore(Arrays.asList(
                ChatColor.YELLOW + "Balance: " + ChatColor.WHITE + credits + " credits",
                creditsEnabled
                        ? ChatColor.GRAY + "Tag cost: " + ChatColor.WHITE + price + " credits"
                        : ChatColor.GRAY + "Tag cost: " + ChatColor.GREEN + "FREE",
                "",
                ChatColor.GRAY + "Use " + ChatColor.WHITE + "/tag buy <suffix>" + ChatColor.GRAY + " to purchase"
        ));
        creditsItem.setItemMeta(creditsMeta);
        inv.setItem(48, creditsItem);

        // Tag stats display at slot 49
        ItemStack statsItem = new ItemStack(Material.PAPER);
        ItemMeta statsMeta = statsItem.getItemMeta();
        statsMeta.setDisplayName(ChatColor.AQUA + "Tag Statistics");
        int customCount = plugin.getCustomTagCount(player.getUniqueId());
        statsMeta.setLore(Arrays.asList(
                ChatColor.YELLOW + "Custom Tags: " + ChatColor.WHITE + customCount,
                ChatColor.YELLOW + "Total Tags: " + ChatColor.WHITE + playerTags.size(),
                ChatColor.YELLOW + "Loaned to You: " + ChatColor.WHITE + loansForMe.size(),
                "",
                ChatColor.GRAY + "Delete tags for " + plugin.getConfig().getInt("credits.refund_percentage", 50) + "% refund"
        ));
        statsItem.setItemMeta(statsMeta);
        inv.setItem(49, statsItem);

        // Help item at slot 50
        ItemStack helpItem = new ItemStack(Material.BOOK);
        ItemMeta helpMeta = helpItem.getItemMeta();
        helpMeta.setDisplayName(ChatColor.GREEN + "Help");
        helpMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Left-click a tag to equip it",
                ChatColor.GRAY + "Right-click an owned tag to delete it",
                "",
                ChatColor.YELLOW + "Commands:",
                ChatColor.WHITE + "/tag buy <suffix>" + ChatColor.GRAY + " - Buy tag",
                ChatColor.WHITE + "/tag preview <suffix>" + ChatColor.GRAY + " - Preview",
                // Bug 6: corrected loan command syntax
                ChatColor.WHITE + "/tag loan <#> <player> <time>" + ChatColor.GRAY + " - Loan tag",
                ChatColor.WHITE + "/tag loanreturn <name>" + ChatColor.GRAY + " - End loan"
        ));
        helpItem.setItemMeta(helpMeta);
        inv.setItem(50, helpItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(ChatColor.GOLD + "Your Tags")) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        Map<Integer, String> slotMap = playerSlotMap.get(player.getUniqueId());
        if (slotMap == null) return;

        String slotValue = slotMap.get(event.getSlot());
        if (slotValue == null) return;

        UUID playerUUID = player.getUniqueId();

        // ── Feature E: Search button ────────────────────────────────────────
        if (slotValue.equals("SEARCH")) {
            if (event.isRightClick() && playerFilters.containsKey(playerUUID)) {
                // Right-click clears the filter
                playerFilters.remove(playerUUID);
                open(player);
            } else {
                // Left-click (or right-click with no active filter): prompt for input.
                // Mark this close as a transition first — otherwise onInventoryClose() fires
                // synchronously from closeInventory() below and immediately wipes filteringPlayers
                // before the player ever gets a chance to type anything.
                filteringPlayers.add(playerUUID);
                reopeningForFilter.add(playerUUID);
                player.closeInventory();
                player.sendMessage(ChatColor.AQUA + "Type a search term in chat (or 'cancel' to go back):");
            }
            return;
        }

        // ── Loaned tag (loaned TO this player) ───────────────────────────────
        if (slotValue.startsWith("LOANED:")) {
            String loanedTagId = slotValue.substring(7);
            LoanedTag loan = plugin.getLoanForTag(loanedTagId);

            if (loan == null || loan.isExpired()) {
                player.sendMessage(ChatColor.RED + "This loan has already expired.");
                player.closeInventory();
                playerSlotMap.remove(playerUUID);
                return;
            }

            Tag loanedTag = plugin.getTags().get(loanedTagId);

            if (event.isLeftClick()) {
                if (loanedTagId.equals(plugin.getEquippedLoanedTagId(playerUUID))) {
                    player.sendMessage(ChatColor.YELLOW + "This tag is already equipped!");
                    return;
                }
                plugin.clearAdminOverride(playerUUID);
                plugin.equipLoanedTag(playerUUID, loanedTagId);
                String preview = loanedTag != null
                        ? ColorUtil.processColors("[" + loanedTag.getSuffix() + "]")
                        : "[?]";
                player.sendMessage(ChatColor.GREEN + "✓ Loaned tag equipped: " + preview);
                player.closeInventory();
                playerSlotMap.remove(playerUUID);

            } else if (event.isRightClick()) {
                if (loanedTagId.equals(plugin.getEquippedLoanedTagId(playerUUID))) {
                    plugin.unequipLoanedTag(playerUUID);
                    player.sendMessage(ChatColor.GREEN + "✓ Loaned tag unequipped.");
                    player.closeInventory();
                    playerSlotMap.remove(playerUUID);
                } else {
                    player.sendMessage(ChatColor.GRAY + "This tag is not currently equipped.");
                }
            }
            return;
        }

        // ── Owned tag ─────────────────────────────────────────────────────────
        String tagId = slotValue;
        Tag tag = plugin.getTags().get(tagId);
        if (tag == null) return;

        if (plugin.isTagLoaned(tagId)) {
            player.sendMessage(ChatColor.RED + "This tag is currently on loan and cannot be modified.");
            return;
        }

        Tag currentTag = plugin.getActiveTag(playerUUID);
        boolean isEquipped = currentTag != null && currentTag.getId().equals(tagId);

        // Feature D: if a left-click arrives while a pendingDelete is active, cancel it
        if (event.isLeftClick() && pendingDeletes.containsKey(playerUUID)) {
            pendingDeletes.remove(playerUUID);
            open(player); // refresh to restore the original item display
            return;
        }

        if (event.isLeftClick()) {
            if (isEquipped) {
                player.sendMessage(ChatColor.YELLOW + "This tag is already equipped!");
                return;
            }

            plugin.clearAdminOverride(playerUUID);
            plugin.unequipLoanedTag(playerUUID);
            plugin.equipTag(playerUUID, tagId);
            plugin.updatePlayerTag(player);

            String preview = ChatColor.translateAlternateColorCodes('&', "[" + tag.getSuffix() + "]");
            player.sendMessage(ChatColor.GREEN + "✓ Tag equipped: " + preview);
            player.closeInventory();
            playerSlotMap.remove(playerUUID);

        } else if (event.isRightClick()) {
            // Right-click on equipped tag = unequip (no delete confirmation needed)
            if (isEquipped) {
                plugin.unequipTag(playerUUID);
                plugin.clearAdminOverride(playerUUID);
                player.sendMessage(ChatColor.GREEN + "Tag unequipped!");
                player.closeInventory();
                playerSlotMap.remove(playerUUID);
                return;
            }

            int slot = event.getSlot();

            // Feature D: two-step delete confirmation for non-equipped owned tags
            Integer pendingSlot = pendingDeletes.get(playerUUID);
            if (pendingSlot != null && pendingSlot == slot) {
                // Second right-click on same slot — proceed with delete
                pendingDeletes.remove(playerUUID);

                int refundPercent = plugin.getConfig().getInt("credits.refund_percentage", 50);
                int refundAmount = (plugin.getPricePerTag() * refundPercent) / 100;

                boolean deleted = plugin.deleteTag(playerUUID, tagId, true);

                if (deleted) {
                    player.sendMessage(ChatColor.GREEN + "✓ Tag deleted!");
                    player.sendMessage(ChatColor.GRAY + "Refunded: " + ChatColor.GOLD + refundAmount + " credits");
                    player.closeInventory();
                    playerSlotMap.remove(playerUUID);
                } else {
                    player.sendMessage(ChatColor.RED + "Failed to delete tag.");
                }
            } else {
                // First right-click — show confirmation item
                pendingDeletes.put(playerUUID, slot);

                ItemStack confirmItem = new ItemStack(Material.RED_CONCRETE);
                ItemMeta confirmMeta = confirmItem.getItemMeta();
                confirmMeta.setDisplayName(ChatColor.RED + "⚠ Click again to confirm delete");
                confirmMeta.setLore(Arrays.asList(
                        ChatColor.GRAY + "Right-click again within 5 seconds",
                        ChatColor.GRAY + "Left-click to cancel"
                ));
                confirmItem.setItemMeta(confirmMeta);
                event.getInventory().setItem(slot, confirmItem);

                player.sendMessage(ChatColor.YELLOW + "Right-click again within 5 seconds to confirm delete.");

                // Schedule expiry of the pending confirmation
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Integer current = pendingDeletes.get(playerUUID);
                    if (current != null && current == slot) {
                        pendingDeletes.remove(playerUUID);
                        // Refresh the GUI if the player still has it open
                        if (player.isOnline()
                                && player.getOpenInventory().getTitle().equals(ChatColor.GOLD + "Your Tags")) {
                            open(player);
                        }
                    }
                }, 100L);
            }
        }
    }

    // Bug 4: clean up on inventory close to prevent map leaks
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(ChatColor.GOLD + "Your Tags")) return;

        UUID uuid = player.getUniqueId();
        playerSlotMap.remove(uuid);
        pendingDeletes.remove(uuid);

        // Feature E: only clear filter state if this close is not part of a filter-reopen transition
        if (!reopeningForFilter.remove(uuid)) {
            playerFilters.remove(uuid);
            filteringPlayers.remove(uuid);
        }
    }

    // Feature E: capture chat input for the search filter
    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("deprecation")
    public void onFilterChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!filteringPlayers.contains(player.getUniqueId())) return;

        event.setCancelled(true);
        filteringPlayers.remove(player.getUniqueId());

        String input = event.getMessage().trim();
        if (!input.equalsIgnoreCase("cancel")) {
            playerFilters.put(player.getUniqueId(), input);
        }

        // Mark that the upcoming close event is a reopen, not a genuine dismiss
        reopeningForFilter.add(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> open(player));
    }

    private String formatTagType(Tag.TagType type) {
        switch (type) {
            case CUSTOM: return "Custom";
            case PRESET: return "Preset";
            case ADMIN_GIVEN: return "Admin Gift";
            default: return "Unknown";
        }
    }
}
