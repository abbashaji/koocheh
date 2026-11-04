# Check your copy of Koocheh before you trust it

APK files can be copied and changed by other people. This check takes a few
minutes and tells you whether your copy was signed by the Koocheh maintainers.

**If you skip this check, you stay exposed to tampered APKs.** A changed copy
can look exactly like the real app.

## Steps

1. **Find the published fingerprint.** The maintainers publish a long code
   (the "signing key fingerprint") with each release. Get it from the
   maintainers' own pages, not from the person or site that gave you the APK.
   If the maintainers have not published one yet, there is nothing to check
   against, and you should not treat the copy as verified.
2. **Find the fingerprint inside the app.** Open Koocheh and look for the
   screen that shows the app's signing fingerprint. [UNVERIFIED: the name and
   location of this screen must be confirmed against the finished app.]
3. **Compare the two codes.** Every character must match, in the same order.
   Reading them aloud with another person helps.
4. **If they match,** your copy was signed with the published key.
5. **If they differ in any way,** do not use the app. Delete it, and get a
   copy from a different trusted source. Tell the maintainers.

## Good to know

- A match tells you who signed the app. It does not tell you the app is
  free of mistakes.
- Someone who can show you a fake fingerprint on a fake page can fool you.
  Compare against the maintainers' published value, and where you can, ask
  someone you trust to check the same value.
- Technical users can check the file before installing with
  `scripts/verify-apk.sh` (see the header of that script for usage).
