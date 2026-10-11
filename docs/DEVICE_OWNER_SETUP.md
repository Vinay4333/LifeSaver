# Device owner setup

**Provisioning a physical phone factory-resets it.** Accounts, photos, 2FA apps, SMS, and Play data on that phone are gone unless you backed them up yourself. Do not run these steps on the real phone until you decide to, and never ask an agent to run `adb` against it.

Device owner can be set **only on an unprovisioned device**. If `Settings.Secure.USER_SETUP_COMPLETE` has ever been set, Android treats the device as provisioned and refuses a new device owner. The official rule: factory reset first ([AOSP provision](https://source.android.com/docs/devices/admin/provision), [device management overview](https://source.android.com/docs/devices/admin)).

If provisioning **fails**, Android may factory-reset the device again ([DevicePolicyManager](https://developer.android.com/reference/android/app/admin/DevicePolicyManager) managed-provisioning notes).

Agents: emulator only. If `adb devices` shows anything that is not `emulator-*`, stop.

## What you get as device owner

- `setAlwaysOnVpnPackage` + lockdown, no consent dialog
- `setUninstallBlocked`
- `setUserControlDisabledPackages`
- `addUserRestriction` for VPN, Private DNS, debugging, safe boot, Settings factory reset, date/time, extra users
- You cannot remove the owner from Settings. The supported way out is a factory reset (and recovery still works)

## Emulator (the only path an agent may run)

Google's test instructions ([Test device management](https://source.android.com/docs/devices/admin/testing-setup), updated 2026-09-22) are written for TestDPC. We do the same sequence with LifeSaver.

### 0. Use a dedicated AVD

The current AVD `Medium Phone API 37.0` is `google_apis_playstore`. Play images often create a Google account during setup, and `dpm set-device-owner` then fails with "already has an account" or "not allowed". **Unverified on this exact image** until M8.

Recommended M8 AVD (create in Android Studio, or after cmdline-tools are installed):

- Image: **Google APIs** (not Play Store) or AOSP, API 36.1
- No Google account
- Name: `LifeSaver_DO_API36`

Wipe data on that AVD whenever you need a clean owner set.

### 1. Cold boot, stay in the setup wizard if you can

1. Wipe the DO AVD.
2. Start it. Prefer not to add any account.
3. Enable USB debugging if the wizard already finished (emulator debugging is on by default).

Confirm no accounts: Settings → Accounts should be empty.

### 2. Confirm adb sees only emulators

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
```

Expected: `emulator-5554` (or similar) `device`. If a physical serial appears, **do not continue**.

### 3. Install the hardened (or debug) APK

```powershell
& $adb -s emulator-5554 install -r path\to\app-debug.apk
```

The manifest must declare a `DeviceAdminReceiver` with `BIND_DEVICE_ADMIN` and `android.app.device_admin` metadata ([DeviceAdminReceiver](https://developer.android.com/reference/android/app/admin/DeviceAdminReceiver)).

### 4. Set device owner

Component name will be decided at implementation (example only):

```powershell
& $adb -s emulator-5554 shell dpm set-device-owner app.lifesaver/.policy.LifeSaverAdminReceiver
```

This is the same shape as the official TestDPC command:

```text
adb shell dpm set-device-owner com.afwsamples.testdpc/.DeviceAdminReceiver
```

Success looks like `Success: Device owner set to package ComponentInfo{...}`.

Typical failures:

| Message | Meaning |
| --- | --- |
| `Already has an account` / `not allowed` | Remove accounts or wipe the AVD |
| `Unknown admin` | Wrong component name or receiver not in the installed APK |
| `Trying to set the device owner, but device is already provisioned` | Wipe AVD data and try again before finishing setup |

### 5. Finish the wizard if it is still up

Then verify:

- Settings → Security → Device admin apps: LifeSaver is listed and **cannot be unchecked**
- App → Uninstall is disabled
- LifeSaver UI reports `isDeviceOwnerApp == true`
- After reboot, always-on VPN is up without tapping Start (once M8 code is in)

To start over: wipe AVD data. Do not use `dpm remove-active-admin` on a physical phone. On an emulator debug build with `android:testOnly="true"`, Google documents `dpm remove-active-admin` as a test-only escape.

## Real phone (owner-operated, not agent-operated)

**This section is documentation only. It will wipe the phone.**

Do this yourself, on a weekend, after a backup you have restored on another device (password manager, authenticator, photos).

### Before you wipe

1. Write down or move: authenticator apps, SMS 2FA, Signal PIN, WhatsApp backup, photos.
2. Decide whether this phone may lose Play Integrity / banking-app comfort. Some banks dislike device-owner / "managed device" bits. **Unverified per bank.**
3. Confirm the phone's Android version (14, 15, or 16). LifeSaver targets 26+ / target 36.
4. Unlock bootloader is **not** required and is a worse residual risk. Do not unlock it.
5. You will need a second computer only if you choose the adb method. QR does not need adb after the APK is hosted.

### Method A — adb on a freshly wiped phone (common for a personal DO)

Official test flow is this sequence ([testing-setup](https://source.android.com/docs/devices/admin/testing-setup), TestDPC README):

1. Backup.
2. Settings → factory reset (this is *your* action).
3. Boot. **Stop on the first setup screen.** Do not add a Google account.
4. Enable debugging only long enough to install (OEM-specific: skip Wi-Fi, tap build number on a setup screen — this varies; **unverified per OEM**). Many people connect Wi-Fi, skip account, finish setup, enable developer options, then run `dpm` **before adding an account**. The AOSP rule is still: if setup-complete and accounts exist, `set-device-owner` fails. Prefer: install + `dpm` before any account.
5. From your PC (you type this, not an agent):

```text
adb devices
# confirm ONE phone serial, and that you intend to provision THIS phone
adb install -r lifesaver.apk
adb shell dpm set-device-owner app.lifesaver/.policy.LifeSaverAdminReceiver
```

6. Finish setup. Add the Google account only after owner is set, if you still want Play.
7. Immediately let LifeSaver apply restrictions (M9), which includes `DISALLOW_DEBUGGING_FEATURES`. Then turn developer options off if they are still visible.
8. Reboot. Confirm VPN + owner.

If `dpm` refuses because setup is complete: you must factory-reset and try again. There is no "promote this installed app to owner" on a used phone.

### Method B — QR provisioning (no adb after hosting the APK)

Android 7+ setup wizard: tap the welcome screen **six times**, connect Wi-Fi, scan a QR payload ([Play EMM provision docs](https://developers.google.com/android/work/play/emm-api/prov-devices)).

The DPC must implement `ACTION_GET_PROVISIONING_MODE` and `ACTION_ADMIN_POLICY_COMPLIANCE` or provisioning fails ([AOSP provision](https://source.android.com/docs/devices/admin/provision)). We will add those activities in M8 if we want QR. Until then, Method A is enough for one personal phone.

Example payload shape (TestDPC):

```json
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "app.lifesaver/app.lifesaver.policy.LifeSaverAdminReceiver",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "https://example.invalid/lifesaver.apk",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": "<base64-url SHA-256 of the signing cert>"
}
```

Host the APK somewhere you control. Wrong checksum → provisioning fails → possible wipe.

NFC provisioning exists but needs tags; Android 10+ dropped Beam. Not worth it for one phone.

### Aftercare on the real phone

- Confirm you can still place calls, use your bank app, and receive SMS **before** you depend on the device.
- Keep a compiled APK and the keystore offline. If the app is deleted by a wipe, you need them to provision again.
- To leave device-owner mode: Settings factory reset (if we did **not** set `DISALLOW_FACTORY_RESET`) or recovery wipe. If we did set that restriction, Settings will refuse — use recovery, or temporarily clear the restriction from a partner-approved unlock build.

## What this document will not do

- No command in this file should be run against a physical serial by an agent.
- No `fastboot oem unlock`.
- No "just try dpm on the phone I have plugged in."
