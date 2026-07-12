package com.swag.swagtags.gui;

import com.swag.swagtags.TagPlugin;
import com.swag.swagtags.util.ColorUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TagPlaceholder extends PlaceholderExpansion {

    private final TagPlugin plugin;

    public TagPlaceholder(TagPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "swagtags";
    }

    @Override
    public @NotNull String getAuthor() {
        return "SwagMC";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        // %swagtags_tag% -> "[TAG]" (NO leading space - for chat)
        if (params.equalsIgnoreCase("tag")) {
            String suffix = plugin.getActiveSuffix(player.getUniqueId());
            if (suffix == null || suffix.isEmpty()) return "";
            return ChatColor.GRAY + "["
                    + ColorUtil.processColors(suffix)
                    + ChatColor.GRAY + "]" + ChatColor.RESET;
        }

        // %swagtags_tag_space% -> " [TAG]" (WITH leading space - for tablist)
        if (params.equalsIgnoreCase("tag_space") || params.equalsIgnoreCase("tagspace")) {
            String suffix = plugin.getActiveSuffix(player.getUniqueId());
            if (suffix == null || suffix.isEmpty()) return "";
            return " " + ChatColor.GRAY + "["
                    + ColorUtil.processColors(suffix)
                    + ChatColor.GRAY + "]" + ChatColor.RESET;
        }

        // %swagtags_tag_colon% -> "[TAG]:" (No leading space, grey colon - for chat)
        if (params.equalsIgnoreCase("tag_colon") || params.equalsIgnoreCase("tagcolon")) {
            String suffix = plugin.getActiveSuffix(player.getUniqueId());
            if (suffix == null || suffix.isEmpty()) return "";
            return ChatColor.GRAY + "["
                    + ColorUtil.processColors(suffix)
                    + ChatColor.GRAY + "]" + ChatColor.RESET + ChatColor.GRAY + " :";
        }

        return null;
    }
}
