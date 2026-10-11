# LifeSaver plan

Order is deliberate: a useful DNS blocker exists after M1. Device-owner hardening is last because it needs a wipe, a second AVD, and is easy to get wrong.

No milestone talks to a physical phone. Device-owner work on the real phone is a later, explicitly approved step documented in [DEVICE_OWNER_SETUP.md](DEVICE_OWNER_SETUP.md), not a milestone that an agent runs.

## Approved decisions (2026-10-11)

- Phone: Samsung Galaxy A15, Android 16 (API 36). The emulator on this machine is API 37, which the SDK labels Android 17. M0 and M1 are verified on that emulator. Behavior on the A15 is unverified until the owner installs the app.
- Package: `app.lifesaver`.
- Stack: Kotlin, Gradle Kotlin DSL, Jetpack Compose, minSdk 26, targetSdk 36, compileSdk 37 (the installed platform is `android-37.0`).
- Engine: local DNS-sinkhole `VpnService`. `:blocklist` arrives at M2. `:policy` arrives at M8.
- Cooldown, when M5 is built: **48 hours of device-on time**. Rules are in [COOLDOWN.md](COOLDOWN.md).
- Accountability partner: TOTP design stands. Implementation is deferred until asked. No webhook in the first build.
- Play Store stays available after device owner. Do not apply the restriction that also blocks registered app stores.

## M0 — Hello world on the emulator

**Goal.** The repo is a real Android project that builds and installs a Compose app on the existing emulator.

**Tasks.**

- Scaffold Gradle Kotlin DSL, `:app`, Compose, `minSdk 26` / `targetSdk 36` / `compileSdk 37`.
- Add ktlint, detekt, unit-test wiring, `.gitignore`, Gradle Wrapper.
- Add a GitHub Actions workflow: JDK 21, `assembleDebug`, `test`.
- One screen: app name + "Protection is off" + version.

**Definition of done.**

- `.\gradlew.bat :app:assembleDebug test ktlintCheck detekt` succeeds on this machine.
- CI workflow file exists (first green run happens after the initial push, which needs approval).
- Debug APK installs on `Medium_Phone_API_37.0` and shows the screen.

**How to test.**

1. Start only the AVD `Medium_Phone_API_37.0`.
2. Confirm `adb devices` lists a serial starting with `emulator-`.
3. Install the debug APK and open LifeSaver.
4. Run unit tests locally.

## M1 — Dns sinkhole VpnService (hardcoded list)

**Goal.** A local `VpnService` resolves DNS and blocks a tiny hardcoded set (for example `example.com` plus one real test domain we control, such as a subdomain we stand up, or `pornhub.com` used only on the emulator).

**Tasks.**

- Add `:vpn`. Declare `VpnService` with `BIND_VPN_SERVICE` and the `android.net.VpnService` intent filter.
- Call `VpnService.prepare()`, then `startForeground()` with type `systemExempted` (Android 14+ requirement for an active VPN).
- Establish a TUN that advertises a fake DNS (IPv4 + IPv6). Intercept UDP/53 (and TCP/53 if cheap). Answer `NXDOMAIN` / sinkhole for listed names; forward other queries to the network DNS with `protect()`.
- Pass through or drop non-DNS traffic in the simplest correct way (see ARCHITECTURE.md). Do **not** implement a full userspace TCP stack.
- Debug UI: Start / Stop protection. Show "VPN up" vs error.
- Unit tests for DNS name extraction and list match.

**Definition of done.**

- On the emulator, with the VPN on: a blocked name does not load in Chrome; an unblocked name (for example `example.org` if not listed) does.
- Turning the VPN off restores the blocked name.
- App survives a rotation; service shows a non-dismissible notification.

**How to test.**

1. Emulator only. Grant the system VPN consent dialog.
2. `adb logcat` tagged `LifeSaver` / `BlockerVpn`.
3. Chrome: visit a blocked host and an allowed host.
4. `adb shell dumpsys vpn` (or Settings → Network & internet → VPN) shows LifeSaver connected.
5. Record what happens for IPv6 and for Chrome if Private DNS is on. Mark anything surprising `unverified` until we see it.

**Stop here after implementation.** Show the owner the emulator evidence before M2.

## M2 — On-device blocklist store

**Goal.** Replace the hardcoded list with a real store and a fast suffix lookup.

**Tasks.**

- Add `:blocklist`. Bundle StevenBlack porn-only (MIT) as the offline fallback.
- Parse hosts / domain lines into a normalized suffix set (hash set of reversed labels, or a compact trie).
- Room or a memory-mapped snapshot plus a version table. Start with an in-memory immutable set loaded from a file; add Room only if we need incremental updates.
- Allow / deny overrides stored locally (small).
- UI: last list version, entry count, "reload bundled list".

**Definition of done.**

- Lookup of `www.blocked.example` matches `blocked.example` in unit tests.
- App cold-starts with the bundled list with no network.
- Emulator: a bundled-list domain is blocked; a domain on the allow override is not.

**How to test.** Unit tests for parser + suffix match. Emulator Chrome against 3 known names (blocked / allowed / override).

## M3 — IPv6, DoH/DoT leakage, known resolver block

**Goal.** Close the easy DNS-bypass holes that a local sinkhole leaves open.

**Tasks.**

- Always configure IPv6 on the TUN (`addAddress` + DNS). Confirm no IPv6 leak on the emulator.
- Ship a small built-in list of known DoH / DoT hostnames and any stable IPs we can document (Cloudflare, Google, Quad9, NextDNS, etc.). Route those to the TUN and sinkhole / refuse them so apps fall back to system DNS. **Mark IP completeness unverified** — anycast and app-hardcoded IPs change.
- Document Private DNS behavior: system DoT can bypass app DNS. Full lock is M8–M9.
- Do not try to MITM HTTPS.

**Definition of done.**

- Emulator test notes exist for: IPv6 literal navigation, Chrome Private DNS automatic, Firefox HTTPS-only / DoH if the Play image includes Firefox (otherwise install a debug browser APK on the emulator).
- Known DoH hostnames on the list return NXDOMAIN through our resolver.

**How to test.** Toggle emulator Private DNS (Off / Automatic / Private DNS provider hostname) and record pass/fail. This is the evidence file for THREAT_MODEL rows we currently mark unverified.

**Status (2026-10-11).** Implemented and verified on the emulator. Evidence and gaps (IPv6 data path, Firefox) are in [verification/M3.md](verification/M3.md).

## M3b — Content guard for Reddit / Discord (proposed, not approved)

**Why.** Reddit and Discord serve NSFW and normal media from the same hostnames over HTTPS. DNS cannot tell them apart. The owner asked for "close the app when NSFW content opens".

**Approach (needs owner approval; it reverses the "no Accessibility-based blockers" line below).** An `AccessibilityService` limited to a short package list (Reddit, Discord, optionally browsers). When it sees a strong NSFW signal in the on-screen text or view tree (NSFW / 18+ / age-restricted tags, a local list of NSFW subreddit and channel names), it sends the user to the home screen.

**Known limits.**

- Text-only: an unlabeled NSFW image is not detected. Screenshot + on-device ML (`takeScreenshot`, API 30+) would catch images but adds a model, battery cost and false positives. Out of scope unless asked.
- The user must enable the service, and on a sideloaded app Android 13+ also needs "Allow restricted settings". The user can turn it off again until M9. Device owner can re-enable it. Unverified.
- No page or screen content is stored or logged. Only a counter (M6).

## M4 — Minimal protected UI

**Goal.** A status surface a tired person can understand, without a one-tap "off".

**Tasks.**

- Status: Protected / VPN down / waiting for consent.
- Debug builds keep Start/Stop. Release/hardened builds hide Stop (M8 will lock this anyway).
- Empty settings: cooldown hours (read-only until M5), list version.

**Definition of done.** Launch → status is correct with VPN up and down. No settings page that disables protection in one tap.

## M5 — Delayed unlock (monotonic)

**Goal.** Turning protection off requires a request plus 48 hours of device-on time that the system clock cannot skip. See [COOLDOWN.md](COOLDOWN.md).

**Tasks.**

- Persist `requestBootCount`, `requestElapsedRealtime`, `accumulatedOnMillis`, `requiredOnMillis`.
- Advance using `SystemClock.elapsedRealtime()` (includes deep sleep; resets on reboot). Accumulate across reboots with boot count.
- Ignore wall-clock going backwards. Do **not** treat wall-clock forward jumps as progress (prevents "set date + 3 days").
- Debug builds: show remaining device-on time. No hidden skip gesture.
- Unit tests for accumulate / reboot / clock-rewind.

**Definition of done.**

- Request unlock → UI shows remaining time → after the configured on-time, Stop becomes available in debug.
- Setting the emulator date forward does not finish the cooldown.
- Reboot does not reset progress already accumulated.

**How to test.** Use a short debug cooldown (2 minutes) on the emulator. Change date/time in Settings. Reboot the AVD. Confirm math.

**Limit.** Time the phone is powered off does not count. That is safer against clock games and slower for a genuine unlock. Partner unlock (M11) is the escape hatch.

## M6 — Local logging

**Goal.** Enough audit for "did I turn this off?" and nothing that is a browsing history.

**Tasks.**

- Room (or a small append-only file) for events: VPN up/down, unlock requested / completed / cancelled, list update ok/fail, policy applied / missing, daily aggregate `blockedQueryCount`.
- Never store queried names, URLs, destination IPs, or per-app destinations.
- UI: last 50 events. No export in M6.

**Definition of done.** Unit tests that a DNS name cannot reach the log table. Emulator: generate events, force-stop, reopen, events still there.

## M7 — Signed list updates + offline fallback

**Goal.** Refresh the list on Wi-Fi without trusting a raw HTTP body.

**Tasks.**

- App contains an Ed25519 public key. A tiny update manifest (URL, SHA-256, version, signature) lives at a location we control, or we pin hashes of the upstream files we fetch.
- Fetch StevenBlack porn-only and optionally OISD NSFW **small** (GPL-3.0 — download at runtime, do not vendor into the APK unless we accept GPL distribution of that data file).
- Fail closed to the last good snapshot, then to the bundled MIT list.
- WorkManager, once a day, unmetered, with backoff.

**Definition of done.**

- Airplane mode: app still blocks using the bundled/last snapshot.
- Tampered payload (wrong hash or signature) is rejected; last good list stays.
- Successful update increments the version shown in the UI.

**How to test.** Unit tests for signature + hash. Emulator: point at a local mock (debug) with good and bad manifests.

## M8 — Device owner on a **test emulator** (always-on + uninstall)

**Goal.** LifeSaver is device owner on a dedicated AVD and can force always-on VPN with lockdown.

**Tasks.**

- Add `:policy` with `DeviceAdminReceiver` and a device-admin XML.
- Create a **second** AVD without Play Store if `dpm set-device-owner` fails on the Play image (likely; treat as unverified until we try).
- Provision with `adb shell dpm set-device-owner` on that emulator only. Follow [DEVICE_OWNER_SETUP.md](DEVICE_OWNER_SETUP.md).
- `setAlwaysOnVpnPackage(admin, package, lockdown=true)`.
- `setUninstallBlocked(admin, package, true)`.
- `setUserControlDisabledPackages` for our package (blocks force-stop and clear-data).
- Hide Stop in this build flavor.

**Definition of done.**

- Settings shows LifeSaver as device admin / device owner and it cannot be disabled.
- Uninstall is rejected.
- Always-on + lockdown is on. After AVD reboot, VPN comes back without tapping Start.
- With lockdown and VPN killed (if we can), other apps have no network.

**How to test.** Only on the DO AVD. Do not run these `adb` commands if any physical serial is present.

## M9 — User restrictions

**Goal.** Close Settings-based bypasses that device owner can actually close.

**Tasks.** Apply, then watchdog-reapply:

| Restriction / API | Why |
| --- | --- |
| `DISALLOW_CONFIG_VPN` | User cannot switch VPN off or add another. Apply **after** always-on is set (this restriction also prevents VPNs from starting, except owner always-on). |
| `DISALLOW_CONFIG_PRIVATE_DNS` | User cannot point DoT at Cloudflare. |
| `DISALLOW_DEBUGGING_FEATURES` | USB debugging / developer options. |
| `DISALLOW_SAFE_BOOT` | Settings / UI safe mode. |
| `DISALLOW_FACTORY_RESET` | Settings factory reset only. |
| `DISALLOW_CONFIG_DATE_TIME` + `setAutoTimePolicy` (API 36) | Clock games. |
| `DISALLOW_ADD_USER` | Second user without the VPN. |
| `DISALLOW_INSTALL_UNKNOWN_SOURCES` only. Do **not** set `DISALLOW_INSTALL_UNKNOWN_SOURCES_INCLUDING_REGISTERED_APP_STORES`. | Slows sideloading a second browser. Play Store stays, which the owner asked to keep. A Play Store browser is still a bypass until DoH blocking (M3) covers it. |
| `setGlobalSetting(ADB_ENABLED, "0")` if still needed after the restriction | Belt and suspenders. |

Private DNS mode: dedicated setters are opportunistic or specified-host only. There is a `PRIVATE_DNS_MODE_OFF` **read** constant; a public "set off" method was **not** found in the current `DevicePolicyManager` reference. Treat "force Private DNS off" as **unverified** and test on the DO emulator. Prefer blocking user changes plus refusing known DoH/DoT endpoints over standing up our own DoT server.

**Definition of done.** Each restriction is toggled on the DO emulator and the corresponding Settings row is missing or disabled. A short checklist is checked into `docs/verification/`.

**How to test.** One restriction at a time on the DO AVD. Screenshot or notes. Reboot. Confirm they persist.

## M10 — Watchdog

**Goal.** If a restriction or always-on flag is cleared, put it back.

**Tasks.**

- On boot (owner-started VPN + `DeviceAdminReceiver` callbacks) and on a conservative periodic job, re-read `getAlwaysOnVpnPackage`, uninstall-blocked, and restriction keys; re-apply if missing.
- Log `policy_reapplied` / `policy_ok` (M6 events).
- Do not fight an intentional unlock that is currently in the allowed window.

**Definition of done.** On the DO emulator, if we clear always-on via our own debug admin API, the watchdog restores it within one period. After a genuine completed unlock, it stays off until re-armed.

## M11 — Accountability partner (deferred)

**Goal.** A second person holds the unlock secret. Optional tamper ping. Not started until the owner asks. The design (TOTP, secret shown once) is unchanged.

**Tasks.**

- One-time setup: generate a TOTP secret, show a QR **once**, then store only the secret in `EncryptedSharedPreferences` and never render it again.
- Unlock after cooldown **or** a valid TOTP (partner can skip the wait).
- Optional: HTTPS POST to a URL the partner chooses (ntfy / email webhook) on unlock-request and on "protection off". Fail open for network; do not block the VPN if the ping fails.

**Definition of done.**

- After setup, the secret is not in the UI or logcat.
- Wrong TOTP fails. Right TOTP (from a test authenticator we control) unlocks.
- Airplane mode: TOTP still works; webhook is skipped and logged.

**How to test.** Emulator + any TOTP app APK. Do not use the owner's real partner secret in screenshots.

---

## Explicitly not in this plan

- Running any provisioning on the physical phone (owner-driven, later).
- Root, Magisk, custom recovery, Accessibility-based blockers (except the proposed M3b), DNS-over-HTTPS MITM, a remote kill-switch server we operate, Play Store listing, content scanning inside images/video (on-device ML).
- Blocking IP-literal CDNs used by Telegram / Reddit / X (called out as residual risk; a later optional IP list can be discussed after M3 evidence).
