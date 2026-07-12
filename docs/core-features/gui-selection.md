# GUI Selection

## Your Tags — `/tag`

Running `/tag` with no arguments opens a 54-slot inventory titled **Your Tags**. Layout:

- **Slots 0–44** — your owned tags, one item per tag:
  - **Equipped tag**: lime name tag, green checkmark name, "Right-click to unequip"
  - **Owned, unequipped tag**: yellow name tag, "Left-click to equip / Right-click to delete"
  - **Loaned-out tag**: clock icon, dark gray "[On Loan]" label, shows who it's loaned to and time remaining — cannot be equipped or deleted while loaned
  - A gray glass separator plus any tags **loaned to you** by other players (clock icon, aqua label), which you can equip/unequip independently of your own tags
- **Slot 45** — Search button. Left-click prompts you to type a filter term in chat (matched against the suffix with colors stripped); right-click clears an active filter.
- **Slot 48** — Credits display: current balance and the per-tag price (or "FREE" if `credits.enabled: false`).
- **Slot 49** — Tag statistics: custom tag count, total tag count, and how many tags are loaned to you.
- **Slot 50** — Help panel listing the relevant commands.

### Equip / unequip

Left-click an unequipped, non-loaned tag to equip it (this also clears any admin override suffix and un-equips any loaned tag you were wearing). Left-click an already-equipped tag and nothing happens except a reminder message. Right-click an equipped tag to unequip it immediately — no confirmation needed.

### Two-step delete

Right-clicking a non-equipped, non-loaned owned tag doesn't delete it immediately. The first right-click swaps the item for a red concrete "⚠ Click again to confirm delete" prompt; a second right-click on the same slot within **5 seconds** actually deletes it (with the configured refund). Left-clicking during that window cancels the pending delete instead of equipping. If the 5-second window expires, the GUI silently reverts to the normal item if you still have it open.

### Search filter

Typing a term in chat after clicking Search filters the tag list to suffixes containing that term (case-insensitive, colors stripped). Type `cancel` to back out without setting a filter. The filter persists across re-opens of the GUI within the same session and is cleared when you close the inventory normally (not when it's programmatically reopened after typing a filter).

## Confirm Tag Purchase

A 27-slot confirmation screen opened from `/tag buy`, showing a live preview (slot 13), Cancel (slot 18), Edit (slot 22 — lets you retype the suffix in chat before re-opening this same confirmation), and Submit for Approval (slot 26).

## Pending Tag Approvals — `/tag admin`

Admin-only 54-slot queue GUI. See [Creating Custom Tags](../server-owners/creating-custom-tags.md) for the full approval workflow.

## Confirm Tag Redemption

Opens automatically when you right-click a withdrawn tag item. See [Loans & Trading](loans-trading.md).

## Give Job Tag / Select Prestige Tier

Two-level admin GUI opened by `/tag give <player>` (no suffix) when SwagJobs is installed. See [SwagJobs Integration](../server-owners/swagjobs-integration.md).
