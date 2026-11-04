# Bluetooth Tracking Test Plan

Status: PLAN ONLY. No test has been run. This document must not be read as a result.

## Goal

Find out whether a passive observer with ordinary hardware can follow one phone over time, across rotations of its identifiers, or link the same person across contexts. Spec section 7 requires this test before any pilot. Existing mesh messaging apps have had flaws that made users trackable [UNVERIFIED on specifics].

## Pass gate

Zero confirmed identifier-tracking failures. A confirmed failure is one that was reproduced at least twice by a person other than the one who found it.

## Limit of what a pass means

Passing shows only the absence of the failures tested for. It does not show the app is untrackable. Untested methods, better equipment, or longer observation may still succeed.

## Setup

- Test devices: at least three phones of different makes and Android versions running the build under test, plus one control phone running no app.
- Observer: placed at several distances and in at least one crowded radio environment, since real conditions are noisier than a lab.
- Observation length: long enough to cover several full rotation cycles. Record the rotation interval from the build config and do not assume it.
- All captures are stored with timestamps, device ids, and the build hash.
- Use only devices and people who have agreed to the test. Do not capture third-party traffic.

## Tool classes

- Class A: an ordinary phone running a BLE scanner app.
- Class B: a laptop or single-board computer with a standard BLE adapter and packet logging.
- Class C: a dedicated BLE sniffer or software-defined radio that sees raw advertising packets, including addresses and channel data.
- Class D: analysis scripts for correlation (statistics or simple machine learning over captured traces).

## Checks

### 1. Advertisement payload stability across rotations
- Method: capture advertisements from one test phone through several ephemeral id rotations. Diff payload bytes before and after each rotation.
- Tool class: B or C, then D.
- Pass: no byte or field outside the ephemeral id stays constant across rotations in a way that is unique to the device or user. Fail: any such constant, or any counter or pattern that lets two payloads be linked.

### 2. Alignment of Bluetooth address rotation with ephemeral id rotation
- Method: record the advertising address and the ephemeral id together. Look for gaps where one changes and the other does not, even briefly.
- Tool class: C (needs raw addresses), then D.
- Pass: both change at the same moment, or the address changes in a way that gives no link to the old one. Fail: an old address appears with a new id, or the reverse, at any point.

### 3. Fixed service UUID as a fingerprint
- Method: scan with a filter for the app's service UUID and with no filter. Check whether the UUID is present in advertisements and in GATT discovery.
- Tool class: A or B.
- Pass: the UUID cannot identify the app to a passive observer beyond what the design has explicitly accepted and recorded, or the design avoids a fixed value. Fail: a fixed UUID that marks a phone as running the app is visible to any scanner and no acceptance is recorded. Note: this check measures app detectability, not user tracking, and the team must decide in writing whether detectability is acceptable.

### 4. Scan response and GATT characteristic constants
- Method: capture scan responses. Connect as an ordinary client where the design allows it and list services, characteristics, descriptors, and default values.
- Tool class: B.
- Pass: no value (name, version, model, counter, static characteristic value) is constant per device or per user across rotations. Fail: any such constant.

### 5. RSSI and timing correlation across a rotation boundary
- Method: with a phone moving or still in view, record signal strength and packet timing. At each rotation, test whether the new identity can be matched to the old one by signal strength continuity, advertising interval, channel order, or the timing of the switch.
- Tool class: C and D.
- Pass: matching accuracy is no better than chance in the test conditions, including a quiet environment with one device. Fail: a method links old and new identities clearly better than chance. Define the chance baseline and the threshold in advance, before looking at the data.

### 6. Work versus Social context unlinkability
- Method: put the same phone in Work and in Social context in turn. Capture both. Test whether an observer can tell it is the same phone using checks 1 to 5 plus any stored per-context values.
- Tool class: B or C, then D.
- Pass: no identifier, constant, or timing feature links the two contexts. Fail: any link.

## Recording results

- Each check gets: date, build hash, devices, tool used, raw data location, outcome (pass, fail, inconclusive), and the tester.
- Inconclusive counts as not passed until repeated.
- Any fail is reported to the owner of the gate and entered into the audit scope.

## Known limits

- The plan does not cover someone who can see the user physically, camera surveillance, network-side or app-store records, or phones with extra radios or compromised firmware.
- Different phone chipsets handle address rotation differently; each model tested stands alone.
- Results apply only to the build hash tested. A new build needs a re-test of any changed transport code.
