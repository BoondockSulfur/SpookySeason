# Changelog

## 1.5.0

### Added
- Zone targets (`raid.target.mode: zone`): a box defined in the plugin configuration and set up in
  game with `/spookyraid zone pos1|pos2|save`. Requires no other plugin. The plugin warns at start
  if the spawn ring lies inside the zone.

### Fixed
- Defenders' splash potions had no effect on attackers while `raid.waves.friendlyFire` was off:
  wither, poison and magic damage was cancelled regardless of its source. Only damage caused by the
  raid itself is cancelled now.
- Ranged attackers walked up to the objective instead of stopping at `raid.waves.rangedReach`.
  An attacker within reach of a living objective now holds its position.
- The wave leader survived a wave timeout and was never removed afterwards.
- The objective target counted as ready before its visuals had been placed. With
  `countdownSeconds: 0` a failed placement went unnoticed.
- Trading with a villager during the trick-or-treat cooldown showed the cooldown message on every
  click.
- Periodic tasks logged exceptions without a stack trace.

### Changed
- Rewards are items and experience given through the inventory API instead of console commands.
  Raid rewards are configured under `raid.rewards.victory` and `raid.rewards.consolation`,
  season-end rewards under `seasonEndRewards.ranks`, each with an `items` list
  (`MATERIAL:amount`) and an `xp` value. Items that do not fit into the inventory are dropped at
  the player's feet. Existing configurations are migrated on start: the old command lists are
  replaced by the new sections and `seasonEndRewards.commands` is removed.
- Configuration keys of removed features are cleaned up on every start.
- The entity scheduler is resolved on the `Entity` interface instead of the concrete entity class.
- Zone corner selections are stored in a concurrent map.
- Code defaults for `raid.target.objective.reach` and `raid.defeat.effect.durationSeconds` match
  the shipped configuration.
- `/spookyraid` usage lists `zone`.

## 1.4.0

### Added
- Undead raid (`raid`, `/spookyraid`): waves of attackers march on a defended target. Wave count,
  size, growth per wave, alive cap, spawn ring, timeout and attacker roster are configurable.
- Targets: one or more objectives with their own health pool (`objective`, `points`, loss
  condition `all`/`any`/number, floating health readout), or a named WorldGuard region with a
  breach counter (`region`).
- Attacker roster (`raid.waves.mobs`): archetypes with equipment, `scale`, own stats, wave
  windows, per-wave minimum and maximum, own spawn ring and name. The flat `composition` list
  remains supported while `mobs` is empty.
- Ranged combat: attackers carrying a bow or crossbow shoot the target from up to `rangedReach`.
- Separate damage values against players (`damage`, reduced by armour) and against the target
  (`targetDamage`, raw); `focus` and `playerAggroRange` control what attackers pursue.
- The Headless Horseman leads one wave (`leaderEnabled`, `leaderWave`). Its health is tracked by
  the plugin and no longer capped at 1024; `/spookyboss info` shows the current pool.
- Own calendar window for automatic starts (`raid.schedule`), independent of `activeWindow`, at
  most once per day (`raid-state.yml`).
- Two boss bars (wave progress, target integrity), shown to nearby players only.
- Rewards per defender for victory and defeat (`raid.rewards`); after a defeat the site stays
  marked for `raid.defeat.effect.durationSeconds`.
- `friendlyFire` (off by default): attackers do not damage each other, including damage over time
  from wither and poison.
- `hideBossBars`: the vanilla boss bars of withers and dragons are hidden.
- `unstickSpeed`: attackers that stop making progress towards the target are nudged.
- `names` per mob type, `nameVisible`, `babies`, `dropLoot`, `noGriefing`, `pumpkinHeads`.
- `raid.debug`: per-attacker diagnostics in the log.
- `RegionIntegration` can query named WorldGuard regions.

### Fixed
- Spawn points no longer land in treetops or caves.
- Attackers no longer lose the target to animals or other mobs.
- Starting a raid on `peaceful` difficulty is refused instead of running empty.
- A failed rider spawn no longer leaves an orphaned skeleton horse (also affects
  `/spookyboss spawn`).
- One warning per wave for failed spawn attempts instead of one per attempt.

## 1.3.0

### Fixed
- Folia: scheduled tasks were never cancelled because the cancel method was resolved on a
  package-private implementation class. Every `/spooky reload` left an additional set of timers
  running. Scheduler access now goes through the public API interfaces and a failed cancellation
  is logged.
- Folia: bat swarms failed because `Bukkit.getCurrentTick()` is invalid on the global scheduler.
  Lifetimes are based on `System.currentTimeMillis()`.
- Folia: the boss tick was dropped silently when the entity vanished with its chunk, blocking all
  further boss spawns until a restart. `runEntityTimer` takes a retired callback.
- Folia: boss removal was scheduled at the spawn position instead of the entity's current region.
- Season-end rewards were skipped when the server was offline across the end of the season. The
  state is kept in `reward-state.yml`.
- `/spooky off` triggered the season-end rewards. Rewards now depend on the calendar window only.
- Villager trading was blocked for the whole season; the click is only cancelled when a
  trick-or-treat roll happens.
- Folia: reward commands run on the region thread of the rewarded player; missed rewards are
  re-queued.
- Folia: `removeAllPluginEntities()` no longer walks `World#getEntities()` across regions; the GUI
  is reopened one tick after the click; orphaned entities are removed one tick after
  `EntitiesLoadEvent`; `pending-rewards.yml` and `reward-state.yml` are written asynchronously.
- Boss bar updates send only differences; the update checker compares versions numerically;
  `/spookyboss` messages come from the language files; `ghostCooldown` is thread-safe.

## 1.2.0

### Fixed
- Language files: duplicate `command:` block that caused raw keys to be shown.
- WorldGuard and GriefPrevention checks were ineffective due to wrong reflection signatures.
- Boss: inverted season check on despawn; orphaned boss entities are removed on chunk load; boss
  bar leak on world change; configurable respawn cooldown (`halloweenBoss.respawnCooldownSeconds`).
- Reward duplication via relog; treat statistics are reset after distribution; global
  trick-or-treat cooldown (`trickOrTreat.globalCooldownSeconds`); hoppers cannot pick up pumpkin
  rain items.
- GUI: drag events are cancelled; the menu is detected via `InventoryHolder`.
- Folia: thread-safe player data with asynchronous saving; shared entity lists as
  `CopyOnWriteArrayList`; spawns use the planned region location; `onDisable` completes its cleanup.
- Jumpscares use the total light level; villager trick-or-treat respects `enabledWorlds`; off-hand
  double triggers are filtered; NaN/Infinity validation in `/spookyboss set`; treats are dropped
  when the inventory is full.
