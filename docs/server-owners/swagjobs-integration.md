# SwagJobs Integration

SwagTags has an optional, soft-dependency integration with **SwagJobs** (listed in `plugin.yml` under `softdepend`) for granting job-themed preset tags. SwagJobs is a private/internal plugin — this page only documents the integration surface visible from SwagTags' side, not SwagJobs' own internals.

## Availability check

The integration is entirely optional and self-disabling: `JobTagGUI.isAvailable()` checks `Bukkit.getPluginManager().getPlugin("SwagJobs") != null` before anything job-related is offered. If SwagJobs isn't installed, `/tag give <player>` (no suffix) simply tells the admin it's unavailable and suggests the plain `/tag give <player> <suffix>` form instead. Nothing else in SwagTags is affected by SwagJobs' presence or absence.

## What the integration does

When SwagJobs **is** installed, `/tag give <player>` (with no suffix) opens a two-level admin GUI instead of requiring a typed suffix:

1. **Give Job Tag** (level 1) — one slot per job SwagJobs reports via its `TagManager#getJobNames()`, plus a "Custom Tag" button that falls back to typing a free-form suffix in chat (creates a normal `ADMIN_GIVEN` tag via SwagTags, unrelated to any job).
2. **Select Prestige Tier** (level 2) — after picking a job, one slot per prestige tier SwagJobs reports via `TagManager#getTierNames()` (e.g. Novice/Expert/Master, though the exact tier set is defined by SwagJobs, not SwagTags).

Clicking a tier calls `TagManager#grantPresetTag(targetUUID, job, tier)` on SwagJobs directly — SwagTags does **not** re-derive or duplicate the tag text for job tiers. SwagJobs remains the single source of truth for what each job/tier tag actually says; SwagTags only provides the picker UI and delegates the grant.

## Build-time note

SwagJobs is referenced as a `system`-scoped Maven dependency pointing at a local jar (`Libs/SwagJobs-1.0.0.jar`), not a published artifact — this is a private, internally-shared plugin between SwagTags and SwagJobs, so there's no public API documentation to link to here. If you're not part of that private ecosystem, the SwagJobs integration is simply inert: `/tag give <player> <suffix>` continues to work exactly as normal.
