# Tag System

The core loop is: **preview → buy → admin approval → equip**. Every tag a player owns is a `Tag` object with an 8-character hex ID, an owner UUID, a raw color-coded suffix string, a type, and a creation timestamp.

## Tag types

| Type | How it's created | Can be deleted by owner? | Can be withdrawn? |
|---|---|---|---|
| `CUSTOM` | Approved via the buy/approval flow, or redeemed from a withdrawn item | Yes (with refund) | Yes |
| `PRESET` | Reserved for a future preset-tag system (`preset_tags.enabled` in config); not currently creatable in-game | Yes (with refund) | No |
| `ADMIN_GIVEN` | Granted directly by an admin via `/tag give` (including SwagJobs preset-tier grants) | No | No |

## Buying a tag

`/tag buy <suffix>` runs the suffix through [validation](../getting-started/configuration.md) and, if it passes and you have enough credits, opens the **Confirm Tag Purchase** GUI. Confirming there:

1. Deducts `credits.price_per_tag` from your balance (if `credits.enabled`)
2. Creates a `PendingTag` entry (suffix + escrowed credits + timestamp), stored in `pending.yml`
3. Notifies every online player with `swagtags.admin`

The tag is **not** usable yet — it only becomes a real, equippable `Tag` once an admin approves it via the approval GUI (see [Creating Custom Tags](../server-owners/creating-custom-tags.md)).

## Pending request expiry

A background task (`checkPendingTagExpiry`, run every 30 minutes) auto-cancels any pending request older than `credits.pending_expiry_days` (default 7), refunding the escrowed credits and notifying the player if they're online.

## Deleting a tag

`/tag delete` with no argument lists your owned tags with numbers; `/tag delete <#>` deletes it. Non-`ADMIN_GIVEN` tags refund `credits.refund_percentage`% of `credits.price_per_tag`. A tag currently on loan to someone else cannot be deleted until the loan ends. The same flow exists as a two-step right-click confirmation inside the "Your Tags" GUI — see [GUI Selection](gui-selection.md).

## Duplicate protection

You can't own two tags with the exact same suffix string (case-insensitive) — `playerOwnsTagWithSuffix` blocks both `/tag buy` and item redemption if you already own an identical tag.

## Persistence

Tag ownership, equip state, credits, active loans, and pending approvals are all persisted through **SwagAPI's shared database service** (`IDatabaseService`), not local YAML files. SwagTags is a hard dependency on SwagAPI — if SwagAPI isn't loaded, SwagTags disables itself at startup rather than falling back to flatfiles.

Each of these lives in its own table (`swagtags_tags`, `swagtags_equipped`, `swagtags_loans`, `swagtags_pending`, `swagtags_credits`), all loaded into memory once on `onEnable`. Every mutating action (equip, delete, buy, loan, credit change, etc.) writes straight through to the database at the time it happens via `DatabaseManager`, dispatched asynchronously through `dbService.executeAsync(...)` so gameplay never blocks on the JDBC round-trip. The underlying connection pool is owned and closed by SwagAPI, not SwagTags.

If you're upgrading from a pre-SwagAPI version of SwagTags, any legacy `tags.yml`, `equipped.yml`, `loans.yml`, `pending.yml`, and `playerdata/*.yml` files found in the plugin's data folder are automatically imported into the shared database on first startup (insert-if-absent, safe to re-run), then renamed with an `.imported` suffix so they aren't re-imported.
