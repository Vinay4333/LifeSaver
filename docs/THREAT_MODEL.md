# LifeSaver threat model

**Adversary:** the same person who installed the app, at a low-willpower moment. Minutes of friction matter. Hours of friction plus a second human matter more. Root, a soldering iron, and a second phone are out of scope as "wins."

**Asset:** "this phone does not resolve or fetch pornographic sites and common in-app porn surfaces, and I cannot casually undo that."

**Honesty rule:** if Android cannot block it, the row says so.

Legend: **M#** is the milestone that first addresses the row. Residual risk stays even after that milestone.

| Bypass | Mitigation | Residual risk | Milestone |
| --- | --- | --- | --- |
| Turn the VPN off in Settings or the app | M1: app Stop is the only easy off switch. M8: `setAlwaysOnVpnPackage(..., lockdown=true)` so the system keeps the service up and **blocks traffic that leaves the VPN**. M9: `DISALLOW_CONFIG_VPN` hides/locks VPN settings. M10: watchdog re-applies always-on. | Lockdown + a dead VPN means **no internet**, which is the intended fail-closed. A bug in our service can take the whole network down (called out in the DPM docs). System apps may still bypass VPN. | M1, M8, M9, M10 |
| Ignore always-on / "block connections without VPN" | User can set this in Settings from Android 7; owner can force it via `setAlwaysOnVpnPackage` lockdown ([VPN guide](https://developer.android.com/develop/connectivity/vpn), [enterprise networking](https://developer.android.com/work/dpc/network-telephony)). | Without device owner, the same Settings page can turn lockdown off. | M8 |
| Change Private DNS to Cloudflare / Google / a custom DoT host | M3: sinkhole known DoH/DoT names. M9: `DISALLOW_CONFIG_PRIVATE_DNS` (device owner or org-owned profile owner only; applies globally). | **Unverified:** whether we can *force Private DNS off*. DPM exposes `setGlobalPrivateDnsModeOpportunistic` and `setGlobalPrivateDnsModeSpecifiedHost`, and a `PRIVATE_DNS_MODE_OFF` getter constant, but no public "set off" method was in the current reference. Specified-host DoT may **bypass** the VPN DNS path; official note: the Private DNS resolver must be reachable inside *and* outside the VPN. We will not run our own DoT server in v1. | M3, M9 |
| App DoH (Chrome, Firefox, Brave, some VPN apps) | M3: block well-known DoH hostnames and documented IPs so the app falls back to system DNS. | Apps with hardcoded IPs or encrypted client hello to a moving CDN will still get through. **Unverified** until we try Chrome/Firefox on the emulator. | M3 |
| Install another browser | DNS sinkhole applies to every app that uses system DNS (M1). M9: optionally `DISALLOW_INSTALL_UNKNOWN_SOURCES` to slow sideloads. | Play Store browsers still install unless we block Play or unknown-sources-including-stores (that also hurts legitimate updates). A browser that uses DoH needs M3. | M1, M3, M9 |
| In-app browsers / Custom Tabs / WebView | Same as system DNS if they use the system resolver. | An app that embeds its own Chromium with DoH is the Chrome-DoH case. | M1, M3 |
| Telegram / Reddit / X / similar media | Domain list catches named hosts that go through system DNS. | **Cannot fully block.** Many clients use IP literals, their own CDNs, or shared hosts (`i.redd.it`, `pbs.twimg.com`, Telegram DC IPs) that we must not blanket-block without breaking the app. Image/video scanning is out of scope. Residual: in-app porn on those platforms. | M2 (partial), never complete |
| Another VPN app | Only one `VpnService` is active per user; starting another replaces ours ([VPN guide](https://developer.android.com/develop/connectivity/vpn)). M8 always-on + M9 `DISALLOW_CONFIG_VPN` prevent the user from switching. | Before M8, installing Proton/WireGuard and tapping Connect wins. | M8, M9 |
| Uninstall LifeSaver | M8: `setUninstallBlocked(admin, package, true)`. Device owner cannot be removed by the user except factory reset ([DPM](https://developer.android.com/reference/android/app/admin/DevicePolicyManager)). | Recovery / fastboot wipe still removes everything. | M8 |
| Force-stop or clear app data | M8: `setUserControlDisabledPackages` — user cannot clear data or force-stop; packages are also exempted from App Standby. M9: `DISALLOW_APPS_CONTROL` if we want Settings → Apps locked down further. | **Unverified** on API 37 Play images. Safe mode (below) still avoids our process. | M8, M9 |
| Safe mode | M9: `DISALLOW_SAFE_BOOT` (device owner / primary-user PO / org-owned PO parent). | **Unverified** whether every OEM volume-key chord honors this. Some recovery menus are outside Android. | M9 |
| USB debugging / adb | M9: `DISALLOW_DEBUGGING_FEATURES` disables debugging including USB debugging when set by a device owner. Enterprise guide also says to enable this so adb cannot override always-on VPN. Optional `setGlobalSetting(ADB_ENABLED, "0")`. | If adb is already authorized **before** we set the restriction, **unverified** whether an existing session survives until reboot. Bootloader `fastboot` is a different channel. | M9 |
| Developer options | Covered by `DISALLOW_DEBUGGING_FEATURES`. `setGlobalSetting(DEVELOPMENT_SETTINGS_ENABLED)` has **no effect** since API 23 (official DPM javadoc). | OEM secret codes: unverified. | M9 |
| Factory reset from Settings | M9: `DISALLOW_FACTORY_RESET` — Settings path only, device/profile owner on an admin user. | **Cannot block** recovery-mode wipe, bootloader `fastboot erase` / `oem unlock`, or a service-center flash. This is the accepted back door. | M9 |
| Factory reset from recovery / fastboot | None in-app. Optional later: `setFactoryResetProtectionPolicy` only helps against *other people* unlocking a wiped device, not against the owner. | Accepted residual. | None |
| Second user / guest / private space | M9: `DISALLOW_ADD_USER`. Android also has private-space / private-profile flags (`DISALLOW_ADD_PRIVATE_PROFILE` exists in current `UserManager`). | **Unverified** on this emulator image whether a private space can run a browser that misses our VPN. Test in M9. | M9 |
| Samsung Secure Folder, Dual Messenger, or another Knox container on the Galaxy A15 | None in M0–M1. A device-owner VPN applies to the user it is set on. A separate Knox user can miss it. | **Unverified** on this phone. One UI safe-mode key chords may also differ from AOSP `DISALLOW_SAFE_BOOT`. Do not treat either as solved. | M9 attempt; may stay residual |
| Clear data + reinstall to skip cooldown | `setUserControlDisabledPackages` + uninstall blocked + same signing key. Cooldown lives in app files. | Factory reset clears it. | M5, M8 |
| Set date/time forward to finish the cooldown | M5: cooldown uses `elapsedRealtime` + boot-count accumulation, not `System.currentTimeMillis()`. M9: `DISALLOW_CONFIG_DATE_TIME` and `setAutoTimePolicy` (API 36 / Android 16). | Powered-off time does **not** count (safer, slower). A rooted clock is out of scope. | M5, M9 |
| Airplane mode + wait | Cooldown only counts device-on elapsed time. Airplane mode still advances `elapsedRealtime`. | Fine. Network is already gone. | M5 |
| Install a second DNS changer / "Private DNS" app | Those apps cannot set the global Private DNS without owner APIs. M9 locks the setting. | Before M9, the user can open Settings themselves — no extra app needed. | M9 |
| `adb shell dpm` / `pm uninstall` / `am force-stop` | M9 debugging restriction. M8 uninstall blocked. | Physical phone with USB debugging already on, before M9, is wide open. That is why we never leave adb on after hardening, and why agents must not enable it on the phone. | M9 |
| Bootloader unlock / custom ROM / root | Out of scope. Developer options + OEM unlocking can be discouraged via debugging restriction. | A determined owner with a PC wins. | None |
| New phone / PC / borrowed device | Out of scope. | Always. | None |
| Disable notification / ignore the key icon | Notification is required for the FGS; always-on also shows system UI. Not a bypass. | User can emotionally ignore it. | M1 |
| Revoke VPN consent | Owner always-on is "automatically granted" and does not need the consent dialog ([enterprise networking](https://developer.android.com/work/dpc/network-telephony)). | Before M8, revoking in Settings stops us (`onRevoke`). | M8 |
| Play Store update that we signed that removes protections | Social / process: do not ship a build that weakens policy without cooldown + partner. Optionally pin `hardened` as the only installed variant. | The owner can sideload an older/weaker APK they built, until unknown-sources is restricted. | M9, process |
| Supply-chain blocklist (malicious update) | M7: signature + hash; fail to last snapshot. | If our private key leaks, a hostile list could overblock (DoS), not silently underblock unless we allow empty lists — reject empty/huge deltas. | M7 |

## What we will not claim to block

1. **Recovery / fastboot factory reset.** `DISALLOW_FACTORY_RESET` is Settings-only.
2. **Telegram / Reddit / X in-app media** that never does a blockable DNS lookup.
3. **Hardcoded-IP or fully in-app DoH** after we have blocked the obvious public resolvers.
4. **A second device.**
5. **Someone who will spend an hour with a USB cable and a blog post before M9, or with fastboot after M9.**

## Android version notes that affect this table

See the research section in the chat write-up (Step 0). Short version:

- Always-on + lockdown APIs are old (API 24 / 29) and still current through Android 16 enterprise docs (page updated 2026-10-01). No Android 16 VPN policy break was documented.
- Android 14: FGS types mandatory; `systemExempted` is the correct VPN/DO type. Policy conflicts resolve MostRestrictive for `addUserRestriction` / `setUninstallBlocked`.
- Android 15: do not use `dataSync` FGS for this app.
- Android 16: `setAutoTimePolicy` / `setAutoTimeZonePolicy` are the right time APIs.

Anything in the table marked **unverified** will be filled after emulator tests in M1, M3, or M9.
