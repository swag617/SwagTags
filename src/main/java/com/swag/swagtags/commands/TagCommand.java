package com.swag.swagtags.commands;

import com.swag.swagtags.TagPlugin;
import com.swag.swagtags.gui.JobTagGUI;
import com.swag.swagtags.models.LoanedTag;
import com.swag.swagtags.models.Tag;
import com.swag.swagtags.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class TagCommand implements CommandExecutor, TabCompleter {

    private final TagPlugin plugin;

    public TagCommand(TagPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            plugin.getTagListGUI().open(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "buy":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /tag buy <suffix>");
                    player.sendMessage(ChatColor.GRAY + "Example: /tag buy &cCool&fTag");
                    return true;
                }
                handleBuy(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                break;

            case "preview":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /tag preview <suffix>");
                    return true;
                }
                handlePreview(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                break;

            case "delete":
            case "remove":
                if (args.length < 2) {
                    handleDeleteList(player);
                    return true;
                }
                handleDelete(player, args[1]);
                break;

            case "withdraw":
                if (args.length < 2) {
                    handleWithdrawList(player);
                    return true;
                }
                handleWithdraw(player, args[1]);
                break;

            case "unequip":
            case "clear":
                plugin.unequipTag(player.getUniqueId());
                plugin.unequipLoanedTag(player.getUniqueId());
                plugin.clearAdminOverride(player.getUniqueId());
                player.sendMessage(ChatColor.GREEN + "Tag unequipped!");
                break;

            case "loan":
                if (args.length < 4) {
                    player.sendMessage(ChatColor.RED + "Usage: /tag loan <tag number> <player> <duration>");
                    player.sendMessage(ChatColor.GRAY + "Duration examples: 30m  1h  12h  1d  3d");
                    return true;
                }
                handleLoan(player, args[1], args[2], args[3]);
                break;

            case "loanreturn":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /tag loanreturn <tag name>");
                    player.sendMessage(ChatColor.GRAY + "Tab-complete to see your loaned-out tags.");
                    return true;
                }
                handleLoanReturn(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                break;

            case "loanlist":
                handleLoanList(player, args);
                break;

            case "admin":
                if (player.hasPermission("swagtags.admin")) {
                    plugin.getTagApprovalGUI().openAdmin(player);
                } else {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                }
                break;

            case "addcredits":
                if (!player.hasPermission("swagtags.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /tag addcredits <player> <amount>");
                    return true;
                }
                handleAddCredits(player, args[1], args[2]);
                break;

            case "setcredits":
                if (!player.hasPermission("swagtags.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /tag setcredits <player> <amount>");
                    return true;
                }
                handleSetCredits(player, args[1], args[2]);
                break;

            case "give":
                if (!player.hasPermission("swagtags.admin")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }
                if (args.length == 2) {
                    // No suffix provided — open the job tag GUI if SwagJobs is available
                    Player giveTarget = Bukkit.getPlayer(args[1]);
                    if (giveTarget == null) {
                        player.sendMessage(ChatColor.RED + "Player not found.");
                        return true;
                    }
                    if (JobTagGUI.isAvailable()) {
                        plugin.getJobTagGUI().openJobSelection(player, giveTarget.getUniqueId());
                    } else {
                        player.sendMessage(ChatColor.RED + "SwagJobs is not installed.");
                        player.sendMessage(ChatColor.GRAY + "Usage: /tag give <player> <suffix>");
                    }
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /tag give <player> [suffix]");
                    return true;
                }
                handleGiveTag(player, args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                break;

            case "reload":
                if (!sender.hasPermission("swagtags.admin")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission.");
                    return true;
                }
                plugin.reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "✓ SwagTags config reloaded.");
                return true;

            case "help":
                sendHelpMessage(player);
                break;

            default:
                player.sendMessage(ChatColor.RED + "Unknown subcommand. Use /tag help for commands.");
                break;
        }
        return true;
    }

    private void handleBuy(Player player, String suffix) {
        String validationError = plugin.validateTag(suffix);
        if (validationError != null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', validationError));
            return;
        }

        if (plugin.playerOwnsTagWithSuffix(player.getUniqueId(), suffix)) {
            String msg = plugin.getConfig().getString("validation.already_owned_msg", "&cYou already own this exact tag!");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            return;
        }

        if (plugin.isCreditsEnabled()) {
            int cost = plugin.getPricePerTag();
            int credits = plugin.getPlayerCredits(player.getUniqueId());

            if (credits < cost) {
                String msg = plugin.getConfig().getString("credits.insufficient_credits_msg",
                                "&cYou need {PRICE} credits to buy a custom tag. You have {BALANCE}.")
                        .replace("{PRICE}", String.valueOf(cost))
                        .replace("{BALANCE}", String.valueOf(credits));
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
                return;
            }
        }

        plugin.getTagBuyConfirmGUI().open(player, suffix);
    }

    private void handlePreview(Player player, String suffix) {
        String validationError = plugin.validateTag(suffix);
        if (validationError != null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', validationError));
            return;
        }

        String preview = ChatColor.GRAY + "[" + ChatColor.translateAlternateColorCodes('&', suffix) + ChatColor.GRAY + "]";
        player.sendMessage(ChatColor.GREEN + "Tag Preview:");
        player.sendMessage(ChatColor.WHITE + player.getName() + " " + preview);
        player.sendMessage(ChatColor.GRAY + "Cost: " + ChatColor.GOLD + plugin.getPricePerTag() + " credits");
    }

    private void handleDeleteList(Player player) {
        List<Tag> playerTags = plugin.getPlayerTags(player.getUniqueId());
        if (playerTags.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "You have no tags to delete.");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "── Your Tags ──────────────────");
        for (int i = 0; i < playerTags.size(); i++) {
            Tag tag = playerTags.get(i);
            String rendered = org.bukkit.ChatColor.translateAlternateColorCodes('&', tag.getSuffix());
            String status;
            if (tag.getType() == Tag.TagType.ADMIN_GIVEN) {
                status = ChatColor.DARK_GRAY + " [Admin Gift — cannot delete]";
            } else if (plugin.isTagLoaned(tag.getId())) {
                status = ChatColor.YELLOW + " [On Loan]";
            } else {
                status = "";
            }
            player.sendMessage(ChatColor.GRAY + "" + (i + 1) + ". " + rendered + status);
        }
        player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/tag delete <#>" + ChatColor.GRAY + " to delete a tag.");
    }

    private void handleDelete(Player player, String tagNumberStr) {
        try {
            int tagNumber = Integer.parseInt(tagNumberStr) - 1;
            List<Tag> playerTags = plugin.getPlayerTags(player.getUniqueId());

            if (tagNumber < 0 || tagNumber >= playerTags.size()) {
                player.sendMessage(ChatColor.RED + "Invalid tag number. You have " + playerTags.size() + " tags.");
                return;
            }

            Tag tag = playerTags.get(tagNumber);

            if (tag.getType() == Tag.TagType.ADMIN_GIVEN) {
                player.sendMessage(ChatColor.RED + "You cannot delete admin-given tags.");
                return;
            }

            if (plugin.isTagLoaned(tag.getId())) {
                player.sendMessage(ChatColor.RED + "This tag is currently on loan and cannot be deleted.");
                player.sendMessage(ChatColor.GRAY + "Use /tag loanreturn <tag name> to end the loan first.");
                return;
            }

            int refundPercent = plugin.getConfig().getInt("credits.refund_percentage", 50);
            int refundAmount = (plugin.getPricePerTag() * refundPercent) / 100;
            boolean deleted = plugin.deleteTag(player.getUniqueId(), tag.getId(), true);

            if (deleted) {
                player.sendMessage(ChatColor.GREEN + "✓ Tag deleted!");
                player.sendMessage(ChatColor.GRAY + "Refunded: " + ChatColor.GOLD + refundAmount + " credits");
            } else {
                player.sendMessage(ChatColor.RED + "Failed to delete tag.");
            }

        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid number. Usage: /tag delete <number>");
        }
    }

    private void handleWithdrawList(Player player) {
        List<Tag> playerTags = plugin.getPlayerTags(player.getUniqueId());
        if (playerTags.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "You have no tags to withdraw.");
            return;
        }
        player.sendMessage(ChatColor.GOLD + "── Your Tags ──────────────────");
        for (int i = 0; i < playerTags.size(); i++) {
            Tag tag = playerTags.get(i);
            String rendered = ChatColor.translateAlternateColorCodes('&', tag.getSuffix());
            String status;
            if (tag.getType() != Tag.TagType.CUSTOM) {
                String typeName = tag.getType() == Tag.TagType.PRESET ? "Preset" : "Admin Gift";
                status = ChatColor.DARK_GRAY + " [" + typeName + " — cannot withdraw]";
            } else if (plugin.isTagLoaned(tag.getId())) {
                status = ChatColor.YELLOW + " [On Loan — cannot withdraw]";
            } else {
                status = ChatColor.GREEN + " [Withdrawable]";
            }
            player.sendMessage(ChatColor.GRAY + "" + (i + 1) + ". " + rendered + status);
        }
        player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/tag withdraw <#>"
                + ChatColor.GRAY + " to convert a tag into a tradeable item.");
    }

    private void handleWithdraw(Player player, String tagNumberStr) {
        if (!plugin.isWithdrawEnabled()) {
            player.sendMessage(ChatColor.RED + "Tag withdrawal is currently disabled on this server.");
            return;
        }

        try {
            int tagNumber = Integer.parseInt(tagNumberStr) - 1;
            List<Tag> playerTags = plugin.getPlayerTags(player.getUniqueId());

            if (tagNumber < 0 || tagNumber >= playerTags.size()) {
                player.sendMessage(ChatColor.RED + "Invalid tag number. You have " + playerTags.size() + " tags.");
                return;
            }

            Tag tag = playerTags.get(tagNumber);

            if (tag.getType() != Tag.TagType.CUSTOM) {
                player.sendMessage(ChatColor.RED + "Only custom tags can be withdrawn.");
                return;
            }

            if (plugin.isTagLoaned(tag.getId())) {
                player.sendMessage(ChatColor.RED + "This tag is currently on loan and cannot be withdrawn.");
                player.sendMessage(ChatColor.GRAY + "Use /tag loanreturn <tag name> to end the loan first.");
                return;
            }

            Tag withdrawn = plugin.withdrawTag(player.getUniqueId(), tag.getId());
            if (withdrawn == null) {
                player.sendMessage(ChatColor.RED + "Failed to withdraw tag.");
                return;
            }

            ItemStack item = plugin.getTagWithdrawGUI().createWithdrawnItem(withdrawn.getSuffix());
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                player.sendMessage(ChatColor.YELLOW + "Your inventory was full — the item was dropped at your feet.");
            }

            String preview = ColorUtil.processColors("[" + withdrawn.getSuffix() + "]");
            player.sendMessage(ChatColor.GREEN + "✓ Withdrew tag " + preview + ChatColor.GREEN + " into an item!");
            player.sendMessage(ChatColor.GRAY + "You can now trade it or list it on the AH. Right-click it to redeem.");

        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid number. Usage: /tag withdraw <number>");
        }
    }

    private void handleLoan(Player player, String tagNumberStr, String targetName, String durationStr) {
        int tagNumber;
        try {
            tagNumber = Integer.parseInt(tagNumberStr) - 1;
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid tag number.");
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found or not online.");
            return;
        }

        if (target.equals(player)) {
            player.sendMessage(ChatColor.RED + "You cannot loan a tag to yourself.");
            return;
        }

        List<Tag> playerTags = plugin.getPlayerTags(player.getUniqueId());
        if (tagNumber < 0 || tagNumber >= playerTags.size()) {
            player.sendMessage(ChatColor.RED + "Invalid tag number. You have " + playerTags.size() + " tags.");
            return;
        }

        Tag tag = playerTags.get(tagNumber);

        if (plugin.isTagLoaned(tag.getId())) {
            LoanedTag existing = plugin.getLoanForTag(tag.getId());
            String loanedToName = Bukkit.getOfflinePlayer(existing.getLoanedToUUID()).getName();
            player.sendMessage(ChatColor.RED + "This tag is already on loan to " +
                    (loanedToName != null ? loanedToName : "someone") + ".");
            player.sendMessage(ChatColor.GRAY + "Returns in: " + TagPlugin.formatDuration(existing.getRemainingMs()));
            return;
        }

        long durationMs = parseDuration(durationStr);
        if (durationMs <= 0) {
            player.sendMessage(ChatColor.RED + "Invalid duration. Use formats like: 30m  1h  12h  1d  3d");
            return;
        }

        boolean success = plugin.loanTag(player.getUniqueId(), tag.getId(), target.getUniqueId(), durationMs);
        if (!success) {
            player.sendMessage(ChatColor.RED + "Failed to loan tag.");
            return;
        }

        String preview = ChatColor.translateAlternateColorCodes('&', "[" + tag.getSuffix() + "]");
        player.sendMessage(ChatColor.GREEN + "✓ Loaned tag " + preview + ChatColor.GREEN + " to " + target.getName() + "!");
        player.sendMessage(ChatColor.GRAY + "Duration: " + ChatColor.GOLD + TagPlugin.formatDuration(durationMs));
        player.sendMessage(ChatColor.GRAY + "You will not be able to use this tag while it is loaned.");

        target.sendMessage(ChatColor.GREEN + "✓ " + player.getName() + " has loaned you a tag: " + preview);
        target.sendMessage(ChatColor.GRAY + "Duration: " + ChatColor.GOLD + TagPlugin.formatDuration(durationMs));
        target.sendMessage(ChatColor.GRAY + "Open /tag to equip it!");
    }

    private void handleLoanReturn(Player player, String input) {
        List<Tag> playerTags = plugin.getPlayerTags(player.getUniqueId());

        Tag match = null;
        for (Tag tag : playerTags) {
            if (plugin.getLoanForTag(tag.getId()) != null) {
                String stripped = com.swag.swagtags.util.ColorUtil.stripAllColors(tag.getSuffix());
                if (stripped.equalsIgnoreCase(input)) {
                    match = tag;
                    break;
                }
            }
        }

        if (match == null) {
            player.sendMessage(ChatColor.RED + "No loaned-out tag found matching \"" + input + "\".");
            player.sendMessage(ChatColor.GRAY + "Use /tag loanreturn <tab> to see your loaned tags.");
            return;
        }

        plugin.returnLoan(match.getId(), true);
        String preview = com.swag.swagtags.util.ColorUtil.processColors("[" + match.getSuffix() + "]");
        player.sendMessage(ChatColor.GREEN + "✓ Ended the loan for tag " + preview + ChatColor.GREEN + ". It's yours again!");
    }

    /**
     * Feature B: List active outgoing loans.
     * Usage: /tag loanlist [player|all]
     * - No args: shows the sender's own loans
     * - With player name (admin only): shows that player's loans
     * - "all" (admin only): shows all active loans
     */
    private void handleLoanList(Player player, String[] args) {
        Map<String, LoanedTag> allLoans = plugin.getAllActiveLoans();

        boolean isAdmin = player.hasPermission("swagtags.admin");
        boolean showAll = false;
        UUID targetUUID = player.getUniqueId();

        if (args.length >= 2 && isAdmin) {
            String arg = args[1];
            if (arg.equalsIgnoreCase("all")) {
                showAll = true;
            } else {
                OfflinePlayer target = Bukkit.getOfflinePlayer(arg);
                targetUUID = target.getUniqueId();
            }
        }

        final UUID finalTargetUUID = targetUUID;
        final boolean finalShowAll = showAll;

        List<LoanedTag> loans = allLoans.values().stream()
                .filter(loan -> !loan.isExpired())
                .filter(loan -> finalShowAll || loan.getOwnerUUID().equals(finalTargetUUID))
                .collect(Collectors.toList());

        if (loans.isEmpty()) {
            if (showAll) {
                player.sendMessage(ChatColor.GRAY + "There are no active loans.");
            } else if (finalTargetUUID.equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.GRAY + "You have no active loans.");
            } else {
                player.sendMessage(ChatColor.GRAY + "That player has no active loans.");
            }
            return;
        }

        player.sendMessage(ChatColor.GOLD + "Active Loans:");
        for (LoanedTag loan : loans) {
            Tag tag = plugin.getTags().get(loan.getTagId());
            String tagName = tag != null
                    ? com.swag.swagtags.util.ColorUtil.stripAllColors(tag.getSuffix())
                    : loan.getTagId();

            OfflinePlayer loanedTo = Bukkit.getOfflinePlayer(loan.getLoanedToUUID());
            String recipientName = loanedTo.getName() != null ? loanedTo.getName() : loan.getLoanedToUUID().toString();

            String remaining = TagPlugin.formatDuration(loan.getRemainingMs());

            if (finalShowAll) {
                OfflinePlayer owner = Bukkit.getOfflinePlayer(loan.getOwnerUUID());
                String ownerName = owner.getName() != null ? owner.getName() : loan.getOwnerUUID().toString();
                player.sendMessage(ChatColor.GRAY + "  [" + tagName + "] " + ChatColor.WHITE + ownerName
                        + ChatColor.GRAY + " -> " + ChatColor.WHITE + recipientName
                        + ChatColor.DARK_GRAY + " (" + ChatColor.YELLOW + remaining + " remaining" + ChatColor.DARK_GRAY + ")");
            } else {
                player.sendMessage(ChatColor.GRAY + "  [" + tagName + "] " + ChatColor.WHITE + "\u2192" + ChatColor.GRAY + " " + recipientName
                        + ChatColor.DARK_GRAY + " (" + ChatColor.YELLOW + remaining + " remaining" + ChatColor.DARK_GRAY + ")");
            }
        }
    }

    private void handleAddCredits(Player player, String targetName, String amountStr) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found.");
            return;
        }

        try {
            int amount = Integer.parseInt(amountStr);
            plugin.addPlayerCredits(target.getUniqueId(), amount);
            player.sendMessage(ChatColor.GREEN + "✓ Added " + amount + " credits to " + target.getName());
            player.sendMessage(ChatColor.GRAY + "New balance: " + plugin.getPlayerCredits(target.getUniqueId()));
            target.sendMessage(ChatColor.GREEN + "You received " + amount + " tag credits!");
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid amount.");
        }
    }

    private void handleSetCredits(Player player, String targetName, String amountStr) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found.");
            return;
        }

        try {
            int amount = Integer.parseInt(amountStr);
            plugin.setPlayerCredits(target.getUniqueId(), amount);
            player.sendMessage(ChatColor.GREEN + "✓ Set " + target.getName() + "'s credits to " + amount);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid amount.");
        }
    }

    private void handleGiveTag(Player player, String targetName, String suffix) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found.");
            return;
        }

        String validationError = plugin.validateTag(suffix);
        if (validationError != null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', validationError));
            return;
        }

        plugin.createTagForPlayer(target.getUniqueId(), suffix, Tag.TagType.ADMIN_GIVEN);
        player.sendMessage(ChatColor.GREEN + "✓ Gave tag to " + target.getName());

        String preview = ChatColor.translateAlternateColorCodes('&', "[" + suffix + "]");
        target.sendMessage(ChatColor.GREEN + "You received a new tag: " + preview);
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "====== SwagTags Help ======");
        player.sendMessage(ChatColor.YELLOW + "/tag" + ChatColor.GRAY + " - Open your tags GUI");
        player.sendMessage(ChatColor.YELLOW + "/tag buy <suffix>" + ChatColor.GRAY + " - Purchase a custom tag");
        player.sendMessage(ChatColor.YELLOW + "/tag preview <suffix>" + ChatColor.GRAY + " - Preview a tag before buying");
        player.sendMessage(ChatColor.YELLOW + "/tag delete <number>" + ChatColor.GRAY + " - Delete a tag for partial refund");
        player.sendMessage(ChatColor.YELLOW + "/tag withdraw <number>" + ChatColor.GRAY + " - Convert a custom tag into a tradeable item");
        player.sendMessage(ChatColor.YELLOW + "/tag unequip" + ChatColor.GRAY + " - Remove your current tag");
        player.sendMessage(ChatColor.YELLOW + "/tag loan <tag#> <player> <duration>" + ChatColor.GRAY + " - Loan a tag (e.g. 1h, 1d)");
        player.sendMessage(ChatColor.YELLOW + "/tag loanreturn <tag name>" + ChatColor.GRAY + " - End a loan early");
        player.sendMessage(ChatColor.YELLOW + "/tag loanlist" + ChatColor.GRAY + " - View your active loans");

        if (player.hasPermission("swagtags.admin")) {
            player.sendMessage(ChatColor.RED + "--- Admin Commands ---");
            player.sendMessage(ChatColor.YELLOW + "/tag admin" + ChatColor.GRAY + " - Open approval GUI");
            player.sendMessage(ChatColor.YELLOW + "/tag give <player> [suffix]" + ChatColor.GRAY + " - Give a tag (omit suffix for job tag GUI)");
            player.sendMessage(ChatColor.YELLOW + "/tag addcredits <player> <amount>" + ChatColor.GRAY + " - Add credits");
            player.sendMessage(ChatColor.YELLOW + "/tag setcredits <player> <amount>" + ChatColor.GRAY + " - Set credits");
            player.sendMessage(ChatColor.YELLOW + "/tag loanlist [player|all]" + ChatColor.GRAY + " - View loans");
            player.sendMessage(ChatColor.YELLOW + "/tag reload" + ChatColor.GRAY + " - Reload config");
        }
    }

    private static long parseDuration(String s) {
        if (s == null || s.isEmpty()) return -1;
        s = s.toLowerCase().trim();
        try {
            if (s.endsWith("m")) return Long.parseLong(s.substring(0, s.length() - 1)) * 60_000L;
            if (s.endsWith("h")) return Long.parseLong(s.substring(0, s.length() - 1)) * 3_600_000L;
            if (s.endsWith("d")) return Long.parseLong(s.substring(0, s.length() - 1)) * 86_400_000L;
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("buy", "preview", "delete", "withdraw", "unequip", "loan", "loanreturn", "loanlist", "help"));

            if (sender.hasPermission("swagtags.admin")) {
                completions.addAll(Arrays.asList("admin", "addcredits", "setcredits", "give", "reload"));
            }

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();

            if ((sub.equals("loan") || sub.equals("withdraw")) && sender instanceof Player) {
                List<Tag> tags = plugin.getPlayerTags(((Player) sender).getUniqueId());
                List<String> numbers = new ArrayList<>();
                for (int i = 1; i <= tags.size(); i++) numbers.add(String.valueOf(i));
                return numbers.stream()
                        .filter(n -> n.startsWith(args[1]))
                        .collect(Collectors.toList());
            }

            if (sub.equals("loanreturn") && sender instanceof Player) {
                List<Tag> tags = plugin.getPlayerTags(((Player) sender).getUniqueId());
                List<String> loanedNames = new ArrayList<>();
                for (Tag tag : tags) {
                    if (plugin.getLoanForTag(tag.getId()) != null) {
                        loanedNames.add(com.swag.swagtags.util.ColorUtil.stripAllColors(tag.getSuffix()));
                    }
                }
                return loanedNames.stream()
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }

            // /tag loanlist [player|all] — admin-only suggestions
            if (sub.equals("loanlist") && sender.hasPermission("swagtags.admin")) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("all");
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .forEach(suggestions::add);
                return suggestions.stream()
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if (sub.equals("addcredits") || sub.equals("setcredits") || sub.equals("give")) {
                if (sender.hasPermission("swagtags.admin")) {
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("loan")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("loan")) {
            List<String> durations = Arrays.asList("30m", "1h", "2h", "6h", "12h", "1d", "3d", "7d");
            return durations.stream()
                    .filter(d -> d.startsWith(args[3].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}
