# Module map

One Gradle module per cell where the cell builds code. Exact public signatures live in each cell's contract; this page records ownership, direction of dependency and the role of each interface. Where this page and a cell contract differ, the contract is the truth.

## Modules and owners

| Cell | Module | Interface it exposes |
|---|---|---|
| C-003 | core-model | Shared data types (profile, interest card, context, tier, identifiers) used by every other module |
| C-004 | core-crypto | Key generation per context, signing, key agreement, authenticated encryption, wrapping over audited libraries |
| C-005 | core-psi | Private set intersection over interest cards, from a maintained library |
| C-006 | storage | Encrypted local database, Keystore key plus PIN wrapping, wipe |
| C-007 | transport-ble | BLE advertising and scanning, GATT exchange of small payloads |
| C-008 | disclosure | Mutual, stepwise tier state machine for interest, identity and photos |
| C-009 | familiar | Familiar-stranger handling (see open problem 8; unspecified in spec v1.0) |
| C-010 | security | Decoy mode, panic wipe, build fingerprint check |
| C-011 | invites | Invite-only install tokens (see open problem 10) |
| C-012 | chat | End-to-end encrypted, forward-secret messaging |
| C-013 to C-015 | app | UI and wiring of all modules into the Android application |

## Work cells that are not library modules

| Cell | Purpose |
|---|---|
| C-016 | pilot |
| C-017 | integration |
| C-018 | release |
| C-019 | audit preparation |

## Dependency direction

- core-model is the base. It depends on no other project module.
- No module depends on app.
- app depends on all other modules.
- Other edges between feature modules are defined by each cell's contract; a module consumes another through that contract (against a mock where the contract says so), never through its implementation.

```
app -> { core-crypto, core-psi, storage, transport-ble, disclosure,
         familiar, security, invites, chat }
every module -> core-model
```
