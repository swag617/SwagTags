# Troubleshooting

## Placeholders return nothing / blank

- Confirm PlaceholderAPI is installed and loaded — SwagTags only registers its expansion if `Bukkit.getPluginManager().getPlugin("PlaceholderAPI")` is non-null at startup. If PAPI was installed *after* SwagTags started, restart the server (there's no runtime re-registration).
- Confirm the player actually has a tag **equipped**, not just owned — `/tag` shows a green checkmark on the equipped one.
- If they were wearing a *loaned* tag, check whether the loan expired — `%swagtags_tag%` returns an empty string once a loan lapses until they equip something else.
- A tag that's currently loaned **out** to someone else renders as empty for the *owner*, even if it shows as equipped in their own memory of things — this is expected, not a bug.

## `/tag give <player>` says "SwagJobs is not installed"

Expected if SwagJobs isn't present — this form of the command only works with SwagJobs' job/tier picker. Use `/tag give <player> <suffix>` instead for a plain admin-granted tag.

## A player's purchase never showed up in `/tag admin`

Check `plugins/SwagTags/pending.yml` directly — if the entry isn't there, the purchase confirmation likely wasn't completed (they may have hit Cancel, or their client disconnected before submitting). Ask them to retry `/tag buy <suffix>`.

## Config changes aren't taking effect

Run `/tag reload` after editing `config.yml`. Note this only reloads config values — it does not touch tag ownership, loans, pending requests, or credit balances, since those are always read live from memory, never cached from a stale reload.

## A tag suffix that used to validate now gets flagged in the approval GUI

This is intentional — the approval GUI re-validates every pending suffix against your **current** `validation.*` rules every time you open it, even though it already passed at submission time. If you tightened `blacklist` or `blocked_chars` after a request was submitted, older pending requests can retroactively show a validation warning (red wool icon). You can still approve or deny them manually; SwagTags won't auto-reject on your behalf.

## Player lost their withdrawn tag item

Withdrawn tag items are ordinary Name Tags with hidden PDC data — if dropped, destroyed, or lost through normal item-loss mechanics (lava, despawn, etc.), the tag is gone for good. There's no server-side backup of a withdrawn tag's data once it's converted to an item; the item itself *is* the only remaining record. Advise players to store valuable withdrawn tags securely (e.g., an ender chest) until they redeem or sell them.

## SwagTags won't start / ClassNotFoundException related to SwagJobs

SwagJobs is bundled as a `system`-scoped dependency at build time (`Libs/SwagJobs-1.0.0.jar`), which means the classes are compiled against but **not** shaded into `SwagTags.jar`. If you see class-loading errors referencing `com.swag.swagjobs.*` at runtime, verify SwagJobs itself is actually installed and loaded on the server — SwagTags' own runtime checks (`Bukkit.getPluginManager().getPlugin("SwagJobs")`) should prevent this in normal operation, but a build against a mismatched SwagJobs jar version could still cause it.

## Still stuck?

Ask in the [Discord](https://discord.gg/9rKuThh6yU) or open an issue on [GitHub](https://github.com/swag617/SwagTags).
