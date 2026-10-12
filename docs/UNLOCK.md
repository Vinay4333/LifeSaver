# Partner-approved unlock (signed approval)

Approved 2026-10-12, revised the same day. The first design emailed a secret token through the ntfy.sh email gateway. ntfy.sh no longer allows anonymous email (HTTP 400), and any email sender the user controls exposes the token. This design needs **no secret in transit**.

**There is no fallback.** If the partner cannot approve, protection stays on. The accepted back door is still a factory reset (see [THREAT_MODEL.md](THREAT_MODEL.md)).

## Idea

The partner holds a private key. The phone stores only the partner's **public** key. Each request carries a fresh random request id. Pausing protection requires a valid signature from the partner's key over that request. Anyone may see the request, the link, the relay and the signature: none of it helps without the private key.

- Algorithm: ECDSA P-256 with SHA-256. Chosen because both WebCrypto (the approve page) and Android's JCA support it.
- Public key: the 65-byte uncompressed point, base64url, no padding (87 characters).
- Fingerprint: first 8 bytes of SHA-256 of the public key, as `A1B2-C3D4-E5F6-0718`.
- Signature: 64 bytes `r || s` (what WebCrypto produces), base64url. The phone converts it to DER to verify.
- Signed message (UTF-8): `lifesaver/v1|<purpose>|<requestId>|<extra>`, where `<extra>` is the new public key for a partner change and empty otherwise.
- Purposes: `pair`, `unlock`, `partner`.

## Pairing (once)

1. The **partner** opens the approve page on their own device and taps "Become a partner". The page creates the key in their browser and shows the public key and fingerprint. It also offers a backup of the private key.
2. The partner sends the user the public key.
3. The user pastes it into the app. The app shows the fingerprint, to compare with the partner out loud or by text, and optionally takes the partner's email for the draft.
4. The app creates a `pair` request. The user sends it to the partner. The partner presses Approve, which signs it.
5. The phone verifies the signature against the pasted key. Only then is the partner saved. Until then nothing can be unlocked.

Limit that software cannot close: if the **user** makes the key, the user is their own partner. The setup text says the key must be made on the partner's device.

## Unlock

1. "Request unlock" makes a request id and a random relay topic, with no network call. The app offers **Share** (Android share sheet) and **Email** (a draft to the optional address). The user sends the link. Nothing secret is in it.
2. The partner opens the link. The page checks that its key matches the fingerprint the request names, shows what is being asked, and on **Approve** signs the request and posts `purpose.requestId.signature` to the relay topic.
3. While the request is pending, the VPN service polls the topic every few seconds. A message for the pending request id with a valid signature opens a **15-minute window**. Anything else is ignored.
4. An unapproved request expires after 24 hours of device-on time. The user can cancel it.

During the window the VPN service keeps running and forwards DNS without blocking, and the content guard stays quiet. When it ends, blocking resumes by itself. The user may re-lock early. There is no Stop button.

The window and expiry use `SystemClock.elapsedRealtime()` and the boot count, never the wall clock. A reboot closes the window and drops a pending request.

## Changing the partner

A `partner` request: the **current** partner signs a message that includes the new public key, so the new key cannot be swapped after signing. The same pairing steps apply to the new partner's own device and fingerprint check, but the signature that counts is the current partner's.

## Links

```
https://vinay4333.github.io/LifeSaver/approve/#p=unlock&r=<requestId>&topic=<topic>&relay=<relay>&fp=<expected fingerprint>[&n=<new key>]
lifesaver://approve?p=unlock&r=...&topic=...&relay=...&fp=...
```

Nothing secret rides in the link, so the fragment is only for tidiness. The page runs no third-party scripts. A mail scanner that prefetches the link cannot approve anything: signing needs the partner's key and a button press.

## Trust and limits

- **Key loss means lockout.** No fallback. The page offers a private-key backup for the partner to keep somewhere safe. A lost key and no backup means only a factory reset.
- **Who holds the key is a human matter** (see pairing).
- **Relay (ntfy.sh)** carries only public data. It can delay or drop an approval; it cannot forge one. Publishing anonymously to a topic works (tested). Needs network: offline means no approval.
- **The partner's browser** stores the private key in `localStorage`. Anyone with that browser profile can sign. Out of scope.
- **Names resolved during the window stay cached** by the system for their TTL after it closes, and open connections continue. Unverified how long.
- `Settings.Global.BOOT_COUNT` on the Galaxy A15 is **unverified**. The code also treats `elapsedRealtime` going backwards as a reboot.
- adb or a debugger on the phone, root, or a flash are out of scope here. M9 closes adb. A factory reset wipes everything.
- Debug builds only: `-PntfyBase=...` points the relay at a local test server.

## Stored values (SharedPreferences `unlock`)

`partnerKey` (public), `partnerContact` (optional email), `pendingPurpose`, `pendingId`, `pendingTopic`, `pendingKey`, `pendingContact`, and the timers `pending*` / `window*` each with `Until`, `At`, `Boot`. No private key and no secret on the user's phone.
