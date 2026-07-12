# FAQ

**My tag isn't showing up in chat or the tablist. What's wrong?**

SwagTags doesn't render your tag anywhere itself — it only tracks what's equipped. You need PlaceholderAPI plus a plugin like TAB (or your chat plugin) configured to use `%swagtags_tag%` (or `%swagtags_tag_space%` for tablist name formats) in its format string. See [Cosmetic Display](../core-features/cosmetic-display.md).

**Why did my tag purchase get "stuck" with no admin response?**

Pending requests aren't instant — a human with `swagtags.admin` has to review and approve them in `/tag admin`. If nobody does within `credits.pending_expiry_days` (7 days by default), it auto-cancels and refunds your escrowed credits automatically.

**Can I get my credits back if I delete a tag?**

Yes, but only a partial refund — `credits.refund_percentage` (50% by default) of the current `credits.price_per_tag`. `ADMIN_GIVEN` tags can't be deleted at all, so there's nothing to refund there.

**Why can't I delete/withdraw/loan one of my tags?**

Most likely it's currently on loan to someone else — loaned tags are locked from deletion, withdrawal, and re-loaning until the loan ends or is returned early with `/tag loanreturn`. `PRESET` and `ADMIN_GIVEN` tags also can't be withdrawn (only `CUSTOM` tags can), and `ADMIN_GIVEN` tags can't be deleted at all.

**I equipped a loaned tag, but it disappeared. What happened?**

Loans expire automatically. When a loan expires, SwagTags returns it to the owner and unequips it from whoever was borrowing it, with a chat notification to both.

**Does SwagTags need Vault or an economy plugin?**

No. Credits are a self-contained SwagTags-only currency stored per-player in `plugins/SwagTags/playerdata/`, unrelated to any server economy.

**Is there a permission to control who can buy/loan/withdraw tags?**

No — only `swagtags.admin` exists. Every other subcommand is open to anyone who can run `/tag` at all. Gate the base command through your permissions plugin if you need finer control. See [Permissions](../server-owners/permissions.md).

**Does `/tag give` work on offline players?**

No — both the direct-suffix form and the SwagJobs job-picker form require the target to be online, since the command looks them up with `Bukkit.getPlayer(name)`.
