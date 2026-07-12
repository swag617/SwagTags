# Admin Commands

All admin commands and subcommands are gated behind the single `swagtags.admin` permission node (default: `op`). See [Permissions](permissions.md).

## Command reference

| Command | Effect |
|---|---|
| `/tag admin` | Opens the **Pending Tag Approvals** GUI |
| `/tag give <player>` | Opens the job/prestige-tier picker GUI (requires SwagJobs) to grant a preset job tag |
| `/tag give <player> <suffix>` | Grants an `ADMIN_GIVEN` tag directly, bypassing credits and approval |
| `/tag addcredits <player> <amount>` | Adds `amount` credits to a player's balance |
| `/tag setcredits <player> <amount>` | Sets a player's credit balance to an exact value |
| `/tag loanlist [player\|all]` | Views a specific player's active outgoing loans, or every active loan server-wide with `all` |
| `/tag reload` | Reloads `config.yml` (does **not** reload tag/loan/credit data — those are always live) |

`/tag give` targets an **online** player only (`Bukkit.getPlayer(name)` — no offline-player support). All of the above require the target player to be specified by their current in-game name.

## Examples

```
/tag admin
/tag give Notch &6&lLEGEND
/tag give Notch
/tag addcredits Notch 50
/tag setcredits Notch 0
/tag loanlist all
/tag loanlist Notch
/tag reload
```

## Notes on `/tag give`

- With a suffix: creates an `ADMIN_GIVEN` tag. These can't be deleted, withdrawn, or loaned by the recipient — only equipped/unequipped. Use this for permanent rewards.
- Without a suffix: only works if SwagJobs is detected (`Bukkit.getPluginManager().getPlugin("SwagJobs") != null`). If SwagJobs isn't installed, the command tells the admin so and reminds them of the `<suffix>` form instead.

## Notes on `/tag reload`

Reloads the Bukkit `FileConfiguration` from disk (`plugin.reloadConfig()`). Player-owned tags, loans, pending requests, and credit balances are all held in memory and saved continuously as they change — there's no "reload" needed or available for that data, since it's never stale.
