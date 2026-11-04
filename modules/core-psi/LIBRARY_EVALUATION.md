# core-psi: library evaluation

Status: **nothing below was checked against a source.** It was written from memory with no
search or repository inspection. Every factual statement about a library, project or paper is
tagged **[UNVERIFIED]** and must be confirmed from the project's own repository, release page
or paper before anyone relies on it. Statements without the tag are our own requirements or
reasoning, not claims about a library.

## What we need (spec 5.3)

- Each side learns only what the reveal level allows about shared tags and nothing about
  unmatched tags. Tier 0 shows a count (`COUNT_ONLY`), Tier 1 shows names (`TAGS`).
- About 10 tags per side. The vocabulary is small, so plain hashing is out.
- `COUNT_ONLY` has to be a protocol-level cardinality mode. Hiding a field in `PsiResult` is
  not enough, because a modified client could then extract identities.
- Runs on low-end Android devices over a local link, with no network code in this module.

## Checklist for every candidate

1. **Maintenance status**: last release date, open security issues, number of active maintainers.
2. **Audit status**: independent audit report exists, covers the protocol and the implementation.
3. **Mobile performance at card size**: round time and peak memory for 10 vs 10 tags on a
   low-end device, via `PsiBenchmark` (androidTest). Also bytes on the wire.
4. **Cardinality-only mode**: native in the protocol, not a wrapper around full intersection.
5. **Android packaging**: JVM/Kotlin binding or JNI/NDK build, ABIs, APK size added, minSdk.
6. **Security model**: semi-honest or malicious peers. Does it hide set sizes or only contents.
7. **License** compatible with distribution.

## Candidates

### A. OpenMined PSI
- C++ library with bindings for several languages. **[UNVERIFIED]**
- Based on a DDH / elliptic-curve style protocol with optional Bloom-filter compression.
  **[UNVERIFIED]**
- Offers a cardinality-only variant. **[UNVERIFIED]**
- Has a Java or Android binding usable from Kotlin. **[UNVERIFIED]**
- Actively maintained. **[UNVERIFIED]**
- Independently audited. **[UNVERIFIED]**
- Check: items 1-7; especially the Android binding and cardinality mode.

### B. Google private-join-and-compute
- C++ project implementing PSI-style protocols with an additional sum-of-associated-values
  feature. **[UNVERIFIED]**
- Described as not an officially supported product. **[UNVERIFIED]**
- No ready Android/Kotlin binding. **[UNVERIFIED]**
- Check: whether the extra functionality can be dropped, and the NDK porting cost.

### C. Research libraries from the academic PSI community (for example libPSI, volePSI)
- C++ implementations of modern PSI protocols from academic groups. **[UNVERIFIED]**
- Aimed at research and benchmarking rather than production mobile use. **[UNVERIFIED]**
- Have a cardinality-only mode. **[UNVERIFIED]**
- Check: maintenance, audit, and whether a 10-element set even benefits from these designs.

### D. Hand-built DDH-style PSI on a vetted elliptic-curve primitive library
- A hash-to-curve, blind, exponentiate exchange is a well-known textbook construction.
  **[UNVERIFIED]** (the description of the textbook construction; no specific library claimed)
- This cell forbids writing a protocol ourselves. It is listed only so the decision record shows
  why it is not recommended: it would need an expert cryptographic review before use.

### E. Server-assisted contact-discovery approaches (trusted hardware or an online service)
- Rejected for this module: they need network code and a trusted operator, and spec 5.3 as
  given to this cell implies a local exchange. Revisit only if the owners change that.

## Risks the engine alone cannot fix (our reasoning, to be confirmed by the owners)

- **Dictionary probing.** PSI reveals the intersection by design. A peer that presents a card
  holding the whole vocabulary learns every tag you hold. Mitigations sit outside the
  cryptography: enforce the 10-tag card size, rate-limit sessions per peer, and require a
  protocol where card size cannot be silently exceeded (a malicious-security property).
- **Count oracle.** Repeated `COUNT_ONLY` sessions with changing inputs let a peer binary-search
  your tags. Needs a per-peer session limit.
- **Set-size leakage.** Most protocols reveal each side's set size. Padding to a fixed size
  with dummy elements may be required.
- **Contract limits.** `PsiContractTest` catches plaintext or unsalted-hash leaks and wrong
  results. It is not a proof of privacy.

## Decision the owners must make

1. Which candidate to adopt, after the **[UNVERIFIED]** items above have been checked from
   primary sources. If none passes the audit and cardinality-mode checks, whether to accept
   an unaudited engine or defer Tier 0/Tier 1 matching.
2. Whether semi-honest security is acceptable or malicious security is required.
3. Whether `COUNT_ONLY` must be a native cardinality protocol (recommended) and whether card
   size must be padded.
4. The acceptable round time and transcript size on the lowest-end target device, measured
   with `PsiBenchmark`.
5. Session rate limits per peer, and who owns them (this module has none).
6. Who funds and schedules an independent cryptographic review.
