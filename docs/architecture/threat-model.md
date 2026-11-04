# Threat model

Spec v1.0, pre-pilot. The residual column is part of the model: mitigations reduce exposure and none of them guarantees or eliminates a risk.

| Threat | Mitigation | Residual risk |
|---|---|---|
| Passive Bluetooth tracking | Rotating identifiers; no stable payload | Timing and RSSI correlation by a motivated observer |
| Phone seized and inspected | Decoy mode, encryption, wipe | Cannot defend against compelled unlock or a forced app reveal |
| Hostile participant | Mutual tiers, vouching, local blocks | Entrapment and infiltration are unsolved |
| Tampered APK | Signed builds, fingerprint check | Users who skip verification |
| Metadata through gossip | Hop limits, delay, batching | Pattern leakage remains |
| Network-level blocking | Not applicable, there is no network dependency | Cannot help if radios are jammed |

## Limits the user must be told

The software cannot remove legal and social consequences, and onboarding must say so.

See `open-problems.md` for the problems behind the residual column and `unverified-register.md` for claims here that rest on unchecked assumptions.
