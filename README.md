# SpookySeason

Halloween-Event-Plugin für Paper/Folia **1.21.x und 26.x** — Trick-or-Treat, Kürbisregen, Blutmond-Nächte, Kopfloser-Reiter-Boss, Fledermausschwärme, Jumpscares und Season-End-Rewards.

Eine Jar deckt beide Versionslinien ab: kompiliert gegen die Paper-26.1.2-API, `api-version: '1.21'` als Minimum. Die mit MC 1.21.3 umbenannten Attribute (`GENERIC_MAX_HEALTH` → `MAX_HEALTH`) werden zur Laufzeit über die Registry aufgelöst (`util/Attributes`, mit Fallback auf die Legacy-Keys) statt über Enum-Konstanten — verifiziert auf Paper 26.1.2, Paper 1.21.1 und Folia 1.21.4.

## Historie

⚠️ Der Original-Quellcode (bis v1.1.0) ging verloren — weder lokal, noch in den Backups (D1-P1 / T7), noch auf GitHub vorhanden. Dieses Projekt wurde am 2026-07-01 aus der gebauten `spookyseason-1.1.0.jar` decompiliert (CFR 0.152) und am 2026-07-02 als Maven-Projekt rekonstruiert, bereinigt und auf **v1.2.0** gefixt.

- `jars/` — die geretteten Original-Builds (`0.0.1` … `1.1.0`). Einziger verlässlicher Originalstand.
- `src-decompiled/` — das unveränderte CFR-Decompilat von `1.1.0` (Referenz).
- `src/main/` — die lauffähige, gefixte Quellbasis (v1.2.0). Die eingebettete bStats-Kopie wurde durch die echte `org.bstats:bstats-bukkit`-Dependency mit Shade-Relocation ersetzt.

## Wichtigste Fixes in 1.2.0 gegenüber 1.1.0

- **Sprachdateien:** doppelter `command:`-Block entfernt — Spieler sahen vorher rohe Keys statt Meldungen.
- **RegionIntegration:** WorldGuard-/GriefPrevention-Checks waren wegen falscher Reflection-Signaturen komplett wirkungslos (stiller No-Op); jetzt korrekt via `BukkitAdapter.adapt()` bzw. `Claim`-Typ, mit Warnung statt stillem Durchwinken.
- **Boss:** invertierter Season-Check beim Despawn; verwaiste Boss-Mobs nach Chunk-Entladung werden jetzt per `EntitiesLoadEvent`-Cleanup entfernt; BossBar-Leak bei Weltwechsel; Respawn-Cooldown konfigurierbar (`halloweenBoss.respawnCooldownSeconds`).
- **Exploits:** Reward-Dupe per Relog geschlossen; Treat-Stats werden nach der Verteilung zurückgesetzt; globaler Trick-or-Treat-Cooldown (`trickOrTreat.globalCooldownSeconds`) gegen Tür-Farming; Hopper können Kürbisregen-Items nicht mehr einsammeln.
- **GUI:** `InventoryDragEvent` wird gecancelt (kein Item-Verlust mehr), Erkennung über `InventoryHolder` statt Titel-String.
- **Folia:** Player-Daten-Stores thread-sicher mit asynchronem, entprelltem Speichern; geteilte Entity-Listen als `CopyOnWriteArrayList`; Spawns nutzen die geplante Region-Location; `onDisable` bricht nicht mehr mitten im Cleanup ab; Scheduler cached Reflection-Lookups.
- **Kleinkram:** Jumpscare nutzt Gesamtlicht statt nur Blocklicht; Villager-Trick-or-Treat respektiert `enabledWorlds`; Off-Hand-Doppeltrigger gefiltert; NaN/Infinity-Validierung in `/spookyboss set`; volle Inventare droppen Treats statt sie zu verschlucken.

## Build

```bash
mvn package        # → target/spookyseason-1.2.0.jar
```

Benötigt Java 21+. API: Paper 26.1.2 (`api-version: '1.21'`, `folia-supported: true`); läuft auf 1.21.x und 26.x.
