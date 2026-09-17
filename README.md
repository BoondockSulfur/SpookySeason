# SpookySeason

Halloween event plugin for Paper/Folia **1.21.x and 26.x** — trick-or-treating, pumpkin rain, blood
moon nights, the Headless Horseman, an undead raid on a target your players have to defend, bat
swarms, jumpscares and season-end rewards.

Current version: **1.4.0**

## Features

| Feature | Trigger | Config section |
| --- | --- | --- |
| **Trick-or-Treat** | Right-click a door or a villager | `trickOrTreat`, `trickOrTreatVillagers` |
| **Blood moon night** | At night (world time 13000–23000): bossbar, ambient sound, soul particles, ghosts (vex) | `hauntedNight` |
| **Fog** | A short darkness pulse during the blood moon | `fog` |
| **Pumpkin rain** | Manually via `/pumpkinrain start` | `pumpkinRain` |
| **Headless Horseman** | At night near a random player, or as the leader of a raid wave; skeleton horse and wither skeleton share one health pool | `halloweenBoss` |
| **Bat swarms** | At night around random players | `batSwarm` |
| **Jumpscares** | Breaking blocks in the dark | `jumpScare` |
| **Halloween mobs** | Naturally spawning zombies and skeletons wear carved pumpkins, creepers drop them | `halloweenMobs` |
| **Undead raid** | Via `/spookyraid start` or its own calendar window: waves of undead march on a target that has to be defended | `raid` |
| **Season-end rewards** | Automatically when the calendar window closes, or by hand | `seasonEndRewards` |
| **Leaderboard** | Treats collected per player | `treatLeaderboard` |

Every feature respects `enabledWorlds`. The personal opt-out (`/spooky optout`) covers all
player-facing effects; the pumpkin heads and creeper drops of the Halloween mobs are a world effect
and deliberately excluded from it.

## Commands

`/spooky` — main command, no permission node of its own.

| Subcommand | Permission | Description |
| --- | --- | --- |
| `/spooky on` \| `off` | `spooky.admin` | Sets `active` in the config and starts or stops all features. **Not** a season end, so no rewards are handed out. |
| `/spooky status` | `spooky.admin` | Is the season active right now? |
| `/spooky reload` | `spooky.admin` | Reloads config and language files and restarts the managers. |
| `/spooky rewards` | `spooky.admin` | Hands out the season-end rewards now and resets the treat statistics. |
| `/spooky spawn boss` | `spooky.admin`, in game only | Spawns the boss next to you. Exempt from the auto-despawn. |
| `/spooky optout` | everyone, in game only | Toggles your own participation in all effects. |
| `/spooky top` | everyone | Treat leaderboard (top 10), can be disabled with `treatLeaderboard.enabled`. |
| `/spooky menu` | everyone, in game only | Opens the GUI: status, opt-out, volumes, leaderboard. |

| Command | Permission | Description |
| --- | --- | --- |
| `/spookyvolume <ambient\|ghost\|rain> <0.0–1.0>` | everyone, in game only | Personal volume per sound type. |
| `/spookyvolume reset` | everyone, in game only | Resets **all** of your own settings, opt-out included. |
| `/pumpkinrain <start\|stop>` | `spooky.pumpkinrain` | Starts/stops the pumpkin rain. `start` runs **independently** of the season and of `pumpkinRain.enabled`. |
| `/spookyboss spawn` | `spooky.admin`, in game only | Same as `/spooky spawn boss`. |
| `/spookyboss remove` | `spooky.admin` | Removes the active boss. |
| `/spookyboss info` | `spooky.admin` | Shows the full boss configuration and its current health pool. |
| `/spookyboss set <health\|damage\|speed\|spawnChance\|enabled> <value>` | `spooky.admin` | Changes a boss value and saves the config. |
| `/spookyboss armor <helmet\|chestplate\|leggings\|boots\|weapon> <MATERIAL\|none>` | `spooky.admin` | The rider's equipment. |
| `/spookyboss ability <witherOnHit\|witherDurationTicks\|fireResistance\|charge\|chargeIntervalSeconds> <value>` | `spooky.admin` | The boss's abilities. |
| `/spookyboss loot <add MATERIAL:amount \| remove index \| list>` | `spooky.admin` | The boss's loot table. |
| `/spookyraid start` | `spooky.admin` | Starts a raid immediately, independently of the season and the calendar window. |
| `/spookyraid stop` | `spooky.admin` | Aborts a running raid: no victory, no defeat, no rewards. |
| `/spookyraid status` | `spooky.admin` | State, wave, attackers alive, target integrity, number of defenders. |
| `/spookyraid target info` | `spooky.admin` | Shows the mode, the objective positions, the zone and the region. |
| `/spookyraid target mode <objective\|zone\|region>` | `spooky.admin` | Switches between the three kinds of target. |
| `/spookyraid target set` | `spooky.admin`, in game only | Sets the objective position to where you stand. |
| `/spookyraid target add` \| `list` \| `remove <n>` \| `clear` | `spooky.admin` | Manages several objectives at once. |
| `/spookyraid target region <name>` | `spooky.admin`, in game only | Picks a WorldGuard region as the target (has to exist in your own world). |
| `/spookyraid zone pos1` \| `pos2` | `spooky.admin`, in game only | Marks the two corners of your own zone. |
| `/spookyraid zone save <name>` | `spooky.admin`, in game only | Saves the zone and selects it as the target. |
| `/spookyraid zone list` \| `remove <name>` | `spooky.admin` | Shows or deletes your own zones. |

## Permissions

| Node | Default | Covers |
| --- | --- | --- |
| `spooky.admin` | `op` | All admin subcommands of `/spooky`, plus `/spookyboss` and `/spookyraid` in full |
| `spooky.pumpkinrain` | `op` | `/pumpkinrain` |

`/spookyvolume` and the player subcommands of `/spooky` (`optout`, `top`, `menu`) need no
permission.

## PlaceholderAPI

Registered automatically as soon as PlaceholderAPI is installed.

| Placeholder | Result |
| --- | --- |
| `%spooky_active%` | `Yes` / `No` — whether the season is currently active |
| `%spooky_treats%` | Number of treats the player has collected |
| `%spooky_optout%` | `Yes` / `No` — whether the player has opted out |
| `%spooky_language%` | The active language from the config |

## Configuration

`config.yml` is commented throughout. Three things worth knowing:

**When is the season active?** Only when `active: true` **and** the calendar window matches.
`activeWindow` always refers to **October**; `startDay: 0` or `endDay: 0` means "active all year".

**When are the rewards handed out automatically?** On the transition of the **calendar window** from
active to inactive — not on a manual `/spooky off`. Two consequences:

- With `activeWindow` at `0/0` (all year) there is never a transition and therefore **never** an
  automatic distribution. Only `/spooky rewards` is left.
- If the server was down across the transition, it is caught up: the last state lives in
  `reward-state.yml`.

**New config keys** are added on every start without overwriting existing values or comments.
Obsolete keys are not removed.

### The undead raid

The raid depends on neither `activeWindow` nor `active`. It only ever starts through
`/spookyraid start` or through its own window under `raid.schedule` — deliberately, so it can be
tested outside October without bending the season settings. `enabledWorlds` still applies, though:
if the target world is not listed, the start is refused.

Three kinds of target, switched with `raid.target.mode`:

| Mode | Target | Lost when … |
| --- | --- | --- |
| `objective` | One or **several** points with their own health. The plugin tracks the health itself, so the value is free and **not** capped at 1024. Attackers deal damage through proximity (`reach`), not through real hits. | depending on `lose`: all of them / any one / a given number have fallen |
| `zone` | A box you **mark out yourself in game** — no other plugin needed. Every attacker that gets inside counts as a breach and is removed. | `breachLimit` breaches are reached |
| `region` | Like `zone`, but the area comes from a named **WorldGuard** region — for servers that keep their areas there anyway. | `breachLimit` breaches are reached |

**Your own zones** are marked out in game:

```
/spookyraid zone pos1          at one corner
/spookyraid zone pos2          at the opposite one
/spookyraid zone save village  saves it and selects it straight away
/spooky reload
```

`/spookyraid zone list` shows them all, `remove <name>` deletes one. They live in
`raid.target.zones` as `world,x1,y1,z1,x2,y2,z2` and can be maintained by hand there too.

**Important:** the spawn ring has to sit **outside** the zone. Otherwise the attackers appear inside
it, count as breaches immediately, and the raid is lost within seconds without a single mob ever
walking. The plugin warns at startup with the minimum distance needed.

The `region` mode needs WorldGuard **with a working region lookup**; if it is missing, the start
reports that properly instead of quietly doing nothing.

**Several objectives** go into `raid.target.objective.points` — one line each as `world,x,y,z` or
`world,x,y,z,health`, all in the same world. While the list is empty, the single point from
`world/x/y/z` applies. Easiest in game:

| Command | Effect |
| --- | --- |
| `/spookyraid target add` | Adds your own position as another objective |
| `/spookyraid target list` | Lists all objectives with their number |
| `/spookyraid target remove <n>` | Removes one |
| `/spookyraid target clear` | Empties the list, the single point applies again |

`lose` decides when the raid is lost: `all` (default, only once every objective has fallen), `any`
(as soon as the first one does) or a number of fallen objectives.

**Why `style: display` is the default:** a mob as the target has a hitbox — 1.4 × 2.7 blocks for an
iron golem. Every sword swing that grazes it lands on the target, gets cancelled and is spent. Small
attackers in front of it, baby zombies above all, are then nearly impossible to hit. A display
object has no hitbox at all and therefore none of that. The mob style is still available through
`style: entity`.

How it runs: announcement with a countdown, then wave after wave of attackers walking at the target,
with a breather in between. A wave holds `baseMobs + (wave − 1) × mobsPerWave` attackers, but never
more than `maxAlive` at once.

### The wave leader

The leader of a wave is the plugin's existing Headless Horseman (`raid.waves.leaderEnabled`,
`leaderWave`, `0` = the last wave). He is the **only** attacker with a bar of his own — everything
else has its vanilla bossbar hidden through `raid.waves.hideBossBars`. If a boss happens to be
standing in the world already, that one is used rather than placing a second.

The plugin tracks his health itself, so it is **not** capped at 1024. Technically the entity only
ever holds one chunk of it: a blow that would kill it is intercepted and the visible health topped
back up while the pool still has reserves — hit feedback, knockback and the shared horse/rider pool
all stay as they were. The bossbar always shows the **pool**, not the entity.

Everything about him is editable individually, in `config.yml` under `halloweenBoss` or live:

```
/spookyboss info                       # including the current pool level
/spookyboss set health 1800
/spookyboss armor chestplate NETHERITE_CHESTPLATE
/spookyboss ability chargeIntervalSeconds 10
/spookyboss loot add NETHERITE_INGOT:2
```

The shipped 100 HP date back to when he only turned up at night near a single player. For a wave
leader facing a full server, 900–3000 is a workable starting range.

On **victory** `raid.rewards.victory` runs, on **defeat** `raid.rewards.consolation` — per defender,
meaning everyone who was within `participationRadius` of the target during the raid or killed an
attacker. After a defeat the place stays visibly marked for `raid.defeat.effect.durationSeconds`
(particles and darkness, **no** block changes); no new raid can start during that time, and
`/spookyraid stop` ends it immediately.

### How the attack works

Two separate paths, which explains most of the knobs:

| | against the target | against players |
| --- | --- | --- |
| Mechanic | Proximity: whatever is within `reach` strikes once per second | ordinary vanilla mob AI, real melee |
| Damage | `targetDamage`, raw — no armour, no resistance | `damage`, set as the attack attribute, reduced by armour |
| Animation | the swing is triggered by the plugin | vanilla |

`targetDamage: -1` means "same as `damage`". Separate values are almost always better: player damage
has to stay survivable, target damage does not — with a single knob one of the two is inevitably
wrong.

**What the attackers go after** is controlled by `focus`:

- `target` (default) — the objective comes first. A defender is only engaged while within
  `playerAggroRange`; beyond that the attacker breaks off and walks on. Without it an entire wave
  trails a single player across the map while the target goes untouched. **Anything else an attacker
  picks up on its own is dropped** — withers otherwise attack every non-undead mob, and one stray
  chicken parks one of them 25 blocks from the target for the rest of the wave.
- `players` — plain vanilla behaviour, players first, however far away.

Both are attacked either way: players through the mob AI, the target through proximity.

### The attacker roster

**Who attacks is defined in `raid.waves.mobs`.** Each entry is an archetype; several entries may use
the same entity type, which is how a plain zombie and an armoured one become two different
attackers:

| Field | Meaning |
| --- | --- |
| `type` | Entity type (required) |
| `id` | Name used for the per-wave bookkeeping, defaults to the type |
| `weight` | Relative frequency among everything allowed in the wave |
| `fromWave` / `untilWave` | First and last wave it may appear in (`0` = no end) |
| `maxPerWave` | At most this many per wave (`0` = no limit) |
| `minPerWave` | At least this many per wave, drawn before the random picks (`0` = pure chance) |
| `health`, `damage`, `targetDamage`, `speed` | Override the wave-wide values; omit to inherit |
| `scale` | Size multiplier, needs MC 1.20.5+ |
| `name` | Overrides `waves.names` for this archetype |
| `ranged` | Shoots instead of closing in; automatic for bows and crossbows |
| `spawnRadiusMin` / `spawnRadiusMax` | An own spawn ring for this archetype instead of the wave-wide one |
| `equipment` | `weapon`, `offhand`, `helmet`, `chestplate`, `leggings`, `boots` |

**Equipment is actually used** — a zombie with an `IRON_SWORD` hits with it, and an archer shoots the
target from up to `raid.waves.rangedReach` blocks away instead of walking up to it. That needs help
from the plugin: the target has no hitbox, so a real arrow could never connect — the shot is
cosmetic and the plugin books the damage. Drop chances are always zero. Careful: an archetype with
a helmet of its own does not get a pumpkin and will burn in daylight.

Which archetype spawns is **drawn fresh every time**, weighted by `weight` among everything unlocked
for the current wave. The number of waves is set by `raid.waves.count`.

**`minPerWave` matters for rare archetypes.** With twenty-odd entries a `weight: 1` can mean a mob
never turns up at all — measured: five archetypes did not appear once across ten waves. A minimum
guarantees them. If the minimums add up to more than a wave holds, not all of them fit; the plugin
warns at startup with the wave number affected.

**Special mobs are pure configuration.** The shipped suggestion includes a reinforced zombie from
wave 3, an archer from wave 4 and up to three scaled-down withers from wave 8:

```yaml
      - id: lesser_wither
        type: WITHER
        weight: 1
        fromWave: 8
        maxPerWave: 3
        scale: 0.6
        health: 120
        damage: 6
        targetDamage: 12
        name: "§8Lesser Wither"
```

**Withers travel badly.** They move by their own flight control rather than pathfinding, and reliably
get stuck somewhere in the open. Rather than fighting that, let them appear **right at the target**
with `spawnRadiusMin: 0` and `spawnRadiusMax: 5`; the journey then does not happen at all. Measured:
distance consistently under 5 blocks instead of 25 to 70.

**Ender dragons are no use as attackers.** They can be entered as an archetype and do spawn (size and
health apply), but then they do not move: `EnderDragon` is not a `Mob`, so it has no pathfinding, and
its vanilla AI hangs off the End podium. Measured: two dragons stood on the same coordinate for 36
seconds. For something airborne use phantoms; for a third kind of boss, the scaled-down wither.

**Names per mob type** go into `raid.waves.names` — `default` applies to everything without an entry
of its own, and an empty string means "no name" (the default). Colour codes with `§` work, and
`{wave}` is replaced with the wave number:

```yaml
    nameVisible: true
    names:
      default: "§7Risen"
      ZOMBIE: "§2Rotting One (wave {wave})"
      WITHER_SKELETON: "§8Black Bone"
```

`nameVisible` only controls the text above their heads; it is off deliberately, because a large wave
turns into a forest of name plates. The name still has an effect while hidden — it shows up in death
messages.

### Things that will catch you out

- **`peaceful` difficulty**: the server refuses every monster spawn there. The start therefore
  refuses with a clear message instead of letting a wave run into nothing.
- **`respectRegionProtection`** (default `true`) checks every single spawn point against WorldGuard
  and GriefPrevention. If the event area itself is a protected region, nothing gets through — the
  raid reports that in the log and runs with fewer attackers. Either move the spawn ring outwards
  (`spawnRadiusMin`/`spawnRadiusMax`) or turn this off for the event world.
- **`raid.waves.dropLoot`** is `false` deliberately. Otherwise the raid is a mob farm.
- **For a large event, raise `maxAlive` first.** That is the real limit on how much pressure arrives
  at once — `baseMobs` alone does nothing while the cap sits at 40. Rough starting points are in the
  config comments.
- **After a defeat the raid ends by itself.** Attackers are cleared, the consolation reward goes out,
  the place stays marked for `raid.defeat.effect.durationSeconds` (default 20 s), and then everything
  returns to idle and a new raid can be started. `raid.defeat.effect.enabled: false` finishes the
  instant the last target falls.
- **Attackers do not hurt each other** (`raid.waves.friendlyFire`, off by default). One stray arrow
  is enough otherwise: a skeleton hits a zombie, the zombie turns on the skeleton, and the wave thins
  itself out before it reaches the target. Both are blocked — the hit itself **and** the damage over
  time from wither, poison and magic, which arrives later with no damager attached. Measured over 75
  seconds with four withers in the wave: 1 loss with the protection, 7 without.
- **Attackers that stop making progress get a nudge** (`unstickSpeed`). A path does not move
  everything: a wither flies by its own control and otherwise just hovers, and ground mobs get caught
  on terrain. What is measured is simply whether the distance to the target is shrinking.
- **Spawn points sit on the ground, not in treetops.** `getHighestBlockYAt` returns the canopy over a
  forest, so the search walks down from there for the first solid block that is neither leaves nor
  logs and has two blocks of clear space above it. That also rules out spawning in water or under an
  overhang. The search only goes a few blocks below the surface — going all the way down finds the
  first cave, and an attacker in one is stuck there for the whole raid. If nothing is found in a
  column, the attempt counts as failed and another spot is rolled.
- **Withers and dragons bring their own vanilla bossbar.** `raid.waves.hideBossBars` (on by default)
  hides it — with three withers in a wave they otherwise fill the screen and push the raid's own two
  bars out of view. The wave leader keeps his, because that one is drawn by the plugin.
- **`raid.debug: true`** logs each attacker's position, distance to the target, actual target and
  whether it has a path. Very noisy, but exactly the tool for "the attackers never arrive".

A `/spooky reload` does **not** abort a running raid: it carries on with the configuration snapshot
taken when it started. `/spooky off` does abort it.

### First install on a server

On its first start the plugin creates `config.yml` and the language files and is live from that
moment. Three things worth knowing **beforehand**:

- **`enabledWorlds` ships empty, and empty means ALL worlds** — the build world included. Enter the
  worlds you want before the first start or right after, then `/spooky reload`.
- **`activeWindow` is set to 27–31 October.** Outside that window, trick-or-treat, the blood moon,
  ghosts, bats, jumpscares and the boss auto-spawn stay dormant by themselves.
- **The raid does not depend on the season window**, but only starts through `/spookyraid start` or
  through `raid.schedule`, and that is off by default. It will not go off unasked.

### Files in the plugin folder

| File | Contents |
| --- | --- |
| `config.yml` | Configuration |
| `lang/en.yml`, `lang/de.yml` | Messages; put your own languages here and set `language` |
| `player-prefs.yml` | Volumes and opt-out per player |
| `player-stats.yml` | Treats collected per player |
| `pending-rewards.yml` | Rewards for players who were offline at the season end |
| `reward-state.yml` | Season state and the date of the last distribution |
| `raid-state.yml` | Date of the last automatically started raid |

## Integrations

All optional, and only active when the plugin is present (`softdepend`):

- **WorldGuard** / **GriefPrevention** — keep spawns and trick-or-treat out of protected areas.
  Controlled through `regionIntegration`; if a check fails, it warns instead of waving things
  through silently. WorldGuard additionally allows a named region to be used as a raid target.
- **PlaceholderAPI** — see above.
- **CustomJukebox** — plays a disc of your own during blood moon nights instead of the vanilla cave
  ambience (`customJukebox.ambientDiscId`), and during a raid (`raid.music.discId`).
- **bStats** — anonymous usage statistics (project ID 30933), can be disabled in
  `plugins/bStats/config.yml`.

## Compatibility

One JAR covers both version lines: compiled against the Paper 26.1.2 API, `api-version: '1.21'` as
the minimum, `folia-supported: true`. The attributes renamed in MC 1.21.3 (`GENERIC_MAX_HEALTH` →
`MAX_HEALTH`) are resolved at runtime through the registry (`util/Attributes`, with a fallback to the
legacy keys) rather than through enum constants.

Evidence for 1.3.0:

- **Bytecode check**: all 302 API references in the built JAR (Bukkit/Paper, Adventure, Gson) were
  resolved against the API JARs of Paper **1.21**, **1.21.4**, **26.1.2** and **26.2** — complete in
  all four. Attributes appear, as expected, only as a `Registry.ATTRIBUTE` lookup, never as a
  version-bound enum constant.
- **Live test on Folia 26.2** (test server): loading, enabling, all console commands,
  `/spooky reload`, config and language file creation and a clean shutdown — no errors.

For 1.4.0 the raid was tested extensively on Folia 26.2 and on a Paper 26.1.2 server: all start
refusals, the full state machine across ten waves, every archetype of a 21-entry roster with its
equipment, ranged combat, the separate damage values, the boss health pool past 1024, friendly-fire
protection, and a zone raid through to defeat.

**Limits of that testing:** most of it ran through the console with no player logged in. The
player-facing paths — bossbars, titles, sounds, the rewards handed to defenders, the GUI, jumpscares
and trick-or-treat — are **not** verified that way. Anyone repeating this should set `activeWindow`
to `0/0`, otherwise practically every feature bows out again outside October and the test only
really checks the startup.

## History

⚠️ The original source (up to v1.1.0) was lost — not locally, not in the backups (D1-P1 / T7) and not
on GitHub. This project was decompiled from the built `spookyseason-1.1.0.jar` on 2026-07-01
(CFR 0.152) and rebuilt and cleaned up as a Maven project on 2026-07-02 (v1.2.0). The current state
is v1.4.0, see [CHANGELOG.md](CHANGELOG.md).

- `jars/` — the rescued original builds (`0.0.1` … `1.1.0`). The only reliable original state.
- `src-decompiled/` — the unmodified CFR decompilation of `1.1.0` (reference).
- `resources/` — the unmodified resources from `1.1.0` (reference, like `src-decompiled/`).
- `src/main/` — the working, fixed source base. The embedded bStats copy was replaced by the real
  `org.bstats:bstats-bukkit` dependency with shade relocation.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## Build

```bash
mvn package        # → target/spookyseason-1.4.0.jar
```

Requires **JDK 25**. The bytecode itself targets Java 21 (`maven.compiler.release`), but the
`paper-api` for MC 26.1.2 ships as class file version 69 (Java 25) — with a JDK 21 even reading the
dependency fails ("cannot access org.bukkit.Bukkit").
