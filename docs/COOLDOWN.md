# Cooldown

Approved duration: **48 hours of device-on time**.

This is not 48 hours on the wall clock. It is 48 hours during which the phone has been powered on since the unlock request, including time the screen is off and the CPU is asleep. Time the phone is fully powered off does not count.

M5 implements this. M0 and M1 do not have an unlock timer. Debug builds, when M5 exists, use a 2-minute timer so the emulator test does not take two days. Hardened builds do not have a shorter timer.

## Why not the clock

`System.currentTimeMillis()` follows the clock in Settings. Setting the date two days ahead would finish a wall-clock timer immediately. `DISALLOW_CONFIG_DATE_TIME` (M9) hides that setting, and it is still the wrong clock to trust: a reboot, a time-zone change, or a network time update can move it.

`SystemClock.elapsedRealtime()` is a count of milliseconds since boot. It keeps moving while the phone sleeps. It does not jump when the date changes. It goes back to zero on reboot.

`SystemClock.uptimeMillis()` stops during deep sleep. A phone that sleeps most of the night would barely advance that timer, so "48 hours" would become many calendar days. That is more friction than the request asked for. Use `elapsedRealtime`.

## What gets stored

- `requiredOnMillis` — `48L * 60 * 60 * 1000` in hardened builds
- `accumulatedOnMillis` — how much on-time has already been counted for this request
- `anchorElapsedRealtime` — the `elapsedRealtime` value at the last sample
- `anchorBootCount` — `Settings.Global.BOOT_COUNT` at the last sample

The wall clock may be stored as a label in the UI ("requested Tuesday"). It is never an input to "is the wait over?".

## How a tick works

1. Read `elapsedRealtime` and `BOOT_COUNT`.
2. If the boot count is unchanged, add `elapsedRealtime - anchor` to `accumulated`, then move the anchor to now.
3. If the boot count changed, keep `accumulated` as it was. Do not invent time for the gap. Set the new boot count and the new anchor. The phone was off, or it rebooted; neither should be trusted as elapsed progress.
4. The request is done when `accumulated >= required`.

Concrete case. You request unlock at 23:00, leave the phone charging overnight (asleep, not off), and it stays on until 23:00 the next night. That is about 24 hours of the 48. You then power it off for a week. The week adds nothing. You turn it on and use it for another 24 hours of on-time. The request completes. Setting the date to next year at step one does not change the result.

## Cancel and re-arm

Cancelling a request throws away `accumulated`. Starting again starts at zero. There is one active request, not a bank of partial waits.

Re-enabling protection clears the request.

## What this cannot do

- It cannot count time the phone is off. A genuine unlock takes longer if the phone spends days powered down. That is the trade for not trusting the clock.
- It cannot survive a factory reset. App storage is gone. That is the accepted back door.
- It cannot stop someone who can change `elapsedRealtime`. That means root or a custom ROM, which is out of scope.
- A partner TOTP (M11, not built yet) is the way to skip the wait without lying about the clock. Until that exists, the only skip is the debug build's short timer, and that timer must not ship in a hardened build.

`Settings.Global.BOOT_COUNT` is a public setting. Whether every OEM, including this Samsung build, updates it on every reboot is **unverified** until M5 on the emulator and, later, on the phone.
