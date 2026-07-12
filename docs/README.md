# ✦ SwagTags

> Custom player tag system for Paper 1.21+

SwagTags lets players design their own custom suffix tag, spend credits to submit it for admin review, and equip it once approved. Tags show up wherever a server hooks in the `%swagtags_tag%` PlaceholderAPI placeholder (typically TAB or a chat plugin) — SwagTags itself only manages ownership, equipping, and the approval workflow, not chat/tablist formatting.

Beyond the core buy → approve → equip loop, SwagTags includes a full economy layer: refundable deletes, a two-step delete confirmation in the GUI, temporary tag loans between players, and the ability to withdraw a tag into a tradeable item for an auction house.

---

## Features

- **Custom tag purchases** — players type `/tag buy <suffix>`, preview it, and submit it for admin approval
- **Credits economy** — configurable price per tag, starting balance, and partial refund on delete
- **Admin approval queue** — a dedicated GUI (`/tag admin`) to approve or deny pending requests, with built-in validation-issue warnings
- **Tag validation** — minimum length, blocked characters, word blacklist, and color-tag syntax checks
- **Rich color support** — legacy `&` codes, hex (`&#RRGGBB`, `<#RRGGBB>`, `{#RRGGBB}`), MiniMessage-style tags, gradients, and rainbow text
- **"Your Tags" GUI** — equip, unequip, and delete owned tags with a live search filter and two-step delete confirmation
- **Tag loans** — temporarily lend an owned tag to another player for a set duration (`30m`, `1h`, `1d`, etc.), with automatic expiry and return
- **Tag withdrawal & trading** — convert a custom tag into a tradeable Name Tag item (`/tag withdraw`) that can be sold on an AH, then redeemed back into an equippable tag by right-clicking it
- **PlaceholderAPI expansion** — `%swagtags_tag%`, `%swagtags_tag_space%`, and `%swagtags_tag_colon%` for chat/tablist integration
- **SwagJobs integration (optional)** — admins can grant job-preset tier tags directly through a job/tier picker GUI when SwagJobs is installed
- **Admin tools** — give tags directly, adjust player credits, and reload config without a restart

---

## Quick Links

| | |
|---|---|
| [Installation](getting-started/installation.md) | Get SwagTags running on your server |
| [Configuration](getting-started/configuration.md) | Every config.yml option explained |
| [Tag System](core-features/tag-system.md) | How the buy → approve → equip flow works |
| [Admin Commands](server-owners/admin-commands.md) | Full admin command reference |
| [Permissions](server-owners/permissions.md) | Permission nodes |

---

## Requirements

| Dependency | Required |
|---|---|
| Paper 1.20+ (built against Paper API 1.21) | Yes |
| Java 17 | Yes |
| PlaceholderAPI | No — needed only for `%swagtags_tag%` display placeholders |
| SwagJobs | No — needed only for the job-preset tag grant GUI |

> SwagTags does not format chat or tablist entries itself. Pair it with a placeholder-aware plugin such as TAB and use `%swagtags_tag%` (or `%swagtags_tag_space%` / `%swagtags_tag_colon%`) to actually display the equipped tag.
