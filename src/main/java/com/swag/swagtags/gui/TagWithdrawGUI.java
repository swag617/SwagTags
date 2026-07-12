package com.swag.swagtags.gui;

import com.swag.swagtags.TagPlugin;
import com.swag.swagtags.models.Tag;
import com.swag.swagtags.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts a CUSTOM tag into a tradeable Name Tag item ("withdraw") so it can be sold
 * on a generic Auction House plugin, and converts it back into an equippable tag
 * ("redeem") when someone right-clicks it.
 *
 * The item's PersistentDataContainer is the sole source of truth for redemption — the
 * display name is cosmetic and can be changed at an anvil, but the PDC key cannot, so
 * a renamed/spoofed vanilla Name Tag can never be redeemed as a real tag.
 *
 * Note: an anvil rename can still change what the *listing name* shows on an AH, which
 * is a known cosmetic-spoofing risk inherent to any renameable-item-as-currency design —
 * the "Raw:" line in the lore (which anvils cannot touch) is the reliable way to verify
 * an item's true tag before buying it.
 */
public class TagWithdrawGUI implements Listener {

    private static final String CONFIRM_GUI_TITLE = ChatColor.DARK_AQUA + "Confirm Tag Redemption";

    private final TagPlugin plugin;
    private final NamespacedKey suffixKey;

    // uuid -> suffix awaiting redeem confirmation
    private final Map<UUID, String> pendingRedeems = new ConcurrentHashMap<>();

    public TagWithdrawGUI(TagPlugin plugin) {
        this.plugin = plugin;
        this.suffixKey = new NamespacedKey(plugin, "withdrawn_tag_suffix");
    }

    // -------------------------------------------------------------------------
    //  Item creation / identification
    // -------------------------------------------------------------------------

    public ItemStack createWithdrawnItem(String suffix) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.processColors(suffix));
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "SwagTags " + ChatColor.DARK_GRAY + "— Withdrawn Custom Tag",
                    "",
                    ChatColor.GRAY + "Raw: " + ChatColor.WHITE + suffix,
                    "",
                    ChatColor.YELLOW + "Right-click to redeem this tag",
                    ChatColor.RED + "Tradeable — don't lose this item!"
            ));
            meta.getPersistentDataContainer().set(suffixKey, PersistentDataType.STRING, suffix);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isWithdrawnTagItem(ItemStack item) {
        if (item == null || item.getType() != Material.NAME_TAG || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(suffixKey, PersistentDataType.STRING);
    }

    public String getSuffix(ItemStack item) {
        if (!isWithdrawnTagItem(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(suffixKey, PersistentDataType.STRING);
    }

    // -------------------------------------------------------------------------
    //  Redeem flow
    // -------------------------------------------------------------------------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        if (!isWithdrawnTagItem(event.getItem())) return;

        event.setCancelled(true);
        openConfirm(event.getPlayer(), getSuffix(event.getItem()));
    }

    // A Name Tag right-clicked on a mob (or armor stand, via the PlayerInteractAtEntityEvent
    // subtype) renames it via a separate event — and consumes the item — instead of going
    // through PlayerInteractEvent. Must intercept both or the item vanishes without ever
    // going through redemption.
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (!isWithdrawnTagItem(item)) return;

        event.setCancelled(true);
        openConfirm(event.getPlayer(), getSuffix(item));
    }

    @EventHandler
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        onInteractEntity(event);
    }

    private void openConfirm(Player player, String suffix) {
        if (suffix == null) return;
        pendingRedeems.put(player.getUniqueId(), suffix);

        Inventory inv = Bukkit.createInventory(null, 27, CONFIRM_GUI_TITLE);

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        ItemStack preview = new ItemStack(Material.NAME_TAG);
        ItemMeta previewMeta = preview.getItemMeta();
        previewMeta.setDisplayName(ChatColor.AQUA + "Redeem This Tag?");
        previewMeta.setLore(Arrays.asList(
                ChatColor.GRAY + "Preview: " + ChatColor.GRAY + "[" + ColorUtil.processColors(suffix) + ChatColor.GRAY + "]",
                "",
                ChatColor.GRAY + "This will consume the item and",
                ChatColor.GRAY + "add the tag back to your account."
        ));
        preview.setItemMeta(previewMeta);
        inv.setItem(13, preview);

        ItemStack cancel = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "✗ Cancel");
        cancel.setItemMeta(cancelMeta);
        inv.setItem(11, cancel);

        ItemStack confirm = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.GREEN + "✔ Redeem");
        confirm.setItemMeta(confirmMeta);
        inv.setItem(15, confirm);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(CONFIRM_GUI_TITLE)) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        UUID uuid = player.getUniqueId();
        String suffix = pendingRedeems.get(uuid);
        if (suffix == null) return;

        int slot = event.getSlot();

        if (slot == 11) {
            pendingRedeems.remove(uuid);
            player.closeInventory();
            player.sendMessage(ChatColor.GRAY + "Redemption cancelled.");
            return;
        }

        if (slot == 15) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (!suffix.equals(getSuffix(held))) {
                pendingRedeems.remove(uuid);
                player.closeInventory();
                player.sendMessage(ChatColor.RED + "You're no longer holding that tag item.");
                return;
            }

            if (plugin.playerOwnsTagWithSuffix(uuid, suffix)) {
                pendingRedeems.remove(uuid);
                String msg = plugin.getConfig().getString("validation.already_owned_msg", "&cYou already own this exact tag!");
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                player.closeInventory();
                return;
            }

            pendingRedeems.remove(uuid);
            if (held.getAmount() > 1) {
                held.setAmount(held.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }

            plugin.createTagForPlayer(uuid, suffix, Tag.TagType.CUSTOM);
            player.closeInventory();

            String preview = ColorUtil.processColors("[" + suffix + "]");
            player.sendMessage(ChatColor.GREEN + "✓ Redeemed tag " + preview + ChatColor.GREEN + "! It's now in your /tag list.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pendingRedeems.remove(event.getPlayer().getUniqueId());
    }
}
