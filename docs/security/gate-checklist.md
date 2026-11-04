# Go/No-Go Gate Checklist for v1.5

All five gates must be marked passed, with evidence, before the pilot goes ahead. Any gate that is blank, failed, or inconclusive means no-go. Owner and status fields are intentionally left blank; the team fills them in.

| # | Gate | Pass condition | Owner | Status | Evidence link | Date |
|---|------|----------------|-------|--------|---------------|------|
| 1 | Audit passed | The independent audit (see audit-brief.md) is finished and shows no unresolved critical findings. | | | | |
| 2 | Battery under limit | Measured battery use of the build is at or under the limit set by the team. The limit is: ______ (to be set by the team before testing, not after). | | | | |
| 3 | Cluster dense enough | A cluster of participants is dense enough to give a weekly signal. Density and the definition of a weekly signal are: ______ (to be set by the team). | | | | |
| 4 | Zero identifier-tracking failures | The tracking test (see tracking-test-plan.md) is complete with zero confirmed identifier-tracking failures. | | | | |
| 5 | No participant harm reported | No participant has reported harm connected to the pilot, and the reporting channel was open and working during the period. | | | | |

## Status values

Suggested values: passed, failed, inconclusive. Leave blank until a decision is made on the evidence.

## Rules

- Thresholds for gates 2 and 3 are fixed in writing before measurement begins.
- Passing gate 1 or gate 4 shows only that the audit and tests found no such problems in what they covered. It does not show the app is safe.
- Gate 5 is checked continuously during the pilot. A harm report after launch pauses the pilot until it is reviewed.
- Before launch, each participant must have received and confirmed pilot-risk-briefing.md.
- Gate results apply to one build. A change to the build requires re-checking the gates it affects.

## Sign-off

Decision (go / no-go): ____________________

Decided by: ____________________

Date: ____________________
