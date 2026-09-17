# Changelog

## 1.4.0

New: the **undead raid** — waves of undead march on a target your players have to defend. Purely
additive; no existing feature was changed or removed.

- **Wave system** (`raid.waves`): announcement with a countdown, wave after wave with a breather in
  between, a cap on how many attackers are alive at once, a weighted mix of mob types, and health
  and damage growing per wave. Attackers wear carved pumpkins so the undead do not burn when a raid
  runs into daylight.
- **Targets** (`raid.target.mode`): one or more objects with their own health at a fixed position, a
  zone you mark out yourself, or a named WorldGuard region with a breach counter. Objects only ever
  take damage from the raid's own attackers.
- **The wave leader** is the plugin's existing Headless Horseman, spawned through
  `HalloweenBossManager` without changing how it behaves elsewhere.
- **Its own calendar window** (`raid.schedule`), independent of `activeWindow`: month, days, start
  time and a tolerance window, at most one automatic start per day (`raid-state.yml`).
- **Two bossbars**: wave progress and target integrity, shown only nearby and sent as differences.
- **Separate rewards** for holding the line and for losing it (`raid.rewards`), handed out per
  defender. After a defeat the place stays visibly marked for a while (`raid.defeat.effect`) —
  particles and darkness, no block changes — and then the raid returns to idle by itself.
- **Folia**: the state machine ticks on the global scheduler and never touches entities or blocks
  directly; spawns go through the region scheduler of the target location, work on an attacker
  through that entity's scheduler, and tasks are only cancelled through `Scheduler.TaskHandle`.
  No `Bukkit.getCurrentTick()`.
- **Safety nets**: `enabledWorlds` applies to the raid as well; every spawn point is checked against
  WorldGuard and GriefPrevention (can be switched off); a wave that cannot finish ends after
  `timeoutSeconds`; attackers and targets are exempt from the general entity cleanup while a raid is
  running.
- `RegionIntegration` can now also query **named regions** (centre, existence, containment). If only
  that part fails, the existing spawn check is unaffected.

Added over the course of live testing:

- **Your own zones as a third kind of target** (`mode: zone`), marked out in game with
  `/spookyraid zone pos1|pos2|save` — no other plugin required. The WorldGuard mode remains
  alongside it. The plugin warns at startup if the spawn ring sits inside the zone.
- **Several targets at once** (`points`), with the loss condition `all` / `any` / a number, and a
  floating health readout above each one.
- **Archetypes instead of a flat mob list** (`raid.waves.mobs`): equipment, size (`scale`), own
  stats, wave windows (`fromWave`/`untilWave`), upper and lower bounds per wave, and an own spawn
  ring. The old `composition` list still applies while `mobs` is empty.
- **Real ranged combat**: anything carrying a bow or crossbow shoots the target from up to
  `rangedReach` blocks away instead of walking up to it.
- **Separate damage values** against players (`damage`, reduced by armour) and against the target
  (`targetDamage`, raw), plus `focus` / `playerAggroRange` for what attackers go after.
- **The boss has its own health pool** and is no longer capped at 1024; `/spookyboss info` shows the
  current level.
- **No more friendly fire** (`friendlyFire`, off by default) — including the damage over time from
  wither and poison, which arrives later with no damager attached.
- **No bossbar flood**: the vanilla bar withers and dragons bring along is hidden
  (`hideBossBars`); only the wave leader keeps one.
- **Attackers reach the target reliably**: spawn points no longer land in caves or treetops,
  animals no longer distract anyone, and whatever stops closing in gets a nudge (`unstickSpeed`).
- **Names per mob type** (`raid.waves.names`), baby zombies can be switched off (`babies`).
- `raid.debug` as a diagnostic for "the attackers never arrive".

Found during live testing on Folia 26.2 and on a real server, and fixed along the way:

- **The target swallowed sword swings.** As a mob it had a hitbox, so blows aimed at attackers in
  front of it landed on the target instead, got cancelled and were spent — baby zombies right in
  front of it were nearly impossible to hit. The target is now a display object with no hitbox at
  all by default.
- **The target's health was capped at 1024**, because it hung off the vanilla `max_health`
  attribute. A configured `health: 1200` silently became 1024 — far too little for an event with a
  lot of players. The plugin now tracks the health itself.
- **The attackers did not walk to the target.** After the rework there was nothing to target any
  more, and a path that had been set was overridden by the mob's own wander goals. Measured with
  `raid.debug`: `actualTarget=none`, distance unchanged over 84 seconds. Slipping in a decoy did not
  help — mob AI discards a `setTarget` aimed at something it would never attack on its own within
  the same tick. Solved through the path, which is now refreshed even when a targeting attempt does
  not stick.
- **Starting on `peaceful` difficulty** visibly ran empty: the server refuses every monster spawn,
  so the raid spawned nothing and ended after the wave timeout as a "victory". The start now refuses
  beforehand with its own message.
- **Orphaned skeleton horse on boss spawn**: if the rider could not be placed (on `peaceful`, for
  instance), the horse that had already spawned was left standing as a marked, untracked orphan.
  `spawnBoss()` now cleans it up. Affects `/spookyboss spawn` too, not just the raid.
- **Log flood**: one warning *per* failed spawn attempt, dozens of lines per wave. Now once per
  wave, plus the existing summary.
- **Baby zombies can be switched off** (`raid.waves.babies`), because they are small, quick and
  nearly impossible to hit in the scrum in front of the target.

## 1.3.0

Focus: Folia correctness and reliability of the season-end rewards. Two of these bugs were only
ever visible in a live test on Folia 26.2.

- **Timers were never cancelled on Folia**: `Scheduler.cancelTask()` looked for `cancel()` on the
  implementation class of the task handle. Some of Folia's implementations are package-private, so
  the call failed — and an empty `catch` clause swallowed the error. Result: **every
  `/spooky reload` left another full set of timers running** (measurable in testing: 3 → 4 → 5 ticks
  per second). All scheduler access now goes through the public API interfaces, and a failed
  cancellation is logged instead of swallowed.
- **Bat swarms did not work on Folia at all**: `Bukkit.getCurrentTick()` is only valid inside a
  region tick and threw `No currently ticking region` on the global scheduler every second. Lifetime
  now runs off `System.currentTimeMillis()`.
- **The boss blocked itself permanently on Folia**: the boss tick hangs off the entity scheduler. If
  the entity disappeared with its chunk instead of dying, Folia dropped the task silently —
  `bossUUID` stayed set and no boss spawned again until a restart. `runEntityTimer` now takes a
  `retired` callback that cleans the state up.
- **Boss removal ran on the wrong region**: `removeBoss()` scheduled the removal at the *spawn*
  position, but the rider moves. It now goes through the entity scheduler
  (`Scheduler.runOnEntity`), which always catches the entity in its current region.
- **Season-end rewards were skipped if the server was down across the transition**: `wasActive`
  lived only in memory and was reset to the current situation on every start, so the edge was lost.
  The state now lives in `reward-state.yml`.
- **`/spooky off` handed out the season-end rewards**: the manual switch was indistinguishable from
  the end of the season, so an `off` mid-season paid out the rewards and reset the treat statistics.
  The rewards now hang off the calendar window (`isInSeasonWindow()`), not the `active` switch.
- **Villager trading was blocked for the whole season**: the right-click was cancelled
  unconditionally, even when no trick-or-treat fired because of a cooldown. It is now only cancelled
  when the roll actually happens.
- **Reward commands on Folia**: `give` commands ran from the global tick against other players'
  inventories; they now run on the region thread of whoever is being rewarded. Missed rewards (a
  logout during the wait) are put back even when Folia never runs the task at all.
- **Further Folia and robustness fixes**: `removeAllPluginEntities()` no longer walks
  `World#getEntities()` across regions on Folia; the GUI is no longer reopened in the middle of the
  click event; orphaned entities are removed one tick after `EntitiesLoadEvent` rather than during
  it; `pending-rewards.yml` and `reward-state.yml` are written debounced and asynchronously
  (`util/YamlSaver`).
- **Smaller things**: the blood moon bossbar sends only differences instead of re-adding every
  viewer each second; the update checker compares versions numerically instead of testing for
  inequality; `/spookyboss` messages come from the language files instead of being hardcoded
  English; `ghostCooldown` is thread-safe; `printStackTrace()` replaced by the logger.

## 1.2.0

- **Language files**: duplicate `command:` block removed — players saw raw keys instead of messages.
- **RegionIntegration**: the WorldGuard and GriefPrevention checks were completely ineffective
  because of wrong reflection signatures (a silent no-op); now correct via `BukkitAdapter.adapt()`
  and the `Claim` type, warning instead of waving everything through silently.
- **Boss**: inverted season check on despawn; orphaned boss mobs after a chunk unload are now
  removed by the `EntitiesLoadEvent` cleanup; bossbar leak on world change; respawn cooldown made
  configurable (`halloweenBoss.respawnCooldownSeconds`).
- **Exploits**: reward dupe via relog closed; treat stats are reset after distribution; a global
  trick-or-treat cooldown (`trickOrTreat.globalCooldownSeconds`) against door farming; hoppers can
  no longer pick up pumpkin rain items.
- **GUI**: `InventoryDragEvent` is cancelled (no more lost items), detection via `InventoryHolder`
  instead of the title string.
- **Folia**: player data stores made thread-safe with asynchronous, debounced saving; shared entity
  lists as `CopyOnWriteArrayList`; spawns use the planned region location; `onDisable` no longer
  breaks off mid-cleanup; the scheduler caches its reflection lookups.
- **Smaller things**: jumpscares use total light instead of block light only; villager
  trick-or-treat respects `enabledWorlds`; off-hand double triggers filtered; NaN/Infinity
  validation in `/spookyboss set`; full inventories drop treats instead of swallowing them.

## 1.1.0 and earlier

The original source up to 1.1.0 was lost. Those builds survive only as JARs in `jars/`; see the
History section in the README.
