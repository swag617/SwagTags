# Installation

## Requirements

| Dependency | Required | Notes |
|---|---|---|
| Paper 1.20+ | Yes | Built against Paper API `1.21-R0.1-SNAPSHOT`; `plugin.yml` declares `api-version: 1.20` |
| Java 17 | Yes | |
| PlaceholderAPI | No | Only needed if you want `%swagtags_tag%` for chat/tablist display |
| SwagJobs | No | Only needed for the job-preset tag grant GUI (`/tag give <player>` with no suffix) |

SwagTags does **not** depend on Vault or any economy plugin — its credit system is entirely self-contained.

## Steps

1. Download or build `SwagTags.jar`.
2. Drop it into your server's `plugins/` folder.
3. (Optional) Install [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) if you want SwagTags' placeholders available to other plugins (e.g. TAB for tablist/chat display).
4. Start or restart the server. On first boot SwagTags will generate:
   - `plugins/SwagTags/config.yml`
   - `plugins/SwagTags/tags.yml`
   - `plugins/SwagTags/equipped.yml`
   - `plugins/SwagTags/loans.yml`
   - `plugins/SwagTags/pending.yml`
   - `plugins/SwagTags/playerdata/` (one file per player, storing their credit balance)
5. Edit `config.yml` to taste (see [Configuration](configuration.md)), then run `/tag reload` (requires `swagtags.admin`) to apply changes without a restart.

> SwagTags only manages tag ownership, the approval workflow, and equip state. It does not touch chat formatting or the tablist itself — pair it with TAB (or any PlaceholderAPI-aware plugin) using the placeholders described in [Cosmetic Display](../core-features/cosmetic-display.md).

## Verifying it works

Run `/tag` in-game. If the plugin loaded correctly, the "Your Tags" GUI opens (empty until you own a tag). Run `/tag help` to see the full command list, which is filtered based on whether you have `swagtags.admin`.
