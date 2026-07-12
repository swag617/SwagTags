# Quick Start

A walkthrough of the default player flow, from buying a tag to wearing it.

## 1. Preview before you buy

```
/tag preview &c[VIP]
```

Sends you a chat preview of how the suffix will render, plus its cost — no credits are spent and nothing is submitted.

## 2. Buy a tag

```
/tag buy &c[VIP]
```

This runs the suffix through validation (length, blocked characters, blacklist, color-tag syntax) and, if credits are enabled, checks your balance. If everything checks out, the **Confirm Tag Purchase** GUI opens showing a live preview with three options:

- **✔ Submit for Approval** — charges your credits and queues the tag for an admin to review
- **✎ Edit Tag** — type a replacement suffix in chat before submitting
- **✗ Cancel** — closes the GUI, nothing is charged

Once submitted, every online player with `swagtags.admin` gets a chat notification, and you'll be notified in-game when an admin approves or denies it.

## 3. Get approved

An admin opens `/tag admin` and left-clicks your request to approve it (or right-clicks to deny and refund your credits). On approval, the tag is created under your account and you get a chat notification.

## 4. Equip it

```
/tag
```

Opens the **Your Tags** GUI. Left-click any owned tag to equip it; left-click again (or right-click an equipped tag) to unequip. The GUI also shows your credit balance, tag statistics, and a search filter for players with many tags.

## 5. See it in-game

SwagTags itself doesn't inject anything into chat or the tablist. If your server has PlaceholderAPI and a placeholder-consuming plugin like TAB, add `%swagtags_tag%` (or `%swagtags_tag_space%` for a leading space, useful in tablist name formats) to that plugin's format string. See [Cosmetic Display](../core-features/cosmetic-display.md) for the exact placeholder set.

## Other things to try

| Command | What it does |
|---|---|
| `/tag delete` | Lists your owned tags with numbers so you can `/tag delete <#>` for a partial refund |
| `/tag loan <#> <player> <duration>` | Temporarily lends one of your tags to another player, e.g. `/tag loan 1 Notch 1d` |
| `/tag withdraw <#>` | Converts a custom tag into a tradeable Name Tag item you can sell on an AH |
| `/tag unequip` | Removes your currently equipped tag |
| `/tag help` | Full command list, expanded with admin commands if you have `swagtags.admin` |
