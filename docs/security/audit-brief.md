# Security Audit Brief

Status: PREPARED, NOT PERFORMED. This document defines the scope for an independent external audit. No audit has been done, and nothing here is a finding or a result.

## Why this audit exists

Spec section 7 requires an independent security audit before any pilot. Existing mesh messaging apps have had flaws that made users trackable [UNVERIFIED on specifics]. The users of this app may face legal and social risk, so a flaw can cause real harm.

## Pass criterion (pilot gate)

No unresolved critical findings. High and medium findings need a written disposition (fixed, mitigated, or accepted with a named owner). The auditor, not the development team, assigns severity.

## What we ask the auditor to do

1. Review the design and the code for each module below.
2. Answer the stated question for each module in writing, with evidence.
3. Try to break the assumptions listed in the threat model section.
4. Report findings with severity, reproduction steps, and a suggested fix.
5. Re-test fixes for critical findings.

## Threat model (summary for the auditor)

- Adversaries: a passive radio observer near the user; an active nearby attacker running a rogue node; a curious or hostile peer in the mesh; someone who takes a locked or unlocked device.
- Assets: user identity, social graph, message content, contact overlap, location history, existence of the app itself.
- Out of scope as defended threats: compelled unlock (the user is forced to unlock the phone); legal and social consequences; entrapment and infiltration by a person who looks like a legitimate user. These are known gaps and must be stated as such in the report, not tested as passing.

## Modules and questions

### core-crypto
Covers key management and PIN wrapping, ephemeral id derivation, commit-then-reveal, and the forward-secret session construction.
- Key management and PIN wrapping: Can an attacker with a copy of device storage recover keys faster than the PIN's stated brute-force cost? Are the KDF, its parameters, salt handling, and retry limiting sound? Are keys ever held in memory, logs, backups, or crash reports longer than needed?
- Ephemeral id derivation: Are ids derived so that two ids from the same device cannot be linked without a secret? Is the rotation schedule independent of anything observable? Is there any counter, timestamp, or nonce pattern that leaks linkage?
- Commit-then-reveal: Does the protocol bind the commitment so the revealer cannot change the value? Can a party learn the other side's value without revealing its own? Are abort and replay cases handled?
- Forward-secret session construction: Does compromise of long-term keys expose past sessions? Is the ratchet or key schedule correct, and are old keys erased? Are there downgrade, replay, or reordering weaknesses? Is the primitive set standard and used as designed?

### core-psi
- Does the private set intersection integration reveal anything beyond the intersection to either party, including set size, timing, or repeated-query leakage? Can a malicious party probe for membership by running many sessions? Is randomness sourced correctly and per session?

### storage
- Is data at rest encrypted with keys that are not stored next to it? Are there plaintext copies in caches, databases, temp files, thumbnails, or system backups? Does deletion actually remove data, and what remains after it?

### security
Covers decoy and wipe behaviour.
- Does the decoy mode present a believable, consistent state without revealing the real one by behaviour, timing, storage size, or leftover files? Does wipe remove keys and data irrecoverably within its stated conditions? Can a wipe be triggered by accident or by an attacker? What does a forensic examiner see after wipe?

### invites
- Are invite tokens unguessable, single use, and time limited? Can a token be replayed, forwarded, or used to learn who issued it? What does a captured token reveal about the issuer or the network?

### disclosure
- Does the disclosure flow reveal only what the user chose, to whom they chose? Can a peer cause disclosure by crafting messages? Is consent recorded and revocable where the design says it is?

### chat
- Are messages authenticated and encrypted end to end? Can a peer or relay learn sender, recipient, or group membership from metadata? Are message parsing and attachment handling safe against malformed input? Are there injection, resource exhaustion, or crash risks?

### transport-ble
- Does the advertisement, scan response, or GATT surface expose a stable identifier? How is the Bluetooth address handled relative to the ephemeral id? Can a rogue node force connections, downgrade pairing, or exhaust resources? Note: the passive-tracking tests are covered by the separate tracking test plan; the auditor should review the design and code for the same risks and may reuse that plan.

## Android manifest and permissions
- Is every requested permission needed, and is each justified in writing? Are there exported components, intent filters, deep links, content providers, or broadcast receivers that outsiders can reach? Are backup, debuggable, and cleartext settings safe? Does the app name, icon, or notification reveal its purpose on a locked or shared phone? Do any permissions (for example location, which Bluetooth scanning may need) create a record elsewhere on the device?

## Deliverables from the auditor
- Written report with scope, method, findings, severity, and status.
- Answers to each module question above.
- A statement of what was not covered.

## Reporting rules
- The audit must not be summarized as "secure". A clean report shows only that the auditors found no critical issues in the scope they covered.
- Findings stay confidential until fixed.
- The gate checklist records the audit as passed only when the report shows no unresolved critical findings.
