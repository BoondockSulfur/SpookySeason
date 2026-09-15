# SpookySeason

Halloween-Event-Plugin für Paper/Folia **1.21.x und 26.x** — Trick-or-Treat, Kürbisregen,
Blutmond-Nächte, Kopfloser-Reiter-Boss, Untoten-Überfall auf ein zu verteidigendes Ziel,
Fledermausschwärme, Jumpscares und Season-End-Rewards.

Aktuelle Version: **1.4.0**

## Features

| Feature | Auslöser | Config-Abschnitt |
| --- | --- | --- |
| **Trick-or-Treat** | Rechtsklick auf eine Tür oder einen Villager | `trickOrTreat`, `trickOrTreatVillagers` |
| **Blutmond-Nacht** | Nachts (Weltzeit 13000–23000): BossBar, Ambient-Sound, Seelen-Partikel, Geister (Vex) | `hauntedNight` |
| **Nebel** | Kurzer Darkness-Puls während der Blutmond-Nacht | `fog` |
| **Kürbisregen** | Manuell per `/pumpkinrain start` | `pumpkinRain` |
| **Kopfloser Reiter** | Nachts zufällig in der Nähe eines Spielers oder als Anführer einer Überfall-Welle; Skelettpferd + Witherskelett teilen sich eine HP-Leiste | `halloweenBoss` |
| **Fledermausschwärme** | Nachts zufällig um Spieler | `batSwarm` |
| **Jumpscares** | Blockabbau im Dunkeln | `jumpScare` |
| **Halloween-Mobs** | Natürlich spawnende Zombies/Skelette tragen Kürbisköpfe, Creeper droppen Kürbisse | `halloweenMobs` |
| **Untoten-Überfall** | Per `/spookyraid start` oder eigenem Kalenderfenster: Wellen von Untoten laufen auf ein Ziel zu, das verteidigt werden muss | `raid` |
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
| `/spookyraid start` | `spooky.admin` | Startet den Überfall sofort — unabhängig von Season und Kalenderfenster. |
| `/spookyraid stop` | `spooky.admin` | Bricht einen laufenden Überfall ab: kein Sieg, keine Niederlage, keine Belohnung. |
| `/spookyraid status` | `spooky.admin` | Zustand, Welle, lebende Angreifer, Zustand des Ziels, Anzahl Verteidiger. |
| `/spookyraid target info` | `spooky.admin` | Zeigt Modus, Objekt-Position und Region des Ziels. |
| `/spookyraid target mode <objective\|region>` | `spooky.admin` | Schaltet zwischen den beiden Ziel-Arten um. |
| `/spookyraid target set` | `spooky.admin`, nur ingame | Setzt die Objekt-Position auf den eigenen Standort. |
| `/spookyraid target region <Name>` | `spooky.admin`, nur ingame | Setzt die Ziel-Region aus WorldGuard (muss in der eigenen Welt existieren). |
| `/spookyraid zone pos1` \| `pos2` | `spooky.admin`, nur ingame | Steckt die beiden Ecken einer eigenen Zone ab. |
| `/spookyraid zone save <Name>` | `spooky.admin`, nur ingame | Speichert die Zone und wählt sie als Ziel aus. |
| `/spookyraid zone list` \| `remove <Name>` | `spooky.admin` | Zeigt bzw. löscht eigene Zonen. |

## Rechte

| Node | Standard | Deckt ab |
| --- | --- | --- |
| `spooky.admin` | `op` | Alle Admin-Subcommands von `/spooky` sowie `/spookyboss` und `/spookyraid` komplett |
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

### Der Untoten-Überfall

Der Überfall hängt **nicht** an `activeWindow` und nicht an `active`. Er startet ausschließlich
über `/spookyraid start` oder über sein eigenes Fenster unter `raid.schedule` — das ist Absicht,
damit er sich außerhalb des Oktobers testen lässt, ohne die Season-Einstellungen zu verbiegen.
`enabledWorlds` gilt aber weiterhin: Steht die Zielwelt nicht drin, verweigert der Start.

Zwei Ziel-Arten, umschaltbar über `raid.target.mode`:

| Modus | Ziel | Niederlage, wenn … |
| --- | --- | --- |
| `objective` | Ein oder **mehrere** Punkte mit eigenen Lebenspunkten. Die Lebenspunkte führt das Plugin selbst, sie sind daher frei wählbar und **nicht** auf 1024 gedeckelt. Angreifer richten Schaden über Nähe an (`reach`), nicht über echte Treffer. | je nach `lose`: alle / ein beliebiges / eine bestimmte Anzahl Ziele gefallen sind |
| `zone` | Ein Quader, den du **selbst im Spiel absteckst** — kein Fremdplugin nötig. Jeder Angreifer, der hineinkommt, zählt als Durchbruch und verschwindet. | `breachLimit` Durchbrüche erreicht sind |
| `region` | Wie `zone`, aber die Fläche kommt aus einer benannten **WorldGuard**-Region — für Server, die ihre Bereiche ohnehin dort pflegen. | `breachLimit` Durchbrüche erreicht sind |

**Eigene Zonen** steckst du im Spiel ab, genau wie die Arenen in SiteZero:

```
/spookyraid zone pos1          an der einen Ecke
/spookyraid zone pos2          an der gegenüberliegenden
/spookyraid zone save dorf     speichert und wählt sie direkt aus
/spooky reload
```

`/spookyraid zone list` zeigt alle, `remove <name>` löscht eine. Sie landen in
`raid.target.zones` als `welt,x1,y1,z1,x2,y2,z2` und lassen sich dort auch von Hand pflegen.

**Wichtig:** Der Spawn-Ring muss **außerhalb** der Zone liegen. Sonst erscheinen die Angreifer
mittendrin, zählen sofort als Durchbruch und der Überfall ist in Sekunden verloren, ohne dass ein
einziger Mob gelaufen ist. Das Plugin warnt beim Start mit der nötigen Mindestentfernung.

Der `region`-Modus braucht WorldGuard **mit funktionierender Regions-Abfrage**; fehlt sie, meldet
der Start das sauber zurück, statt still nichts zu tun.

**Mehrere Ziele** kommen in `raid.target.objective.points` — je Zeile `welt,x,y,z` oder
`welt,x,y,z,lebenspunkte`, alle in derselben Welt. Solange die Liste leer ist, gilt der Einzelpunkt
aus `world/x/y/z`. Am bequemsten ingame:

| Befehl | Wirkung |
| --- | --- |
| `/spookyraid target add` | Fügt die eigene Position als weiteres Ziel hinzu |
| `/spookyraid target list` | Listet alle Ziele mit Nummer |
| `/spookyraid target remove <Nr>` | Entfernt eines |
| `/spookyraid target clear` | Leert die Liste, es gilt wieder der Einzelpunkt |

Über `lose` legst du fest, wann der Überfall verloren ist: `all` (Standard, erst wenn jedes Ziel
gefallen ist), `any` (schon beim ersten) oder eine Zahl gefallener Ziele.

**Warum `style: display` der Standard ist:** Ein Mob als Ziel hat eine Trefferbox — beim Eisengolem
1,4 × 2,7 Blöcke. Jeder Schwertschlag, der sie streift, landet auf dem Ziel, wird abgebrochen und
ist verbraucht. Kleine Angreifer davor, allen voran Babyzombies, sind dann kaum zu treffen. Ein
Display-Objekt hat überhaupt keine Trefferbox und dieses Problem damit nicht. Der Mob-Stil bleibt
über `style: entity` erhalten.

Ablauf: Ansage mit Countdown → Welle für Welle Angreifer, die auf das Ziel zulaufen → zwischen
den Wellen eine Atempause. Eine Welle umfasst `baseMobs + (Welle − 1) × mobsPerWave` Angreifer,
gleichzeitig aber nie mehr als `maxAlive`. Den Kopflosen Reiter als Anführer einer Welle steuert
`raid.waves.leaderEnabled` / `leaderWave` (`0` = letzte Welle); steht ohnehin schon ein Boss in
der Welt, wird der übernommen, statt einen zweiten zu setzen.

### Der Anführer

Den Anführer einer Welle stellt der vorhandene Kopflose Reiter (`raid.waves.leaderEnabled`,
`leaderWave`, `0` = letzte Welle). Er ist der **einzige** Angreifer mit eigener Leiste — alle
anderen bekommen ihre Vanilla-Bossleiste über `raid.waves.hideBossBars` abgeschaltet.

Seine Lebenspunkte führt das Plugin selbst, sie sind daher **nicht** auf 1024 gedeckelt. Technisch
hält die Entity immer nur einen Abschnitt davon: Ein Treffer, der sie töten würde, wird abgefangen
und die sichtbare Gesundheit wieder aufgefüllt, solange der Pool Reserven hat — Trefferrückmeldung,
Rückstoß und die geteilte Leiste von Pferd und Reiter bleiben unverändert. Die Bossleiste zeigt
immer den **Pool**, nicht die Entity.

Alles an ihm ist einzeln einstellbar, in der `config.yml` unter `halloweenBoss` oder live:

```
/spookyboss info                       # inklusive aktuellem Pool-Stand
/spookyboss set health 1800
/spookyboss armor chestplate NETHERITE_CHESTPLATE
/spookyboss ability chargeIntervalSeconds 10
/spookyboss loot add NETHERITE_INGOT:2
```

Die mitgelieferten 100 HP stammen aus der Zeit, als er nur zufällig nachts bei einem einzelnen
Spieler auftauchte. Für einen Wellen-Anführer vor einem vollen Server sind 900–3000 ein
brauchbarer Startbereich.

Bei **Sieg** laufen `raid.rewards.victory`, bei **Niederlage** `raid.rewards.consolation` — je
Verteidiger, also für jeden, der während des Überfalls im Umkreis von `participationRadius` war
oder einen Angreifer erlegt hat. Nach einer Niederlage bleibt der Ort für
`raid.defeat.effect.durationSeconds` sichtbar gezeichnet (Partikel und Dunkelheit, **keine**
Blockveränderung); solange lässt sich kein neuer Überfall starten — `/spookyraid stop` beendet
den Nachlauf sofort.

**Wie der Angriff funktioniert.** Zwei getrennte Wege, das erklärt die meisten Stellschrauben:

| | gegen das Ziel | gegen Spieler |
| --- | --- | --- |
| Mechanik | Nähe: wer innerhalb von `reach` steht, schlägt einmal pro Sekunde zu | normale Vanilla-Mob-KI, echter Nahkampf |
| Schaden | `targetDamage`, roh — keine Rüstung, keine Resistenz | `damage`, als Angriffs-Attribut gesetzt, Rüstung dämpft es |
| Animation | Schwung wird vom Plugin ausgelöst | Vanilla |

`targetDamage: -1` heißt „wie `damage`". Getrennte Werte sind fast immer besser: Der Spielerschaden
muss überlebbar bleiben, der Zielschaden nicht — mit einem einzigen Regler wird eines von beidem
zwangsläufig falsch.

**Worauf die Angreifer sich stürzen**, steuert `focus`:

- `target` (Standard) — das Ziel hat Vorrang. Ein Verteidiger wird nur bekämpft, solange er
  innerhalb von `playerAggroRange` steht; danach bricht der Angreifer ab und läuft weiter. Ohne
  das rennt eine ganze Welle einem einzelnen Spieler quer über die Karte hinterher und das Ziel
  bleibt unberührt. **Alles andere, was ein Angreifer von sich aus anvisiert, wird verworfen** —
  Wither greifen sonst jedes Nicht-Untote an, und ein einziges Huhn parkt einen von ihnen für den
  Rest der Welle 25 Blöcke neben dem Ziel.
- `players` — reines Vanilla-Verhalten, Spieler zuerst, egal wie weit.

Angegriffen wird in beiden Fällen **beides**: Spieler über die Mob-KI, das Ziel über die Nähe.

**Wer angreift, steht in `raid.waves.mobs`.** Jeder Eintrag ist ein Archetyp; mehrere Einträge
dürfen denselben Entity-Typ benutzen — so werden aus einem Zombie ein gewöhnlicher und ein
gepanzerter Angreifer:

| Feld | Bedeutung |
| --- | --- |
| `type` | Entity-Typ (Pflicht) |
| `id` | Name für die `maxPerWave`-Buchführung, Standard: der Typ |
| `weight` | relative Häufigkeit unter allem, was in der Welle erlaubt ist |
| `fromWave` / `untilWave` | ab welcher und bis zu welcher Welle er auftauchen darf (`0` = kein Ende) |
| `maxPerWave` | höchstens so viele pro Welle (`0` = unbegrenzt) |
| `health`, `damage`, `targetDamage`, `speed` | überschreiben die wellenweiten Werte; weglassen = erben |
| `scale` | Größenfaktor, braucht MC 1.20.5+ |
| `name` | überschreibt `waves.names` für diesen Archetyp |
| `minPerWave` | mindestens so viele pro Welle, werden vor der Zufallsziehung gesetzt (`0` = reiner Zufall) |
| `ranged` | schießt statt heranzulaufen; ohne Angabe automatisch bei Bogen/Armbrust |
| `spawnRadiusMin` / `spawnRadiusMax` | eigener Spawn-Ring für diesen Archetyp statt des wellenweiten |
| `equipment` | `weapon`, `offhand`, `helmet`, `chestplate`, `leggings`, `boots` |

**Ausrüstung wird auch benutzt** — ein Zombie mit `IRON_SWORD` schlägt damit zu, und ein Schütze
beschießt das Ziel aus bis zu `raid.waves.rangedReach` Blöcken Entfernung, statt heranzulaufen.
Das braucht Plugin-Hilfe: Das Ziel hat keine Trefferbox, ein echter Pfeil könnte es nie treffen —
der Schuss ist Optik, den Schaden bucht das Plugin. Drop-Chancen stehen immer auf null. Achtung:
Ein Archetyp mit eigenem Helm bekommt keinen Kürbis mehr und verbrennt dann bei Tag.

**Wither reisen schlecht.** Sie bewegen sich über ihre eigene Flugsteuerung, nicht über
Wegfindung — auf offener Strecke bleiben sie zuverlässig irgendwo hängen. Statt das zu bekämpfen,
lässt man sie mit `spawnRadiusMin: 0` und `spawnRadiusMax: 5` **direkt am Ziel** erscheinen; die
Anreise entfällt damit ganz. Gemessen: Abstand durchgehend unter 5 Blöcken statt 25 bis 70.

**Enderdrachen taugen nicht als Angreifer.** Sie lassen sich zwar als Archetyp eintragen und
spawnen auch (Größe und Lebenspunkte greifen), bewegen sich dann aber nicht: `EnderDragon` ist
kein `Mob`, hat also keine Wegfindung, und seine Vanilla-KI hängt am End-Podest. Gemessen: zwei
Drachen standen 36 Sekunden lang auf derselben Koordinate. Wer etwas Fliegendes will, nimmt
Phantome; wer einen dritten Bosstyp will, den verkleinerten Wither.

**`minPerWave` ist bei seltenen Archetypen wichtig.** Mit gut zwanzig Einträgen bedeutet
`weight: 1`, dass ein Mob den ganzen Überfall über nicht auftaucht — gemessen: fünf Archetypen
kamen über zehn Wellen kein einziges Mal. Eine Mindestzahl garantiert sie. Übersteigt die Summe
aller Mindestzahlen die Angreiferzahl einer Welle, passen nicht alle hinein; das Plugin warnt beim
Start mit der betroffenen Wellennummer.

Welcher Archetyp spawnt, wird **jedes Mal neu ausgewürfelt**, gewichtet nach `weight` unter allem,
was für die laufende Welle freigeschaltet ist. Die Wellenzahl steuert `raid.waves.count`.

**Spezial-Mobs** sind damit reine Konfiguration. Der mitgelieferte Vorschlag enthält einen
verstärkten Zombie ab Welle 3, einen Bogenschützen ab Welle 4 und bis zu drei verkleinerte Wither
ab Welle 8:

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

Damit ein Wither das Dorf nicht in einen Krater verwandelt, sorgt `raid.waves.noGriefing`
(Standard an) dafür, dass **kein Angreifer Blöcke zerstört** — Explosionen verletzen weiterhin
Spieler, nur der Blockschaden entfällt. Das gilt auch für Wither-Schädel und Creeper.

**Eigene Namen für die Angreifer** stehen in `raid.waves.names` — `default` gilt für alles ohne
eigenen Eintrag, ein leerer Text heißt „kein Name" (Standard). Farbcodes mit `§` funktionieren,
`{wave}` wird durch die Wellennummer ersetzt:

```yaml
    nameVisible: true
    names:
      default: "§7Auferstandener"
      ZOMBIE: "§2Verwester (Welle {wave})"
      WITHER_SKELETON: "§8Schwarzer Knochen"
```

`nameVisible` steuert nur die Schrift über den Köpfen; sie ist bewusst aus, weil bei einer großen
Welle ein Wald aus Namensschildern mehr stört als trägt. Der Name wirkt auch unsichtbar — er taucht
in den Todesmeldungen auf.

Drei Fallen:

- **Schwierigkeit `peaceful`**: Der Server lehnt dort jeden Monster-Spawn ab. Der Start verweigert
  deshalb mit einer klaren Meldung, statt eine Welle ins Leere laufen zu lassen.
- **`respectRegionProtection`** (Standard `true`) prüft jeden einzelnen Spawnpunkt gegen
  WorldGuard/GriefPrevention. Liegt der Ort des Events selbst in einer geschützten Region, kommt
  damit nichts durch — der Überfall meldet das im Log und läuft mit weniger Angreifern weiter.
  Dann entweder den Spawn-Ring nach außen legen (`spawnRadiusMin`/`spawnRadiusMax`) oder diesen
  Schalter für die Event-Welt abschalten.
- **`raid.waves.dropLoot`** ist bewusst `false`. Sonst ist der Überfall eine Mob-Farm.
- **Für ein Großevent zuerst `maxAlive` anheben.** Das ist der eigentliche Begrenzer dafür, wie
  viel Druck gleichzeitig ankommt — `baseMobs` allein bringt nichts, wenn die Obergrenze bei 40
  steht. Richtwerte stehen als Kommentar in der config.
- **Nach einer Niederlage endet der Überfall von selbst.** Angreifer werden aufgeräumt, die
  Trostbelohnung geht raus, der Ort bleibt `raid.defeat.effect.durationSeconds` lang gezeichnet
  (Standard 20 s), danach steht alles wieder auf IDLE und ein neuer Überfall lässt sich starten.
  `raid.defeat.effect.enabled: false` beendet sofort mit dem Fall des letzten Ziels.
- **Angreifer verletzen einander nicht** (`raid.waves.friendlyFire`, Standard aus). Ein
  verirrter Pfeil genügt sonst: Skelett trifft Zombie, Zombie dreht sich um und tötet das
  Skelett, und die Welle dünnt sich aus, bevor sie das Ziel erreicht. Blockiert wird beides —
  der Treffer selbst **und** die Folgewirkung (Wither-, Gift-, Magieschaden), denn die kommt
  später ohne Verursacher an. Gemessen über 75 Sekunden mit vier Withern in der Welle: 1 Verlust
  mit dem Schutz, 7 ohne.
- **Angreifer, die nicht vorankommen, bekommen einen Schub** (`unstickSpeed`). Eine Wegvorgabe
  bewegt nicht alles: Ein Wither fliegt über seine eigene Flugsteuerung und schwebt sonst einfach,
  Bodenmobs verhaken sich im Gelände. Gemessen wird schlicht, ob der Abstand zum Ziel kleiner wird.
- **Spawnpunkte liegen auf dem Boden, nicht in Baumkronen.** `getHighestBlockYAt` liefert über
  einem Wald die Blattkrone; gesucht wird deshalb von dort abwärts der erste feste Block, der
  weder Laub noch Stamm ist und zwei Blöcke Luft über sich hat. Das schließt auch Spawns im
  Wasser und unter Überhängen aus. Gesucht wird dabei nur wenige Blöcke unter der Oberfläche —
  wer bis ganz nach unten sucht, landet in der ersten Höhle, und ein Angreifer darin steckt den
  ganzen Überfall dort fest. Findet sich in einer Spalte nichts, gilt der Versuch als
  fehlgeschlagen und es wird woanders gewürfelt.
- **Wither und Drachen bringen ihre eigene Vanilla-Bossleiste mit.** `raid.waves.hideBossBars`
  (Standard an) blendet sie aus — bei drei Withern in einer Welle füllen sie sonst den Bildschirm
  und drängen die beiden Leisten des Überfalls weg. Der Wellen-Anführer behält seine Leiste, die
  zeichnet das Plugin selbst.
- **`raid.debug: true`** protokolliert je Angreifer Position, Entfernung zum Ziel, tatsächliches
  Angriffsziel und ob er einen Weg hat. Sehr geschwätzig, aber genau das Werkzeug für
  „die Angreifer kommen nicht an".

Ein `/spooky reload` bricht einen laufenden Überfall **nicht** ab: Er arbeitet mit dem
Konfigurations-Schnappschuss weiter, der beim Start gezogen wurde. `/spooky off` bricht ihn ab.

### Dateien im Plugin-Ordner

| Datei | Inhalt |
| --- | --- |
| `config.yml` | Konfiguration |
| `lang/en.yml`, `lang/de.yml` | Meldungen; eigene Sprachen hier ablegen und in `language` eintragen |
| `player-prefs.yml` | Lautstärken und Opt-out je Spieler |
| `player-stats.yml` | Gesammelte Treats je Spieler |
| `pending-rewards.yml` | Rewards für Spieler, die beim Season-Ende offline waren |
| `reward-state.yml` | Season-Zustand und Datum der letzten Verteilung |
| `raid-state.yml` | Datum des letzten automatisch gestarteten Überfalls |

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

### 1.4.0

Neu: der **Untoten-Überfall** — Wellen von Untoten laufen auf ein zu verteidigendes Ziel zu.
Rein additiv, es wurde kein bestehendes Feature verändert oder entfernt.

- **Wellen-System** (`raid.waves`): Ansage mit Countdown, Welle für Welle, Pause dazwischen,
  Obergrenze gleichzeitig lebender Angreifer, gewichtete Zusammenstellung der Mob-Typen,
  Lebens- und Schadenszuwachs je Welle. Angreifer tragen Kürbisköpfe und verbrennen damit nicht,
  wenn der Überfall in den Tag hineinläuft.
- **Zwei Ziel-Arten** (`raid.target.mode`): ein Objekt mit eigenen Lebenspunkten an fester
  Position, oder eine benannte WorldGuard-Region mit Durchbruchszähler. Das Objekt nimmt nur
  Schaden von den Angreifern des Überfalls.
- **Anführer:** Der vorhandene Kopflose Reiter führt eine konfigurierbare Welle an — gespawnt
  über den bestehenden `HalloweenBossManager`, ohne dessen Verhalten zu ändern.
- **Eigenes Kalenderfenster** (`raid.schedule`), unabhängig von `activeWindow`: Monat, Tage,
  Startzeit und Toleranzfenster, höchstens ein automatischer Start pro Tag (`raid-state.yml`).
- **Zwei BossBars:** Wellenfortschritt und Zustand des Ziels, beide nur im Umkreis und nur als
  Differenz geschickt.
- **Belohnungen** für Sieg und Niederlage getrennt (`raid.rewards`), je Verteidiger; nach einer
  Niederlage bleibt der Ort eine Weile sichtbar gezeichnet (`raid.defeat.effect`) — Partikel und
  Dunkelheit, keine Blockveränderung.
- **Folia:** Zustandsautomat im Global-Scheduler ohne direkten Entity-/Block-Zugriff, Spawns über
  den Region-Scheduler des Zielortes, jede Arbeit an einem Angreifer über dessen Entity-Scheduler,
  Task-Abbruch nur über `Scheduler.TaskHandle`. Kein `Bukkit.getCurrentTick()`.
- **Sicherheitsnetze:** `enabledWorlds` gilt auch für den Überfall; jeder Spawnpunkt wird gegen
  WorldGuard/GriefPrevention geprüft (abschaltbar); eine Welle, die nicht fertig werden kann,
  endet nach `timeoutSeconds`; Angreifer und Ziel sind vom allgemeinen Entity-Cleanup ausgenommen,
  solange der Überfall läuft.
- `RegionIntegration` kann jetzt zusätzlich **benannte Regionen** abfragen (Mittelpunkt,
  Existenz, Enthaltensein). Schlägt nur dieser Teil fehl, bleibt die bisherige Spawn-Prüfung
  unberührt.

Weiter im Verlauf der Live-Tests dazugekommen:

- **Mehrere Ziele gleichzeitig** (`points`), Verlustbedingung `all` / `any` / Anzahl, je Ziel eine
  eigene schwebende Lebenspunkte-Anzeige.
- **Archetypen statt flacher Mob-Liste** (`raid.waves.mobs`): Ausrüstung, Größe (`scale`), eigene
  Werte, Wellen-Fenster (`fromWave`/`untilWave`), Ober- und Untergrenzen je Welle. Die alte
  `composition`-Liste greift weiterhin, solange `mobs` leer ist.
- **Echter Fernkampf:** Wer Bogen oder Armbrust trägt, beschießt das Ziel aus `rangedReach`
  Blöcken Entfernung, statt heranzulaufen.
- **Getrennte Schadenswerte** gegen Spieler (`damage`, wird von Rüstung gedämpft) und gegen das
  Ziel (`targetDamage`, roh), dazu `focus`/`playerAggroRange` für die Zielwahl.
- **Der Boss hat einen eigenen HP-Pool** und ist damit nicht mehr auf 1024 gedeckelt;
  `/spookyboss info` zeigt den aktuellen Stand.
- **Kein Eigenbeschuss mehr** (`friendlyFire`, Standard aus) — inklusive der Folgewirkung von
  Wither- und Giftschaden, die ohne Verursacher ankommt.
- **Keine Bossleisten-Flut:** Die Vanilla-Leiste von Withern und Drachen wird ausgeblendet
  (`hideBossBars`), nur der Wellen-Anführer behält seine.
- **Angreifer spawnen auf dem Boden**, nicht in Baumkronen oder im Wasser.
- **Namen je Mob-Typ** (`raid.waves.names`), Babyzombies abschaltbar (`babies`).
- `raid.debug` als Diagnosewerkzeug für „die Angreifer kommen nicht an".

Im Live-Test auf Folia 26.2 und auf einem echten Server gefunden und mitbehoben:

- **Das Ziel schluckte Schwertschläge.** Als Mob hatte es eine Trefferbox; Hiebe auf Angreifer
  davor landeten auf dem Ziel, wurden abgebrochen und waren verbraucht — Babyzombies direkt davor
  ließen sich praktisch nicht treffen. Das Ziel ist jetzt standardmäßig ein Display-Objekt ganz
  ohne Trefferbox.
- **Die Lebenspunkte des Ziels waren bei 1024 gedeckelt**, weil sie am Vanilla-Attribut
  `max_health` hingen. Ein konfiguriertes `health: 1200` wurde stillschweigend zu 1024 — für ein
  Event mit vielen Spielern zu wenig. Das Plugin führt die Lebenspunkte jetzt selbst.
- **Die Angreifer liefen nicht zum Ziel.** Nach dem Umbau gab es nichts mehr zum Anvisieren, und
  eine gesetzte Wegvorgabe wurde von den eigenen Herumlauf-Zielen des Mobs überschrieben. Gemessen
  per `raid.debug`: `actualTarget=none`, Entfernung über 84 Sekunden unverändert. Ein
  untergeschobenes Hilfsziel half nicht — die Mob-KI verwirft ein `setTarget` auf etwas, das sie
  von sich aus nie angreifen würde, noch im selben Tick. Gelöst über die Wegvorgabe, die jetzt
  auch dann erneuert wird, wenn ein Anvisier-Versuch nicht greift.
- **Babyzombies abschaltbar** (`raid.waves.babies`), weil sie klein, schnell und im Getümmel vor
  dem Ziel kaum zu treffen sind.

Davor bereits gefunden:

- **Start auf Schwierigkeit `peaceful`** lief sichtbar ins Leere: Jeder Monster-Spawn wird vom
  Server abgelehnt, der Überfall spawnte nichts und endete nach dem Wellen-Timeout als „Sieg".
  Der Start verweigert jetzt vorher mit eigener Meldung.
- **Verwaistes Skelettpferd beim Boss-Spawn:** Schlug der Spawn des Reiters fehl (z.B. genau auf
  `peaceful`), blieb das bereits gespawnte Pferd als markierte, ungetrackte Waise stehen.
  `spawnBoss()` räumt es jetzt auf. Betrifft auch `/spookyboss spawn`, nicht nur den Überfall.
- **Log-Flut:** Eine Warnung *pro* fehlgeschlagenem Spawn-Versuch (Dutzende Zeilen je Welle).
  Jetzt einmal pro Welle plus die bestehende Zusammenfassung.

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
mvn package        # → target/spookyseason-1.4.0.jar
```

Benötigt **JDK 25**. Der Bytecode selbst zielt auf Java 21 (`maven.compiler.release`), aber die
`paper-api` für MC 26.1.2 liegt als Class-File-Version 69 (Java 25) vor — mit einem JDK 21
scheitert schon das Einlesen der Abhängigkeit („Kein Zugriff auf org.bukkit.Bukkit").
