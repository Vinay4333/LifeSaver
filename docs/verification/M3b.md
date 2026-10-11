# M3b verification: content guard (text-only)

Emulator `emulator-5554`, debug build. Date: 2026-10-11. No physical device.

## What it does

`ContentGuardService` (AccessibilityService, `canRetrieveWindowContent`) is limited to Reddit (`com.reddit.frontpage`), Discord (`com.discord`) and a few browsers. On each (debounced) event it reads the **visible** node text and descriptions and runs `NsfwTextMatcher`. A match sends the user to the home screen and shows a toast. Nothing read is stored or logged. Only the match category (for example `NsfwWord`) goes to logcat, and a counter is kept.

Matched labels: the word `nsfw`, `18+`, phrases such as "age-restricted", "must be 18", "mature content", "not safe for work", and subreddit names containing explicit terms (`r/...porn...`).
Browsers only count when the screen also shows `reddit.com`, `redd.it`, `discord.com`, `discord.gg` or `discordapp.com`.

## Results so far (emulator)

| Test | Result |
| --- | --- |
| Unit tests for the matcher (positives, negatives, browser host rule) | Pass |
| Enable the service (`settings put secure enabled_accessibility_services ...`, emulator only) | Listed as enabled in `dumpsys accessibility` |
| Chrome opens a harmless local page with no trigger text | Stays in Chrome, no log line |
| Chrome opens a local page showing `reddit.com` and `NSFW` | Sent to the launcher. Log: `ContentGuard: close: NsfwWord` |
| Reddit app / Discord app | **Not tested**: not installed on the emulator image |
| Real Reddit / Discord NSFW screens | **Not tested** |
| Images with no text label | Not detected by design |

## Manual test steps (owner)

1. Install the debug APK (emulator, or the phone only when you say so in a chat turn).
2. Open LifeSaver. The "Content guard" row says Off. Tap "Open accessibility settings".
3. Find "LifeSaver content guard" (it can be under Installed apps / Downloaded apps). On Android 13+ a sideloaded app is greyed out: open Settings → Apps → LifeSaver → ⋮ → "Allow restricted settings", then return.
4. Turn it on and accept the warning. Back in LifeSaver the row says On.
5. Reddit app: open a post or subreddit that Reddit tags NSFW (or an 18+ community gate). Expected: back to the home screen with the toast "LifeSaver closed this screen". The row counter goes up.
6. Discord app: open an age-restricted channel. Same expectation.
7. Chrome: open `reddit.com` on an NSFW subreddit. Same expectation.
8. False-positive check: browse normal Reddit and Discord for a few minutes (home feed, an ordinary subreddit, a normal server). Note any wrongful closes, and which text caused them.
9. Evasion check: note any NSFW post you can still view (unlabeled image, a subreddit with a plain name). Those are the gaps.
10. Optional: `adb logcat -s ContentGuard` shows only `close: <category>` lines.

If misses are common, the next step is blocking the apps (and Reddit/Discord domains) outright.
