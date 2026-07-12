package com.swag.swagtags.gui;

import com.swag.swagtags.TagPlugin;
import com.swag.swagtags.models.PendingTag;
import com.swag.swagtags.models.Tag;
import com.swag.swagtags.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TagApprovalGUI implements Listener {

    private final TagPlugin plugin;

    // Keyed per-admin so two admins with the approval GUI open at once don't clobber each other's slot mapping.
    private final Map<UUID, Map<Integer, UUID>> slotToPlayerByAdmin = new ConcurrentHashMap<>();

    public TagApprovalGUI(TagPlugin plugin) {
        this.plugin = plugin;
    }

    public void openAdmin(Player admin) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_RED + "Pending Tag Approvals");
        Map<Integer, UUID> slotToPlayer = new HashMap<>();
        slotToPlayerByAdmin.put(admin.getUniqueId(), slotToPlayer);

        Map<UUID, PendingTag> pending = plugin.getPendingCustomTags();

        if (pending.isEmpty()) {
            ItemStack noRequests = new ItemStack(Material.BARRIER);
            ItemMeta meta = noRequests.getItemMeta();
            meta.setDisplayName(ChatColor.GRAY + "No pending requests");
            meta.setLore(Arrays.asList("", ChatColor.GRAY + "All caught up!"));
            noRequests.setItemMeta(meta);
            inv.setItem(22, noRequests);
            admin.openInventory(inv);
            return;
        }

        int slot = 0;
        for (Map.Entry<UUID, PendingTag> entry : pending.entrySet()) {
            if (slot >= 45) break;

            UUID targetUUID = entry.getKey();
            String suffix = entry.getValue().getSuffix();
            long ageDays = entry.getValue().getAgeDays();

            Player targetPlayer = Bukkit.getPlayer(targetUUID);
            String playerName = targetPlayer != null ? targetPlayer.getName() : "Offline Player";

            String validationError = plugin.validateTag(suffix);
            boolean hasValidationIssue = validationError != null;

            ItemStack item = new ItemStack(hasValidationIssue ? Material.RED_WOOL : Material.NAME_TAG);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + playerName);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Preview: " + ChatColor.GRAY + "[" +
                    ColorUtil.processColors(suffix) + ChatColor.GRAY + "]");
            lore.add(ChatColor.GRAY + "Raw: " + ChatColor.WHITE + suffix);
            lore.add("");

            if (hasValidationIssue) {
                lore.add(ChatColor.RED + "⚠ Validation Issue:");
                lore.add(ColorUtil.processColors(validationError));
                lore.add("");
            }

            int credits = plugin.getPlayerCredits(targetUUID);
            int ownedCount = plugin.getCustomTagCount(targetUUID);

            lore.add(ChatColor.GRAY + "Player Credits: " + ChatColor.GOLD + credits);
            lore.add(ChatColor.GRAY + "Tags Owned: " + ChatColor.WHITE + ownedCount);
            lore.add(ChatColor.GRAY + "Pending for: " + ChatColor.WHITE + ageDays + (ageDays == 1 ? " day" : " days"));
            lore.add("");
            lore.add(ChatColor.GREEN + "Left-click to approve");
            lore.add(ChatColor.RED + "Right-click to deny & refund");

            meta.setLore(lore);
            item.setItemMeta(meta);

            inv.setItem(slot, item);
            slotToPlayer.put(slot, targetUUID);
            slot++;
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.AQUA + "Admin Info");
        infoMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Pending requests: " + ChatColor.WHITE + pending.size(),
                "",
                ChatColor.YELLOW + "Actions:",
                ChatColor.GREEN + "Approve" + ChatColor.GRAY + " - Gives tag to player",
                ChatColor.RED + "Deny" + ChatColor.GRAY + " - Refunds " + plugin.getPricePerTag() + " credits"
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);

        admin.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        if (event.getCurrentItem() == null) return;

        if (!event.getView().getTitle().equals(ChatColor.DARK_RED + "Pending Tag Approvals")) return;

        event.setCancelled(true);

        Map<Integer, UUID> slotToPlayer = slotToPlayerByAdmin.get(admin.getUniqueId());
        if (slotToPlayer == null) return;

        UUID targetUUID = slotToPlayer.get(event.getSlot());
        if (targetUUID == null) return;

        PendingTag pendingTag = plugin.getPendingTag(targetUUID);
        if (pendingTag == null) return;
        String suffix = pendingTag.getSuffix();

        String playerName = Bukkit.getOfflinePlayer(targetUUID).getName();
        if (playerName == null) playerName = "Unknown Player";

        if (event.isLeftClick()) {
            plugin.removePendingTag(targetUUID);
            plugin.createTagForPlayer(targetUUID, suffix, Tag.TagType.CUSTOM);

            String preview = ColorUtil.processColors("[" + suffix + "]");
            admin.sendMessage(ChatColor.GREEN + "✓ Approved tag for " + playerName);
            admin.sendMessage(ChatColor.GRAY + "Preview: " + preview);

            Player target = Bukkit.getPlayer(targetUUID);
            if (target != null) {
                target.sendMessage(ChatColor.GREEN + "✓ Your tag was approved: " + preview);
                target.sendMessage(ChatColor.GRAY + "Use /tag to equip it!");
            }

            admin.closeInventory();

        } else if (event.isRightClick()) {
            plugin.removePendingTag(targetUUID);
            int refund = pendingTag.getCreditsEscrowed();
            if (refund > 0) plugin.addPlayerCredits(targetUUID, refund);

            admin.sendMessage(ChatColor.RED + "✗ Denied tag for " + playerName);
            if (refund > 0) admin.sendMessage(ChatColor.GRAY + "Refunded " + refund + " credits");

            Player target = Bukkit.getPlayer(targetUUID);
            if (target != null) {
                target.sendMessage(ChatColor.RED + "Your tag request was denied by an admin.");
                if (refund > 0) {
                    target.sendMessage(ChatColor.GRAY + "You were refunded " + ChatColor.GOLD + refund + " credits" + ChatColor.GRAY + ".");
                }
            }

            admin.closeInventory();
        }
    }
}
