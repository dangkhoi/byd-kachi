# Pre-commit sensitive-data scan — v1.05 release commit

> Reviewer session: `precommit-security-scan-1.05` · 2026-08-11
> Scope authority: `.kiro/steering/pre-commit-security.md` + `.kiro/steering/security-overrides.md` (workflow §6)
> Mode: REPORT ONLY — no files modified, no git write commands run.

## SECURITY SCAN REPORT

- **Files scanned:** 42 tracked-modified + ~164 untracked-new (≈206 committable paths). Full-tree regex sweeps (secrets/keys, credentials, emails, machine paths, RFC1918 IPs, VIN/serial, GPS, phone) plus full-content reads of the highest-risk new docs/scripts/JSON (`scripts/re/rcc_extract.py`, `scripts/vehicle/hud3-speedlimit-v4.sh`, `docs/_handoff/cast-nav-ux-release-v104-execution.md`, `docs/_handoff/session-2026-08-06-pm2-…md`, `docs/diagnostics/oncar-speedlimit-test-2026-08-11.md`, `tools/re/manifest.json`). No committable binaries were present in the untracked set (all text).
- **Verdict:** **BLOCKED** — 1 [BLOCK] (real machine-username path in a new committed doc) must be redacted first. 4 [WARN] (un-redacted vehicle IPs in new docs/scripts) should be redacted per `security-overrides.md`.

---

### [BLOCK] — 1 (must fix before commit)

1. **`docs/_handoff/cast-nav-ux-release-v104-execution.md:11` and `:68`** — category: **machine-path** (workspace-specific, `security-overrides.md` → "block on commit").
   Real machine-username absolute path is embedded verbatim:
   - L11: `` `/Users/<user>/Documents/workspaces/experiments/byd/ClusterNav` ``
   - L68: `WORKING DIR: /Users/<user>/Documents/workspaces/experiments/byd/ClusterNav`
   - **Remediation:** replace `<repo-root>` with `<repo-root>` (or a relative path). The sibling new docs (`session-2026-08-06-pm2-…md`, `oncar-speedlimit-test-2026-08-11.md`) already use `<repo>` / `<ip>` placeholders — match that style. This is the only file in the commit set containing the real `<home>/` path.

---

### [WARN] — 4 (ambiguous; `security-overrides.md` policy = redact vehicle IPs to `<vehicle-ip>`)

> Context: these are RFC1918 **private** IPs (not internet-routable, low real-world risk), and the **same** IPs already exist in *pre-existing tracked* files (`CLAUDE.md`, `docs/refactor-car-execution/verdicts.tsv`) that are already public. The workspace override nonetheless lists them as "auto-redact before commit / block on commit." Owner decision: redact the new occurrences for policy consistency, or accept.

1. **`docs/_handoff/session-2026-08-06-pm2-firstlaunch-fixes-and-hud-enable.md:4,41,59`** — personal-hotspot vehicle IP `<vehicle-ip>:5555` ×3. Redact → `<vehicle-ip>`.
2. **`docs/_handoff/hud-cluster-injection-findings-2026-08-10.md:492`** — `<vehicle-ip>` ("Confirmed on-car"). Redact → `<vehicle-ip>`.
3. **`scripts/vehicle/hud-cluster-probe.sh:19`** — `<vehicle-ip>:5555` in a usage-comment example. Redact → `<ip:port>` (the script body already uses `<serial|ip:port>` placeholders elsewhere).
4. **`scripts/re/tests/test_expand_candidate_coverage.py:319`** — the **real** vehicle IP `<vehicle-ip>` used as a scrubber-test fixture input. Recommend replacing with a synthetic RFC1918 (e.g. `10.0.0.1`) so the real IP is not embedded in a test.

---

### [INFO] — false-positives / verified-safe / not-in-scope

**No real secrets found anywhere in the tree.**
- Comprehensive regex sweep for AWS/GitHub/Google/OpenAI/Anthropic/Slack/Stripe/Telegram keys, OAuth/JWT secrets, `-----BEGIN … PRIVATE KEY-----`, DB-URLs-with-password → **0 matches**. The only secret-shape hits are the project's **own secret-detector regex definitions** (`scripts/verify-hud-sign-candidate-expansion.sh:241`, `scripts/re/expand-candidate-coverage.py:111`, `.git/hooks/pre-commit:52`) — detector patterns, not credentials.
- **Signing secrets protected:** `keystore.properties`, `local.properties`, `app/release.keystore` exist locally but are **gitignored** (`.gitignore` L11/L16/L17) and absent from the commit set. `app/build.gradle.kts` reads signing values from the properties file — no hardcoded password/alias.
- **Next-commit git identity is CLEAN:** `git config user.email = dangkhoi@users.noreply.github.com`, `user.name = Đăng Khôi` → the v1.05 commit will be authored correctly per `identity.md`.
- **Firmware constants (allowed):** ZMQ `192.168.195.2:8889` / `192.168.195.3:6666` in `cluster-hud-injection-STATE.md` and `scripts/vehicle/hud3-recon.sh` — firmware-internal, explicitly permitted.
- **Synthetic PII test fixtures:** `person@example.com`, `alice@example.test`, `+84912345678`, `password=correct-horse-battery-staple`, `name=Jane Citizen`, `GPS 21.0285,105.8542`, `/Users/{person,alice,example}/…` in `offcar-planner/src/test/**` and `scripts/re/tests/**` — inputs for the report-sanitizer tests; synthetic.
- **SHA-256 hashes** throughout `docs/diagnostics/hud-sign-re/**`, `tools/re/manifest.json`, exact-source JSONs, and the blocklisted-APK hash in `scripts/vehicle/common.sh` — content-integrity hashes of public tools/artifacts, **not secrets** (as flagged in the task).
- **Owner public attribution** "Đăng Khôi / dangkhoi" (`oncar-speedlimit-test-2026-08-11.md` et al.) — the **intended** public identity per `identity.md`; not a leak.
- **Non-sensitive identifiers:** BYD cluster physical display ID `19261206365013889`; standard Android debug-key cert SHA `dc521dad…` (`CN=Android Debug`); public repo URL `dangkhoi/byd-cluster`. `tools/re/manifest.json` uses `<user-cache>`/`<project-root>` placeholders + standard `/opt/homebrew/…` paths — clean.

**Pre-existing / out-of-scope (already committed & public — NOT introduced by this commit; noted for owner awareness):**
- Company email `<company-email>` in **early git history** (`.git/logs/**`, initial-release commits). This is commit metadata inside `.git/` (never committed as file content) and is already in public history; repo-local identity is now fixed. Not fixable via this commit (would need a history rewrite). Violates `identity.md` only in old history.
- Real GPS coordinate `<redacted-gps>` (residential location) in **tracked** `docs/_handoff/session-2026-08-06-pm-v1.04-release-baseline.md:52` — already public location PII; out of this commit's new content. Owner may want a future history scrub.
- Vehicle IPs in **tracked** `CLAUDE.md:213` and `docs/refactor-car-execution/verdicts.tsv` — already public; out of scope for this commit.

---

### Required before commit
1. Redact the [BLOCK] machine path in `docs/_handoff/cast-nav-ux-release-v104-execution.md` (L11, L68).
2. (Recommended) Redact the 4 [WARN] vehicle-IP occurrences to `<vehicle-ip>` / `<ip:port>` for `security-overrides.md` consistency.
3. Re-run this scan on the corrected set → expect CLEAN.
