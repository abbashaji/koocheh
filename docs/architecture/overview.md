# Koocheh architecture overview

Status: spec v1.0, pre-pilot. Claims marked [UNVERIFIED] have not been checked; see `unverified-register.md`.

## Summary

Koocheh is a native Kotlin Android app for meeting people nearby without any central party. Phones discover each other over Bluetooth Low Energy, keep the profile and all data locally, exchange interest cards, and match only when both sides opt in. Identity, interest and photos are disclosed in mutual, stepwise tiers, and chat is end-to-end encrypted. There is no server, no account, no analytics and no cloud backup. Install is by invitation, passed phone to phone. The design aims to reduce what can be learned or seized, and it does not claim to remove risk: legal and social consequences remain with the user (see `threat-model.md`).

## Design principles (priority order)

1. Safety and deniability beat every other goal.
2. No central party: no server, no analytics, no accounts, no cloud backup.
3. Reveal nothing one-sidedly: interest, identity and photos are disclosed only in mutual, stepwise exchanges.
4. Honest by construction: no fake activity counts, invented urgency or teaser notifications.
5. Density over scale.
6. Say what is unverified.

When two principles conflict, the lower number wins.

## Personas

| Persona | Context |
|---|---|
| Student or maker (study group, band, project team) | Work |
| Founder or developer (looking for a co-founder) | Work |
| Newcomer or hobbyist (looking for friends) | Social |
| Someone seeking a relationship | Social, opt-in |

## Scope by version

| Version | Contents |
|---|---|
| v1 | Android only; BLE discovery; local profile; interest cards; mutual-opt-in matching; tiered disclosure; E2E encrypted chat; contexts Work and Social; invite-only install; decoy mode; panic wipe; encrypted local storage; phone-to-phone APK sharing |
| v1.5 (only if pilot gates pass) | Wi-Fi Aware/Direct transfer; store-and-forward gossip (200 cards, hop limit 4, expiry 72 h); offline vouching |
| Later | UWB confirmation; external LoRa text relay; iOS (limited background Bluetooth and distribution constraints [UNVERIFIED]) |

## Architecture

| Aspect | Decision |
|---|---|
| Platform | Native Kotlin, Android; minimum API level set after a hardware survey |
| Storage | Encrypted local database; key held in Android Keystore and additionally wrapped by a user PIN |
| Identity | Per-context keypairs generated on the device; no global identity |
| Transport | BLE advertising for discovery; GATT for small exchanges; Wi-Fi Aware/Direct for bulk transfer (v1.5) |
| Crypto | Audited libraries only (for example libsodium); private set intersection (PSI) from a maintained library; forward-secret chat |
| Backend | None |
| Telemetry | None; crash reports are manual and user-initiated |
