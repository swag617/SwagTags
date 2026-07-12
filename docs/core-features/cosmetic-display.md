# Cosmetic Display

SwagTags manages *ownership and equip state* only — it does not hook chat or the tablist itself. Actually displaying a player's equipped tag is done through a **PlaceholderAPI expansion** (identifier `swagtags`), which any placeholder-aware plugin can consume. The plugin logs `"SwagTags enabled! TAB plugin will handle display via %swagtags_tag%"` on startup as a reminder of this design.

The expansion only registers if PlaceholderAPI is present on the server (`Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null`); if it's missing, the placeholders simply don't exist and display integration is skipped entirely.

## Placeholders

| Placeholder | Output when a tag is equipped | Output when none is equipped |
|---|---|---|
| `%swagtags_tag%` | `[TAG]` (gray brackets, no leading space) — for chat | `""` (empty string) |
| `%swagtags_tag_space%` | ` [TAG]` (leading space) — for tablist name formats | `""` |
| `%swagtags_tag_colon%` | `[TAG]:` (gray brackets, trailing gray colon) — for chat | `""` |

`[TAG]` is the tag's suffix run through SwagTags' color processor (see below) and wrapped in `ChatColor.GRAY` brackets, ending with `ChatColor.RESET`.

## What "equipped" resolves to

The active suffix returned to the placeholder follows this priority, evaluated fresh on every placeholder request:

1. **Admin override suffix**, if one is set for the player (an in-memory-only override, not currently exposed through any command)
2. **Equipped loaned tag** — a tag someone else loaned to this player, if it hasn't expired
3. **Equipped owned tag** — as long as it isn't currently loaned out to someone else (a loaned-out tag renders as no tag for its owner)
4. Otherwise, empty string

## Color format support

Suffixes support several color syntaxes simultaneously, all handled by `ColorUtil.processColors()`:

- Legacy codes: `&c`, `&l`, `&r`, ...
- Hex, three styles: `&#RRGGBB`, `<#RRGGBB>`, `{#RRGGBB}`
- MiniMessage-style named tags: `<red>`, `<bold>`, `<reset>`, plus common aliases (`<b>`, `<i>`, `<u>`, `<obf>`, ...)
- Multi-stop gradients: `<gradient:#FF0000:#FFFF00:#0000FF>text</gradient>` (also accepts named colors as stops)
- Rainbow: `<rainbow>text</rainbow>`

Hex/gradient/rainbow processing is only applied on server versions SwagTags detects as 1.16+ (checked via `Bukkit.getVersion()`); since this plugin targets Paper 1.20+, that's effectively always on.

> Any placeholder consumer (TAB, a chat plugin, a scoreboard plugin, etc.) that supports PlaceholderAPI can use these three placeholders — SwagTags makes no assumption about *where* the tag is rendered.
