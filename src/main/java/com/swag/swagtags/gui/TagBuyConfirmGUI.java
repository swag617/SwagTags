package com.swag.swagtags.gui;

import com.swag.swagtags.TagPlugin;
import com.swag.swagtags.util.ColorUtil;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TagBuyConfirmGUI implements Listener {

    private final TagPlugin plugin;

    // Bug 3+5: use concurrent collections to avoid race conditions
    // Players currently viewing the confirm GUI (uuid -> pending suffix)
    private final Map<UUID, String> pendingConfirmations = new ConcurrentHashMap<>();

    // Players who clicked "Edit" and are waiting to type in chat
    private final Set<UUID> editingPlayers = Collections.synchronizedSet(new HashSet<>());

    private static final String GUI_TITLE = ChatColor.DARK_AQUA + "Confirm Tag Purchase";

    // Layout (27 slots, 3 rows):
    //   Row 1: filler
    //   Row 2: filler × 4, PREVIEW (13), filler × 4
    //   Row 3: CANCEL (18), filler × 3, EDIT (22), filler × 3, CONFIRM (26)

    public TagBuyConfirmGUI(TagPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String suffix) {
        pendingConfirmations.put(player.getUniqueId(), suffix);

        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        inv.setItem(13, makePreviewItem(player, suffix));

        // Cancel (slot 18)
        ItemStack cancelItem = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "✗ Cancel");
        cancelMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Cancel — no credits charged"));
        cancelItem.setItemMeta(cancelMeta);
        inv.setItem(18, cancelItem);

        // Edit (slot 22)
        ItemStack editItem = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta editMeta = editItem.getItemMeta();
        editMeta.setDisplayName(ChatColor.YELLOW + "✎ Edit Tag");
        editMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Type a new tag name in chat",
                ChatColor.GRAY + "Color codes are supported"
        ));
        editItem.setItemMeta(editMeta);
        inv.setItem(22, editItem);

        // Confirm (slot 26)
        boolean creditsEnabled = plugin.isCreditsEnabled();
        int cost = plugin.getPricePerTag();
        ItemStack confirmItem = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.GREEN + "✔ Submit for Approval");
        confirmMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Send this tag to admins for review.",
                creditsEnabled
                        ? ChatColor.GRAY + "Cost: " + ChatColor.GOLD + cost + " credits"
                        : ChatColor.GRAY + "Cost: " + ChatColor.GREEN + "FREE",
                creditsEnabled
                        ? ChatColor.GRAY + "Your balance: " + ChatColor.WHITE + plugin.getPlayerCredits(player.getUniqueId())
                        : ChatColor.GRAY + "Credit system is disabled on this server"
        ));
        confirmItem.setItemMeta(confirmMeta);
        inv.setItem(26, confirmItem);

        player.openInventory(inv);
    }

    private ItemStack makePreviewItem(Player player, String suffix) {
        ItemStack preview = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = preview.getItemMeta();
        String rendered = ColorUtil.processColors(suffix);
        meta.setDisplayName(ChatColor.AQUA + "Your Tag Preview");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Preview: " + ChatColor.GRAY + "[" + rendered + ChatColor.GRAY + "]",
                ChatColor.GRAY + "Raw: " + ChatColor.WHITE + suffix,
                "",
                ChatColor.GRAY + "This is how it will appear in-game."
        ));
        preview.setItemMeta(meta);
        return preview;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        UUID uuid = player.getUniqueId();
        String suffix = pendingConfirmations.get(uuid);
        if (suffix == null) return;

        int slot = event.getSlot();

        if (slot == 18) {
            pendingConfirmations.remove(uuid);
            player.closeInventory();
            player.sendMessage(ChatColor.GRAY + "Tag purchase cancelled.");

        } else if (slot == 22) {
            pendingConfirmations.remove(uuid);
            editingPlayers.add(uuid);
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Type your new tag text in chat (color codes are supported):");
            player.sendMessage(ChatColor.GRAY + "Type " + ChatColor.RED + "cancel" + ChatColor.GRAY + " to cancel the purchase.");

        } else if (slot == 26) {
            boolean creditsEnabled = plugin.isCreditsEnabled();
            int cost = creditsEnabled ? plugin.getPricePerTag() : 0;

            if (creditsEnabled) {
                int credits = plugin.getPlayerCredits(uuid);
                if (credits < cost) {
                    String msg = plugin.getConfig().getString("credits.insufficient_credits_msg",
                                    "&cYou need {PRICE} credits to buy a custom tag. You have {BALANCE}.")
                            .replace("{PRICE}", String.valueOf(cost))
                            .replace("{BALANCE}", String.valueOf(credits));
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                    player.closeInventory();
                    pendingConfirmations.remove(uuid);
                    return;
                }
            }

            if (plugin.playerOwnsTagWithSuffix(uuid, suffix)) {
                String msg = plugin.getConfig().getString("validation.already_owned_msg", "&cYou already own this exact tag!");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                pendingConfirmations.remove(uuid);
                player.closeInventory();
                return;
            }

            pendingConfirmations.remove(uuid);
            if (creditsEnabled) plugin.removePlayerCredits(uuid, cost);
            plugin.submitPendingTag(uuid, suffix, cost);

            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "✓ Tag submitted for approval!");
            player.sendMessage(ChatColor.GRAY + "Preview: " + ColorUtil.processColors("[" + suffix + "]"));
            player.sendMessage(ChatColor.GRAY + "You will be notified once an admin reviews it.");

            String preview = ColorUtil.processColors("[" + suffix + "]");
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("swagtags.admin"))
                    .forEach(p -> p.sendMessage(ChatColor.YELLOW + "⚠ " + player.getName() + " requested tag: " + preview));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("deprecation")
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!editingPlayers.contains(uuid)) return;

        event.setCancelled(true);
        editingPlayers.remove(uuid);

        String message = event.getMessage();

        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.GRAY + "Tag purchase cancelled.");
            return;
        }

        String validationError = plugin.validateTag(message);
        if (validationError != null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', validationError));
            player.sendMessage(ChatColor.GRAY + "Tag purchase cancelled.");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> open(player, message));
    }

    // Bug 3+5: clean up state on disconnect to prevent memory leaks
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        editingPlayers.remove(uuid);
        pendingConfirmations.remove(uuid);
    }

    public boolean isEditing(UUID uuid) {
        return editingPlayers.contains(uuid);
    }
}
