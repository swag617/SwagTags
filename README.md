# SwagTags

Custom player tag system for Paper 1.20+. Players design their own color-coded suffix tag, spend credits to submit it for admin review, and equip it once approved — SwagTags manages ownership, the approval workflow, and equip state, while a placeholder-aware plugin (e.g. TAB) handles the actual chat/tablist display via `%swagtags_tag%`.

📖 **[Live Documentation](https://swag617.github.io/SwagTags/)** · 📦 **[Releases](https://github.com/swag617/SwagTags/releases)**

## Features

- **Custom tag purchases** — `/tag buy <suffix>`, preview, and admin-approval workflow
- **Credits economy** — configurable price per tag, starting balance, and partial refund on delete
- **Admin approval queue** — dedicated GUI (`/tag admin`) with live validation-issue warnings
- **Tag validation** — minimum length, blocked characters, word blacklist, color-syntax checks
- **Rich color support** — legacy `&` codes, hex (`&#RRGGBB`, `<#RRGGBB>`, `{#RRGGBB}`), MiniMessage-style tags, gradients, and rainbow text
- **"Your Tags" GUI** — equip, unequip, and delete owned tags with live search and two-step delete confirmation
- **Tag loans** — temporarily lend an owned tag to another player for a set duration, with automatic expiry and return
- **Tag withdrawal & trading** — convert a custom tag into a tradeable Name Tag item, redeemable back into an equippable tag
- **PlaceholderAPI expansion** — `%swagtags_tag%`, `%swagtags_tag_space%`, `%swagtags_tag_colon%`
- **SwagJobs integration (optional)** — grant job-preset tier tags through a job/tier picker GUI
- **Admin tools** — give tags directly, adjust player credits, reload config without a restart

## Requirements

| Dependency | Required | Notes |
|---|---|---|
| Paper 1.20+ | Yes | Built against Paper API `1.21-R0.1-SNAPSHOT`; `plugin.yml` declares `api-version: 1.20` |
| Java 17 | Yes | |
| [SwagAPI](https://github.com/swag617/SwagAPI) | **Yes** | Hard dependency — provides the shared database service SwagTags stores all tag/loan/credit data in. SwagTags disables itself on startup if SwagAPI isn't loaded. |
| PlaceholderAPI | No | Only needed for `%swagtags_tag%` chat/tablist display |
| SwagJobs | No | Only needed for the job-preset tag grant GUI |

SwagTags does **not** depend on Vault or any economy plugin — its credit system is entirely self-contained.

## Storage

Tag ownership, equip state, credits, active loans, and pending approvals are persisted through **SwagAPI's shared database service**, not local YAML files. If you're upgrading from an older version that used YAML flatfile storage, any existing `tags.yml`, `equipped.yml`, `loans.yml`, `pending.yml`, and `playerdata/*.yml` files are automatically imported into the shared database on first startup, then renamed with an `.imported` suffix. See [Tag System — Persistence](https://swag617.github.io/SwagTags/#/core-features/tag-system) for details.

## Building from Source

### Prerequisites
- Java JDK 17
- Maven 3.6+

### Build Command

```bash
mvn clean package
```

The compiled JAR will be output to `target/SwagTags.jar`.

> `pom.xml` also references `libs/SwagAPI-1.0.0.jar` and `Libs/SwagJobs-1.0.0.jar` as system-scoped compile dependencies — make sure those jars are present at the paths declared in `pom.xml` before building.

## Installation

1. Install [SwagAPI](https://github.com/swag617/SwagAPI) first — SwagTags will not enable without it.
2. Drop `SwagTags.jar` into your server's `plugins/` folder.
3. (Optional) Install [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) for `%swagtags_tag%` support.
4. Start or restart the server, then edit `plugins/SwagTags/config.yml` to taste and run `/tag reload`.

Full setup walkthrough: [Installation guide](https://swag617.github.io/SwagTags/#/getting-started/installation).

## Project Structure

```
SwagTags/
├── pom.xml                                        # Maven build configuration
├── src/main/
│   ├── java/com/swag/swagtags/
│   │   ├── TagPlugin.java                         # Main plugin class
│   │   ├── commands/
│   │   │   └── TagCommand.java                    # /tag command + tab completion
│   │   ├── database/
│   │   │   └── DatabaseManager.java               # SwagAPI-backed persistence
│   │   ├── gui/
│   │   │   ├── TagListGUI.java                    # "Your Tags" GUI
│   │   │   ├── TagApprovalGUI.java                # Admin approval queue
│   │   │   ├── TagBuyConfirmGUI.java               # Purchase confirmation
│   │   │   ├── TagWithdrawGUI.java                # Withdraw-to-item flow
│   │   │   ├── JobTagGUI.java                     # SwagJobs preset-tier picker
│   │   │   └── TagPlaceholder.java                # PlaceholderAPI expansion
│   │   ├── models/
│   │   │   ├── Tag.java                           # Tag data model
│   │   │   ├── LoanedTag.java                     # Active loan data model
│   │   │   └── PendingTag.java                    # Pending approval data model
│   │   └── util/
│   │       └── ColorUtil.java                     # Color code parsing/validation
│   └── resources/
│       ├── plugin.yml                             # Plugin metadata
│       └── config.yml                             # Main configuration
└── docs/                                          # Docsify documentation site
```

## Documentation

Full docs (installation, configuration, tag system, loans & trading, admin commands, permissions, troubleshooting) are published at **https://swag617.github.io/SwagTags/**.

## License

Proprietary — SwagDev internal use.

---

**SwagTags** v1.0.1 · Built for Paper 1.20+ · Java 17
