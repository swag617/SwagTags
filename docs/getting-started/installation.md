# Installation

## Requirements

| Dependency | Required | Notes |
|---|---|---|
| Paper 1.20+ | Yes | Built against Paper API `1.21-R0.1-SNAPSHOT`; `plugin.yml` declares `api-version: 1.20` |
| Java 17 | Yes | |
| SwagAPI | **Yes** | Hard dependency (`depend: [SwagAPI]`) — provides the shared database service SwagTags stores all tag/loan/credit data in. SwagTags disables itself on startup if SwagAPI isn't loaded. |
| PlaceholderAPI | No | Only needed if you want `%swagtags_tag%` for chat/tablist display |
| SwagJobs | No | Only needed for the job-preset tag grant GUI (`/tag give <player>` with no suffix) |

SwagTags does **not** depend on Vault or any economy plugin — its credit system is entirely self-contained.

## Steps

1. Install [SwagAPI](https://github.com/swag617/SwagAPI) first — SwagTags will not enable without it.
2. Download or build `SwagTags.jar`.
3. Drop it into your server's `plugins/` folder.
4. (Optional) Install [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) if you want SwagTags' placeholders available to other plugins (e.g. TAB for tablist/chat display).
5. Start or restart the server. On first boot SwagTags will generate `plugins/SwagTags/config.yml`; all tag ownership, equip state, loans, pending approvals, and credit balances are stored in SwagAPI's shared database (see [Tag System — Persistence](../core-features/tag-system.md#persistence)), not local YAML files.
   - If you're upgrading from an older version that used local YAML storage, any existing `tags.yml`, `equipped.yml`, `loans.yml`, `pending.yml`, and `playerdata/` files are automatically imported into the database on first boot, then renamed with an `.imported` suffix.
6. Edit `config.yml` to taste (see [Configuration](configuration.md)), then run `/tag reload` (requires `swagtags.admin`) to apply changes without a restart.

> SwagTags only manages tag ownership, the approval workflow, and equip state. It does not touch chat formatting or the tablist itself — pair it with TAB (or any PlaceholderAPI-aware plugin) using the placeholders described in [Cosmetic Display](../core-features/cosmetic-display.md).

## Verifying it works

Run `/tag` in-game. If the plugin loaded correctly, the "Your Tags" GUI opens (empty until you own a tag). Run `/tag help` to see the full command list, which is filtered based on whether you have `swagtags.admin`.
