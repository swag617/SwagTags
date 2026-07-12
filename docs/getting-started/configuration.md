# Configuration

All settings live in `plugins/SwagTags/config.yml`. SwagTags tracks a `config-version` key and migrates missing keys forward automatically when you update the plugin — it will only ever *add* new keys with their defaults, never overwrite a value you've already changed. Run `/tag reload` after editing.

## Full reference

```yaml
# SwagTags Configuration
config-version: 3
tags:
  default: ""
  format: "{PREFIX}{PLAYER}{SUFFIX}"
  permission-error: "&cYou don't have permission to use this tag."

# --- Credit & Purchase System ---
credits:
  enabled: true
  price_per_tag: 10
  starting_credits: 0
  insufficient_credits_msg: "&cYou need {PRICE} credits to buy a custom tag. You have {BALANCE}."
  purchase_success_msg: "&aYou bought a custom tag for {PRICE} credits! You now have {BALANCE} credits."
  refund_percentage: 50  # Percentage refunded when deleting a tag
  pending_expiry_days: 7  # Auto-cancel + refund a pending approval request after this many days with no admin response

# --- Tag Validation ---
validation:
  enabled: true
  min_length: 2
  allow_colors: true
  allow_special_chars: true
  blocked_chars: []  # Add specific characters to block, e.g., ["#", "$", "@"]
  blacklist:
    - "badword"
    - "inappropriate"
  # Messages
  too_long_msg: "&cTag is too long! Maximum {MAX} characters."
  too_short_msg: "&cTag is too short! Minimum {MIN} characters."
  blocked_char_msg: "&cTag contains blocked characters!"
  blacklist_msg: "&cThat tag contains inappropriate content."
  already_owned_msg: "&cYou already own this exact tag!"

# --- Preset Tags (Future Implementation) ---
preset_tags:
  enabled: false
  unlock_message: "&aYou unlocked a new tag: {TAG}"

# --- Tag Withdrawal (convert a custom tag into a tradeable item for the AH) ---
withdraw:
  enabled: true
```

## Key groups explained

### `tags.*`

| Key | Default | Purpose |
|---|---|---|
| `tags.default` | `""` | Reserved for a default tag string; not currently wired to any equip logic |
| `tags.format` | `"{PREFIX}{PLAYER}{SUFFIX}"` | Reserved format template — actual display goes through PlaceholderAPI, not this key |
| `tags.permission-error` | `"&cYou don't have permission to use this tag."` | Reserved error message string |

### `credits.*`

| Key | Default | Purpose |
|---|---|---|
| `credits.enabled` | `true` | If `false`, tags cost nothing and the credits balance/checks are skipped entirely |
| `credits.price_per_tag` | `10` | Cost to submit a new custom tag |
| `credits.starting_credits` | `0` | Balance a player starts with the first time their credits file is read |
| `credits.insufficient_credits_msg` | see above | Supports `{PRICE}` and `{BALANCE}` placeholders |
| `credits.purchase_success_msg` | see above | Supports `{PRICE}` and `{BALANCE}` placeholders (currently informational — actual success feedback is sent directly in code) |
| `credits.refund_percentage` | `50` | Percent of `price_per_tag` refunded when a player deletes a custom tag |
| `credits.pending_expiry_days` | `7` | A pending approval request auto-expires (with a full refund of its escrowed credits) after this many days with no admin action |

### `validation.*`

Controls what suffixes are accepted by `/tag buy` and `/tag preview`, checked in this order: minimum length → special-character restriction (if `allow_special_chars: false`) → `blocked_chars` → `blacklist` (case-insensitive substring match) → color-tag syntax (unclosed `<gradient>`/`<rainbow>` tags, unknown gradient color stops). `min_length` is measured against the suffix with **all color codes stripped**.

### `preset_tags.*`

Present in config but not yet wired to any feature in the codebase — treat as reserved for a future release, not something you can currently configure the effect of.

### `withdraw.*`

| Key | Default | Purpose |
|---|---|---|
| `withdraw.enabled` | `true` | If `false`, `/tag withdraw` is rejected outright; existing withdrawn items already in players' inventories can still be redeemed |

## Message placeholders

Message strings use `&`-style color codes (translated automatically) plus a small set of `{PLACEHOLDER}` tokens specific to that message — `{PRICE}`, `{BALANCE}`, `{MIN}`, `{MAX}`, `{TAG}`. Tokens not listed in the table for a given key are not substituted.
