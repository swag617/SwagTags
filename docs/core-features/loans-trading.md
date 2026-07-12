# Loans & Trading

Two features let tags move between players without an admin: temporary **loans**, and permanent **withdrawal into a tradeable item**.

## Loans

Lend one of your owned tags to another online player for a fixed duration. While loaned, the tag is unusable by you — it's automatically unequipped if you had it on — and equippable only by the recipient.

```
/tag loan <tag#> <player> <duration>
```

Duration accepts `30m`, `1h`, `12h`, `1d`, `3d`, etc. (minutes/hours/days). A tag already on loan can't be loaned again until it's returned; you also can't loan a tag to yourself.

- **Recipient side**: the loaned tag appears in a separate "Loaned to You" section of the recipient's `/tag` GUI (clock icon), and can be equipped/unequipped there independently of their own tags.
- **Ending early**: the owner runs `/tag loanreturn <tag name>` (tab-completes against their currently loaned-out tags) to pull it back immediately.
- **Automatic expiry**: a repeating task (`checkLoanExpiry`, every minute) returns any expired loan automatically, unequipping it from the recipient and notifying both parties.
- **Viewing loans**: `/tag loanlist` shows your own outgoing loans. Admins (`swagtags.admin`) can pass a player name or `all` to inspect anyone's loans server-wide.

Deleting or withdrawing a tag that's currently on loan is blocked until the loan ends or is returned.

## Withdrawal & trading

`/tag withdraw <#>` converts an owned **`CUSTOM`** tag (not `PRESET` or `ADMIN_GIVEN`, and not currently on loan) into a physical Name Tag item, dropped into your inventory (or at your feet if your inventory is full). This is gated by `withdraw.enabled` in config.

The item:

- Displays the tag's rendered suffix as its item name
- Shows a `Raw:` lore line with the unformatted suffix text — this line survives anvil renaming, so it's the reliable way to verify what a listed item actually redeems to before buying it on an auction house
- Stores the suffix in a `PersistentDataContainer` key (`withdrawn_tag_suffix`) that anvils cannot touch — redemption is only ever driven by this PDC value, never the display name

Withdrawing removes the tag from your account entirely (no credit refund — the value now lives in the item). You can then trade or sell it through any generic auction house plugin.

### Redeeming

Right-clicking a withdrawn tag item (in air, on a block, on a mob, or on an armor stand — all four interaction paths are intercepted) opens a **Confirm Tag Redemption** GUI. Confirming:

1. Verifies you're still holding the exact item (re-checked at click time, in case it changed hands or was consumed)
2. Blocks redemption if you already own a tag with that exact suffix
3. Consumes one item from your hand
4. Creates a new `CUSTOM` tag under your account, ready to equip from `/tag`

> The known trade-off: an anvil can still change what a listing's *display name* shows on an AH. The `Raw:` lore line is the tamper-proof source of truth — buyers should check it, not the item's cosmetic name.
