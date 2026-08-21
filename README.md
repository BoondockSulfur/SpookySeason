# SpookySeason

Halloween-Event-Plugin für Paper/Folia **1.21.x und 26.x** — Trick-or-Treat, Kürbisregen,
Blutmond-Nächte, Kopfloser-Reiter-Boss, Fledermausschwärme, Jumpscares und Season-End-Rewards.

Aktuelle Version: **1.3.0**

## Features

| Feature | Auslöser | Config-Abschnitt |
| --- | --- | --- |
| **Trick-or-Treat** | Rechtsklick auf eine Tür oder einen Villager | `trickOrTreat`, `trickOrTreatVillagers` |
| **Blutmond-Nacht** | Nachts (Weltzeit 13000–23000): BossBar, Ambient-Sound, Seelen-Partikel, Geister (Vex) | `hauntedNight` |
| **Nebel** | Kurzer Darkness-Puls während der Blutmond-Nacht | `fog` |
| **Kürbisregen** | Manuell per `/pumpkinrain start` | `pumpkinRain` |
| **Kopfloser Reiter** | Nachts zufällig in der Nähe eines Spielers; Skelettpferd + Witherskelett teilen sich eine HP-Leiste | `halloweenBoss` |
| **Fledermausschwärme** | Nachts zufällig um Spieler | `batSwarm` |
| **Jumpscares** | Blockabbau im Dunkeln | `jumpScare` |
| **Halloween-Mobs** | Natürlich spawnende Zombies/Skelette tragen Kürbisköpfe, Creeper droppen Kürbisse | `halloweenMobs` |
| **Season-End-Rewards** | Automatisch beim Ende des Kalenderfensters, oder manuell | `seasonEndRewards` |
| **Bestenliste** | Gesammelte Treats pro Spieler | `treatLeaderboard` |

Alle Features respektieren `enabledWorlds`. Der persönliche Opt-out (`/spooky optout`) wirkt auf
alle spielerbezogenen Effekte; die Kürbisköpfe und Creeper-Drops der Halloween-Mobs sind ein
Welt-Effekt und davon bewusst ausgenommen.

## Befehle

`/spooky` — Hauptbefehl, ohne eigenes Permission-Node.

| Subcommand | Recht | Beschreibung |
| --- | --- | --- |
| `/spooky on` \| `off` | `spooky.admin` | Setzt `active` in der Config und startet bzw. stoppt alle Features. **Kein** Season-Ende — verteilt also keine Rewards. |
| `/spooky status` | `spooky.admin` | Ist die Season gerade aktiv? |
| `/spooky reload` | `spooky.admin` | Lädt Config und Sprachdateien neu und startet die Manager neu. |
| `/spooky rewards` | `spooky.admin` | Verteilt die Season-End-Rewards sofort und setzt die Treat-Statistik zurück. |
| `/spooky spawn boss` | `spooky.admin`, nur ingame | Spawnt den Boss neben dir. Vom Auto-Despawn ausgenommen. |
| `/spooky optout` | alle, nur ingame | Schaltet die eigene Teilnahme an allen Effekten an/aus. |
| `/spooky top` | alle | Bestenliste der Treat-Sammler (Top 10), abschaltbar über `treatLeaderboard.enabled`. |
| `/spooky menu` | alle, nur ingame | Öffnet das GUI: Status, Opt-out, Lautstärken, Bestenliste. |

| Befehl | Recht | Beschreibung |
| --- | --- | --- |
| `/spookyvolume <ambient\|ghost\|rain> <0.0–1.0>` | alle, nur ingame | Persönliche Lautstärke je Klangart. |
| `/spookyvolume reset` | alle, nur ingame | Setzt **alle** eigenen Einstellungen zurück — auch den Opt-out. |
| `/pumpkinrain <start\|stop>` | `spooky.pumpkinrain` | Startet/stoppt den Kürbisregen. `start` läuft **unabhängig** von Season und `pumpkinRain.enabled`. |
| `/spookyboss spawn` | `spooky.admin`, nur ingame | Wie `/spooky spawn boss`. |
| `/spookyboss remove` | `spooky.admin` | Entfernt den aktiven Boss. |
| `/spookyboss info` | `spooky.admin` | Zeigt die komplette Boss-Konfiguration. |
| `/spookyboss set <health\|damage\|speed\|spawnChance\|enabled> <Wert>` | `spooky.admin` | Ändert einen Boss-Wert und speichert die Config. |
| `/spookyboss armor <helmet\|chestplate\|leggings\|boots\|weapon> <MATERIAL\|none>` | `spooky.admin` | Ausrüstung des Reiters. |
| `/spookyboss ability <witherOnHit\|witherDurationTicks\|fireResistance\|charge\|chargeIntervalSeconds> <Wert>` | `spooky.admin` | Fähigkeiten des Bosses. |
| `/spookyboss loot <add MATERIAL:Anzahl \| remove Index \| list>` | `spooky.admin` | Loot-Tabelle des Bosses. |

## Rechte

| Node | Standard | Deckt ab |
| --- | --- | --- |
| `spooky.admin` | `op` | Alle Admin-Subcommands von `/spooky` sowie `/spookyboss` komplett |
| `spooky.pumpkinrain` | `op` | `/pumpkinrain` |

`/spookyvolume` und die Spieler-Subcommands von `/spooky` (`optout`, `top`, `menu`) brauchen kein Recht.

## PlaceholderAPI

Wird automatisch registriert, sobald PlaceholderAPI installiert ist.

| Platzhalter | Ergebnis |
| --- | --- |
| `%spooky_active%` | `Yes` / `No` — ob die Season gerade aktiv ist |
| `%spooky_treats%` | Anzahl gesammelter Treats des Spielers |
| `%spooky_optout%` | `Yes` / `No` — ob der Spieler ausgestiegen ist |
| `%spooky_language%` | Aktive Sprache aus der Config |

## Konfiguration

`config.yml` ist durchgehend kommentiert. Drei Punkte, die man kennen sollte:

**Wann ist die Season aktiv?** Nur wenn `active: true` **und** das Kalenderfenster passt.
`activeWindow` bezieht sich immer auf **Oktober**; `startDay: 0` oder `endDay: 0` bedeutet
„ganzjährig aktiv".

**Wann werden die Rewards automatisch verteilt?** Beim Übergang des **Kalenderfensters** von
aktiv nach inaktiv — nicht beim manuellen `/spooky off`. Zwei Folgen:

- Steht `activeWindow` auf `0/0` (ganzjährig), gibt es nie einen Übergang und damit **nie** eine
  automatische Verteilung. Dann bleibt nur `/spooky rewards`.
- War der Server über den Übergang hinweg aus, wird das nachgeholt: Der letzte Zustand steht in
  `reward-state.yml`.

**Neue Config-Keys** werden bei jedem Start ergänzt, ohne bestehende Werte oder Kommentare zu
überschreiben. Obsolete Keys werden nicht entfernt.

### Dateien im Plugin-Ordner

| Datei | Inhalt |
| --- | --- |
| `config.yml` | Konfiguration |
| `lang/en.yml`, `lang/de.yml` | Meldungen; eigene Sprachen hier ablegen und in `language` eintragen |
| `player-prefs.yml` | Lautstärken und Opt-out je Spieler |
| `player-stats.yml` | Gesammelte Treats je Spieler |
| `pending-rewards.yml` | Rewards für Spieler, die beim Season-Ende offline waren |
| `reward-state.yml` | Season-Zustand und Datum der letzten Verteilung |

## Integrationen

Alle optional, werden nur bei vorhandenem Plugin aktiv (`softdepend`):

- **WorldGuard** / **GriefPrevention** — verhindern Spawns und Trick-or-Treat in geschützten
  Bereichen. Steuerbar über `regionIntegration`; schlägt eine Prüfung fehl, wird gewarnt statt
  still durchgewinkt.
- **PlaceholderAPI** — siehe oben.
- **CustomJukebox** — spielt statt der Vanilla-Höhlengeräusche eine eigene Disc in der
  Blutmond-Nacht (`customJukebox.ambientDiscId`).
- **bStats** — anonyme Nutzungsstatistik (Projekt-ID 30933), abschaltbar in
  `plugins/bStats/config.yml`.

## Kompatibilität

Eine Jar deckt beide Versionslinien ab: kompiliert gegen die Paper-26.1.2-API, `api-version: '1.21'`
als Minimum, `folia-supported: true`. Die mit MC 1.21.3 umbenannten Attribute
(`GENERIC_MAX_HEALTH` → `MAX_HEALTH`) werden zur Laufzeit über die Registry aufgelöst
(`util/Attributes`, mit Fallback auf die Legacy-Keys) statt über Enum-Konstanten.

Für 1.3.0 belegt durch:

- **Bytecode-Prüfung:** Alle 302 API-Referenzen der gebauten Jar (Bukkit/Paper, Adventure, Gson)
  wurden gegen die API-Jars von Paper **1.21**, **1.21.4**, **26.1.2** und **26.2** aufgelöst —
  vollständig in allen vier. Attribute erscheinen dabei erwartungsgemäß nur als
  `Registry.ATTRIBUTE`-Lookup, nicht als versionsgebundene Enum-Konstante.
- **Live-Test auf Folia 26.2** (Testserver `mc-next`): Laden, Aktivieren, alle Konsolenbefehle,
  `/spooky reload`, Config-/Sprachdatei-Erzeugung und sauberes Herunterfahren — fehlerfrei.

**Grenze dieses Tests:** Es war kein Spieler eingeloggt. Alle spielerabhängigen Pfade sind damit
**nicht** live verifiziert — Boss-Spawn samt `retired`-Callback, Geister, Fledermausschwärme,
Trick-or-Treat, GUI und Jumpscares. Wer das nachholt: `activeWindow` auf `0/0` setzen, sonst
steigen außerhalb des Oktobers praktisch alle Features sofort wieder aus und der Test prüft
faktisch nur den Start.

## Historie

⚠️ Der Original-Quellcode (bis v1.1.0) ging verloren — weder lokal, noch in den Backups (D1-P1 / T7),
noch auf GitHub vorhanden. Dieses Projekt wurde am 2026-07-01 aus der gebauten
`spookyseason-1.1.0.jar` decompiliert (CFR 0.152) und am 2026-07-02 als Maven-Projekt rekonstruiert
und bereinigt (v1.2.0). Der aktuelle Stand ist v1.3.0, siehe Changelog.

- `jars/` — die geretteten Original-Builds (`0.0.1` … `1.1.0`). Einziger verlässlicher Originalstand.
- `src-decompiled/` — das unveränderte CFR-Decompilat von `1.1.0` (Referenz).
- `resources/` — die unveränderten Ressourcen aus `1.1.0` (Referenz, wie `src-decompiled/`).
- `src/main/` — die lauffähige, gefixte Quellbasis. Die eingebettete bStats-Kopie wurde durch die
  echte `org.bstats:bstats-bukkit`-Dependency mit Shade-Relocation ersetzt.

## Changelog

### 1.3.0

Schwerpunkt: Folia-Korrektheit und Zuverlässigkeit der Season-End-Rewards. Zwei der Fehler waren
ausschließlich im Live-Test auf Folia 26.2 sichtbar.

- **Timer wurden auf Folia nie abgebrochen:** `Scheduler.cancelTask()` suchte `cancel()` an der
  Implementierungsklasse des Task-Handles. Folias Implementierungen sind teils paketprivat, der
  Aufruf scheiterte — und eine leere `catch`-Klausel verschluckte den Fehler. Folge: **jedes
  `/spooky reload` ließ einen kompletten Timer-Satz zusätzlich weiterlaufen** (im Test messbar:
  3 → 4 → 5 Ticks pro Sekunde). Alle Scheduler-Zugriffe laufen jetzt über die öffentlichen
  API-Interfaces, ein fehlgeschlagener Abbruch wird geloggt statt verschluckt.
- **Fledermausschwärme funktionierten auf Folia gar nicht:** `Bukkit.getCurrentTick()` ist nur
  innerhalb eines Region-Ticks gültig und warf im Global-Scheduler jede Sekunde
  `No currently ticking region`. Die Lebensdauer läuft jetzt über `System.currentTimeMillis()`.
- **Boss blockierte sich auf Folia dauerhaft selbst:** Der Boss-Tick hängt am Entity-Scheduler.
  Verschwand die Entity mit ihrem Chunk (statt zu sterben), ließ Folia den Task still fallen —
  `bossUUID` blieb gesetzt und es spawnte bis zum Neustart kein Boss mehr. `runEntityTimer` nimmt
  jetzt ein `retired`-Callback, das den Zustand aufräumt.
- **Boss-Entfernung lief auf der falschen Region:** `removeBoss()` plante das Entfernen auf der
  *Spawn*-Position; der Reiter bewegt sich aber. Läuft jetzt über den Entity-Scheduler
  (`Scheduler.runOnEntity`), der die Entity immer in ihrer aktuellen Region erwischt.
- **Season-End-Rewards fielen aus, wenn der Server über den Übergang aus war:** `wasActive` lebte
  nur im Speicher und wurde bei jedem Start auf den aktuellen Stand gesetzt — die Flanke war damit
  verloren. Der Zustand steht jetzt in `reward-state.yml`.
- **`/spooky off` verteilte die Season-End-Rewards:** Der manuelle Schalter war für die
  Reward-Logik nicht von einem Season-Ende zu unterscheiden — ein `off` mitten in der Season
  schüttete die Belohnungen aus und setzte die Treat-Statistik zurück. Die Rewards hängen jetzt am
  Kalenderfenster (`isInSeasonWindow()`), nicht am `active`-Schalter.
- **Villager-Handel war die ganze Season blockiert:** Der Rechtsklick wurde bedingungslos
  abgebrochen, auch wenn wegen Cooldown gar kein Trick-or-Treat auslöste. Abgebrochen wird jetzt
  nur noch, wenn wirklich gewürfelt wird.
- **Reward-Commands auf Folia:** `give`-Commands liefen aus dem Global-Tick gegen fremde
  Spieler-Inventare; sie laufen jetzt im Region-Thread des Beschenkten. Verpasste Rewards (Logout
  während der Wartezeit) werden auch dann zurückgelegt, wenn Folia den Task gar nicht erst ausführt.
- **Weitere Folia-/Robustheitsfixes:** `removeAllPluginEntities()` iteriert auf Folia nicht mehr
  regionsübergreifend über `World#getEntities()`; das GUI wird nicht mehr mitten im Click-Event neu
  geöffnet; verwaiste Entities werden einen Tick nach `EntitiesLoadEvent` entfernt statt mitten
  darin; `pending-rewards.yml`/`reward-state.yml` werden entprellt asynchron geschrieben
  (`util/YamlSaver`).
- **Kleinkram:** BossBar der Blutmond-Nacht schickt nur noch Differenzen statt jede Sekunde alle
  Zuschauer neu; Update-Checker vergleicht Versionen numerisch statt auf Ungleichheit;
  `/spookyboss`-Meldungen kommen aus den Sprachdateien statt hartcodiert englisch; `ghostCooldown`
  thread-sicher; `printStackTrace()` durch Logger ersetzt.

### 1.2.0

- **Sprachdateien:** doppelter `command:`-Block entfernt — Spieler sahen vorher rohe Keys statt Meldungen.
- **RegionIntegration:** WorldGuard-/GriefPrevention-Checks waren wegen falscher Reflection-Signaturen
  komplett wirkungslos (stiller No-Op); jetzt korrekt via `BukkitAdapter.adapt()` bzw. `Claim`-Typ,
  mit Warnung statt stillem Durchwinken.
- **Boss:** invertierter Season-Check beim Despawn; verwaiste Boss-Mobs nach Chunk-Entladung werden
  jetzt per `EntitiesLoadEvent`-Cleanup entfernt; BossBar-Leak bei Weltwechsel; Respawn-Cooldown
  konfigurierbar (`halloweenBoss.respawnCooldownSeconds`).
- **Exploits:** Reward-Dupe per Relog geschlossen; Treat-Stats werden nach der Verteilung
  zurückgesetzt; globaler Trick-or-Treat-Cooldown (`trickOrTreat.globalCooldownSeconds`) gegen
  Tür-Farming; Hopper können Kürbisregen-Items nicht mehr einsammeln.
- **GUI:** `InventoryDragEvent` wird gecancelt (kein Item-Verlust mehr), Erkennung über
  `InventoryHolder` statt Titel-String.
- **Folia:** Player-Daten-Stores thread-sicher mit asynchronem, entprelltem Speichern; geteilte
  Entity-Listen als `CopyOnWriteArrayList`; Spawns nutzen die geplante Region-Location; `onDisable`
  bricht nicht mehr mitten im Cleanup ab; Scheduler cached Reflection-Lookups.
- **Kleinkram:** Jumpscare nutzt Gesamtlicht statt nur Blocklicht; Villager-Trick-or-Treat
  respektiert `enabledWorlds`; Off-Hand-Doppeltrigger gefiltert; NaN/Infinity-Validierung in
  `/spookyboss set`; volle Inventare droppen Treats statt sie zu verschlucken.

## Build

```bash
mvn package        # → target/spookyseason-1.3.0.jar
```

Benötigt Java 21+.
