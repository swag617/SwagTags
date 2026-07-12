# Creating Custom Tags

There are three distinct ways a tag ends up on a player's account. This page covers all three from the server-owner's side.

## 1. Player-submitted, admin-approved (the default flow)

This is the normal path: a player runs `/tag buy <suffix>`, confirms in the GUI, and their credits are escrowed while the request waits in the approval queue.

### Reviewing requests

`/tag admin` (requires `swagtags.admin`) opens the **Pending Tag Approvals** GUI — a 54-slot grid, one item per pending request (up to 45 shown at once). Each item shows:

- The requester's name (or "Offline Player" if they've disconnected)
- A rendered preview and the raw suffix text
- A **validation warning** if the suffix would currently fail validation (shown even though it already passed validation at submission time — useful if you've tightened the blacklist/rules since they submitted) — flagged with red wool instead of a name tag icon
- The player's current credit balance and how many custom tags they already own
- How many days the request has been pending

**Left-click** approves: creates the `CUSTOM` tag on the player's account and notifies them to `/tag` and equip it. **Right-click** denies and refunds the escrowed credits (if any), notifying the player.

There's no bulk-approve — each request is handled individually. Unhandled requests auto-expire and refund after `credits.pending_expiry_days` (default 7 days); see [Tag System](../core-features/tag-system.md).

### Tightening what's allowed

Everything a submitted suffix is checked against lives under `validation.*` in `config.yml` — minimum length, a blocked-character list, a word blacklist, and gradient/rainbow syntax validation. See [Configuration](../getting-started/configuration.md) for the full key reference. Changes apply to *new* submissions immediately after `/tag reload`; they don't retroactively invalidate already-approved tags.

## 2. Admin-granted custom tags

`/tag give <player> <suffix>` creates an `ADMIN_GIVEN` tag directly — no credits, no approval queue, still runs through the same validation rules. `ADMIN_GIVEN` tags can't be deleted or withdrawn by the player (only equipped/unequipped), so use this for permanent cosmetic rewards you don't want traded away.

## 3. SwagJobs preset tags

If SwagJobs is installed, `/tag give <player>` **without** a suffix opens a job/prestige-tier picker GUI instead. See [SwagJobs Integration](swagjobs-integration.md) — the actual tag text for these is defined and owned by SwagJobs, not SwagTags.

## Setting up validation for your community

A reasonable starting point:

```yaml
validation:
  enabled: true
  min_length: 2
  allow_special_chars: true
  blocked_chars: []
  blacklist:
    - "badword"
    - "inappropriate"
```

Add server-specific slurs/impersonation terms to `blacklist` (matched as a case-insensitive substring against the suffix with colors stripped, so partial matches inside longer words will also be blocked — keep entries specific). Set `allow_special_chars: false` to restrict suffixes to `[a-zA-Z0-9 ]` only, which also blocks color-code syntax characters like `&`, `<`, `#`.
