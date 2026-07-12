# Permissions

SwagTags defines exactly **one** permission node in `plugin.yml`:

| Permission | Default | Grants access to |
|---|---|---|
| `swagtags.admin` | `op` | `/tag admin`, `/tag give`, `/tag addcredits`, `/tag setcredits`, `/tag reload`, plus the admin view of `/tag loanlist [player\|all]` |

## What's *not* permission-gated

Every player-facing subcommand — `/tag`, `/tag buy`, `/tag preview`, `/tag delete`, `/tag withdraw`, `/tag unequip`, `/tag loan`, `/tag loanreturn`, `/tag loanlist` (own loans), `/tag help` — is available to **any** player with access to the `/tag` command itself. There is no separate "can buy tags" or "can loan tags" node; access control at that level is whatever your permission plugin does with the base `tag` command (or its alias `stag`).

If you want to restrict tag purchasing to certain rank groups, you'll need to gate the `/tag` command itself (e.g. `swagtags.command` via your permissions plugin's command-block feature) rather than relying on a SwagTags-specific node, since none exists for that purpose.

## Granting admin access

With LuckPerms, for example:

```
/lp group staff permission set swagtags.admin true
```

Since the default is `op`, any operator already has full admin access without extra configuration.
