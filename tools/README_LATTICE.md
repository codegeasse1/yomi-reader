# Lattice Resonance (Резонанс Каркаса)

Hidden mythic easter egg: carrier long-press → hex routing board → encrypted rewards.

## Security model

| Layer | Public in repo/APK | Private (gitignored) |
|-------|--------------------|----------------------|
| Board layout at start | AES-GCM ciphertext (`STAGE_A`) | `lattice_scenario.local.json` cells + solutions |
| Topology solution | only as Stage B key after solve | `solution` rotations in scenario |
| Rewards payload | AES-GCM (`STAGE_B`) | scenario `payload` |
| Stage A open-key | needs latch counts + `FRAME` | scenario `pepper` (source of `FRAME`) |
| Latch thresholds | `THRESHOLDS` int array (generated) | scenario `carriers` |

**Honest residual:** a reverse engineer who extracts `FRAME` + `THRESHOLDS` from `LatticeVaultData` can open Stage A without playing. Board/rewards remain ciphertext; Stage B still requires the unique closed topology (or ~140k×180k PBKDF2 offline).

## Forge

```bash
# scenario must stay OUT of git (see .gitignore: lattice_scenario*.json)
node tools/lattice_forge.mjs lattice_scenario.local.json /tmp/lattice_out \
  app/src/main/java/eu/kanade/domain/easteregg/lattice/LatticeVaultData.kt
node tools/lattice_verify.mjs lattice_scenario.local.json /tmp/lattice_out/lattice_vault.json
rm -rf /tmp/lattice_out
```

Scenario schema:

```jsonc
{
  "pepper": "min-8-char-random",
  "carriers": { "a": 1, "m": 2, "n": 3 },
  "board": { "radius": 2, "port": { "q": 2, "r": 0, "dir": 0 }, "cells": [/* ... */] },
  "payload": {
    "achievementId": "lattice_resonance",
    "bonusPoints": 150,
    "themeId": "LATTICE_PROTOCOL",
    "unlockables": ["theme_LATTICE_PROTOCOL", "special_navbar_lattice_circuit"]
  }
}
```

Changing pepper/carriers/board regenerates `VERSION` → soft-migrates in-progress carrier/topology prefs.

## Runtime

- Domain: `eu.kanade.domain.easteregg.lattice.*`
- UI: `eu.kanade.presentation.easteregg.lattice.*`
- Carriers: player / manga reader bar / novel reader
- Debug (DEBUG builds only): More → Force Lattice breach / Reset Lattice Resonance

## Tests

```bash
./gradlew :app:testDebugUnitTest --tests "eu.kanade.domain.easteregg.lattice.*" --no-daemon
```
