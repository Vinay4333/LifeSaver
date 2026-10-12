# LifeSaver

Personal Android app that blocks pornographic content on **this phone only**. The adversary is the owner's late-night impulsive self, not a remote attacker. The goal is to raise the cost of bypassing protection, not to make removal impossible. A factory reset, recovery wipe, or bootloader unlock will always exist.

This is a single-user, sideloaded app. It is not a product, not an MDM, and not designed for other people's devices.

## Threat model (summary)

- **In scope:** disabling the VPN, changing Private DNS, uninstalling, safe mode, USB debugging / adb, developer options, installing another browser or VPN, in-app browsers, app DoH, clearing app data, changing the clock to skip a cooldown, Telegram / Reddit / X media CDNs.
- **Out of scope as "solved":** factory reset from recovery / fastboot, bootloader unlock, a second physical device, a new phone, flashing a custom ROM.
- Full bypass table: [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md).
- Architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
- Milestones: [docs/PLAN.md](docs/PLAN.md).
- Device-owner provisioning: [docs/DEVICE_OWNER_SETUP.md](docs/DEVICE_OWNER_SETUP.md).
- Unlock rules: [docs/UNLOCK.md](docs/UNLOCK.md) (supersedes [docs/COOLDOWN.md](docs/COOLDOWN.md)).

Target device: Samsung Galaxy A15, Android 16 (API 36). Package `app.lifesaver`. The local emulator is API 37. Do not install on the phone unless this chat turn says so.

## Hard safety rule

**Any command that could wipe, factory-reset, lock, provision, or change device-owner state on a physical phone needs the owner's explicit approval in that chat turn.**

Never do the following against a physical device, even if a cable is plugged in:

- `adb` of any kind (devices, install, shell, reboot, backup, restore, `dpm`, `pm`)
- `fastboot` / `heimdall` / OEM flash tools
- QR / NFC / cloud device-owner provisioning
- `dpm set-device-owner`, `dpm remove-active-admin`, wipe, or lock-task
- Setting `DISALLOW_FACTORY_RESET`, `DISALLOW_DEBUGGING_FEATURES`, or other restrictions on a real phone

Emulator-only is the default. If `adb devices` shows a USB serial that is not an emulator (`emulator-5554` and similar), stop and ask.

When in doubt, treat the device as physical and do not touch it.

## Current machine (verified 2026-10-11)

| Item | Status |
| --- | --- |
| OS | Windows 11 (10.0.26200) |
| Android Studio | Installed: 2026.2.1 (`AI-262.9437.185.2621.16467767`) at `C:\Program Files\Android\Android Studio` |
| JDK 17+ | Present but **not on PATH**: Microsoft OpenJDK 21.0.8 at `C:\Program Files\Android\openjdk\jdk-21.0.8`. Studio JBR is OpenJDK 25.0.3. `JAVA_HOME` is unset. |
| Android SDK | Present at `%LOCALAPPDATA%\Android\Sdk` (`C:\Users\Vinay Kalyan Gajula\AppData\Local\Android\Sdk`). `ANDROID_HOME` / `ANDROID_SDK_ROOT` unset. |
| Platforms | `android-36.1`, `android-37.0` |
| Build-tools | `36.0.0`, `36.1.0` |
| adb | Present at `Sdk\platform-tools\adb.exe` (1.0.41 / 37.0.1), **not on PATH** |
| Emulator binary | Present (`emulator` 37.2.12.0) |
| AVD | `Medium Phone API 37.0` — `google_apis_playstore`, x86_64 |
| cmdline-tools / `sdkmanager` | **Missing** |
| System Gradle | **Missing** (use the Gradle Wrapper once the project is scaffolded) |
| Git | 2.53.0 |

Do not install SDKs, JDKs, or cmdline-tools unless the owner asks.

## Build and test (after the project is scaffolded)

From the repo root, with `JAVA_HOME` pointing at JDK 21:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

.\gradlew.bat :app:assembleDebug
.\gradlew.bat test
.\gradlew.bat ktlintCheck detekt
```

Install and run **on the emulator only**:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$emulator = "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe"

& $emulator -list-avds
& $emulator -avd Medium_Phone_API_37.0
& $adb devices
# proceed only if the serial starts with "emulator-"
& $adb -s emulator-5554 install -r app\build\outputs\apk\debug\app-debug.apk
```

CI (once added): GitHub Actions runs `assembleDebug` and unit tests. CI must not talk to a device.

## Code conventions

- Kotlin only. Gradle Kotlin DSL.
- Jetpack Compose for UI. Keep the UI small: status, unlock request, partner setup, debug toggles in debug builds.
- Four modules as in ARCHITECTURE.md: `:app`, `:vpn`, `:blocklist`, `:policy`. No extra modules without a reason.
- Prefer the smallest design that matches the threat model. No Play Store release pipeline, no analytics, no crash reporter that leaves the device.
- Every claim about Android behavior is either cited from current official docs or verified on the emulator. Write `unverified` otherwise.
- No browsing-history log. See logging rules in ARCHITECTURE.md and THREAT_MODEL.md.
- ktlint + detekt on every module. Unit-test blocklist lookup, DNS parse, cooldown math, and policy-intent mapping. VPN packet I/O is tested on the emulator.
- Do not commit secrets, TOTP seeds, partner passphrases, keystore passwords, or signing keys.

## Commit convention

```
<type>: <imperative summary>
```

Types: `feat`, `fix`, `docs`, `test`, `build`, `ci`, `refactor`, `chore`.

Examples: `feat: sinkhole hardcoded DNS names in VpnService`, `docs: add device-owner emulator steps`.

## Documentation map

| File | What it is |
| --- | --- |
| [docs/PLAN.md](docs/PLAN.md) | Milestones M0–M11 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Modules, data flow, threading |
| [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md) | Bypass → mitigation → residual risk |
| [docs/DEVICE_OWNER_SETUP.md](docs/DEVICE_OWNER_SETUP.md) | Emulator and (later) phone provisioning |
| [docs/UNLOCK.md](docs/UNLOCK.md) | Partner-approved unlock, 15-minute window, no fallback |
| [docs/COOLDOWN.md](docs/COOLDOWN.md) | Superseded 48-hour timer; clock reasoning still used |

Do not write app code until the owner approves the plan. After approval, implement **M0 and M1 only**, then stop and show emulator verification.
