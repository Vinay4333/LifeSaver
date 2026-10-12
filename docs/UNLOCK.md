# Partner-approved unlock

Approved 2026-10-12. This replaces the 48-hour cooldown in [COOLDOWN.md](COOLDOWN.md). **There is no fallback.** If the partner cannot approve, protection stays on. The accepted back door is still a factory reset (see [THREAT_MODEL.md](THREAT_MODEL.md)).

## What "unlock" means

Not "stop the VPN". Approval opens a **15-minute window** in which filtering is suspended: the VPN service keeps running (so there is nothing to restart and no foreground-service start restriction to hit), DNS is forwarded without blocking, and the content guard does not close screens. When the window ends, blocking resumes by itself. The user may re-lock early. There is no Stop button in any build.

The window is measured on `SystemClock.elapsedRealtime()` with the boot count, never the wall clock. A reboot closes the window and drops any pending request.

## Flow

1. **Setup (once).** The user enters the partner's email. The app also creates a random *approval topic*. Setup is allowed only while no partner is set.
2. **Request.** The user taps "Request unlock". The phone makes a random token `T`, stores only `SHA-256(T)` and an expiry (24 h of device-on time), and sends the partner an email through the ntfy.sh email gateway. The email goes to a fresh random topic and contains a link carrying `T`. The phone then forgets `T`. The user never sees the email or `T`.
3. **Approve.** The partner opens the link, which shows who is asking and what for, and presses **Approve**. That posts `purpose.T` to the approval topic. The partner can instead paste the link into the LifeSaver app on their own phone, or open the `lifesaver://approve?...` link there. The approve screen in the app does the same post.
4. **Receive.** While a request is pending, the VPN service polls the approval topic every few seconds. A message whose token hashes to the stored value, with the right purpose, is accepted once. The window opens for 15 minutes.
5. **Expire.** An unapproved request expires after 24 hours of device-on time. The user can cancel it. Either way the link stops working.

Why the user cannot forge approval: the phone never holds `T` after sending it, only its hash. Reading the app's storage (a debug build allows `run-as` while USB debugging is on) yields the hash, not the token.

## Changing the partner

Changing the partner is its own request with purpose `partner`. It is emailed to the **current** partner, and the new address is applied only after that partner approves. The approval topic is replaced at the same time, so old links die. This closes "swap the partner for my own address during an unlock window".

## Link format

```
https://vinay4333.github.io/LifeSaver/approve/#p=unlock&t=<T>&topic=<approval topic>&relay=https%3A%2F%2Fntfy.sh
lifesaver://approve?p=unlock&t=<T>&topic=<approval topic>&relay=https%3A%2F%2Fntfy.sh
```

`T` sits in the URL fragment, so the static page host never receives it. The static page is `docs/approve/index.html` (GitHub Pages, source `main` / `docs`). It has no scripts from third parties. A plain `GET` link would be unsafe because mail scanners prefetch links; the page needs a button press, which a scanner does not make.

## Trust and limits

- **ntfy.sh sees the token and the partner's email address.** The operator could approve a request. Self-hosting is out of scope. Topics are 128-bit random.
- **Email delivery is best effort.** It can be slow, rate limited, or land in spam. If no email arrives the request just waits. No fallback by design.
- **Needs network.** Airplane mode means no approval. A request cannot complete offline.
- **Names resolved during the window stay cached** by the system for their TTL after the window closes, and open connections continue. Unverified how long.
- **Wall clock is never used** for expiry or the window. `Settings.Global.BOOT_COUNT` on the Galaxy A15 is **unverified**. The code also treats `elapsedRealtime` going backwards as a reboot.
- Anyone who can run `adb` or read process memory at the instant of the request, or flash the phone, is out of scope here. M9 closes adb. A factory reset wipes everything.
- Debug builds only: the relay base URL can be pointed at a local test server with `-PntfyBase=...`. Default builds use `https://ntfy.sh`.

## Stored values (SharedPreferences `unlock`)

`partnerEmail`, `approvalTopic`, `pendingHash`, `pendingPurpose`, `pendingNewEmail`, `pendingUntil/Boot/At`, `windowUntil/Boot/At`. No token, no browsing data. `allowBackup` is already false.
