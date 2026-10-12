// Partner-side crypto for the LifeSaver approve page. ECDSA P-256 / SHA-256 through WebCrypto.
// Formats match the Android app: public key = raw uncompressed point, signature = r||s, both
// base64url without padding. No storage, no network here, so Node can test it.

const subtle = globalThis.crypto.subtle;

export function b64u(buffer) {
  const bytes = new Uint8Array(buffer);
  let text = "";
  for (const b of bytes) text += String.fromCharCode(b);
  return btoa(text).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function unb64u(text) {
  const padded = text.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((text.length + 3) % 4);
  const raw = atob(padded);
  return Uint8Array.from(raw, (c) => c.charCodeAt(0));
}

/** A new private key as a JWK (extractable, so it can be backed up). */
export async function generateKey() {
  const pair = await subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
  return subtle.exportKey("jwk", pair.privateKey);
}

/** Raw uncompressed public point from a private JWK, base64url. This is what the user pastes into the phone. */
export function publicKeyOf(jwk) {
  const x = unb64u(jwk.x);
  const y = unb64u(jwk.y);
  const raw = new Uint8Array(65);
  raw[0] = 4;
  raw.set(x, 1);
  raw.set(y, 33);
  return b64u(raw);
}

/** `A1B2-C3D4-E5F6-0718`, the same as the app shows. */
export async function fingerprintOf(publicKey) {
  const digest = new Uint8Array(await subtle.digest("SHA-256", unb64u(publicKey)));
  const hex = [...digest.slice(0, 8)].map((b) => b.toString(16).padStart(2, "0").toUpperCase()).join("");
  return hex.match(/.{4}/g).join("-");
}

export function messageFor(purpose, requestId, extra) {
  return `lifesaver/v1|${purpose}|${requestId}|${extra || ""}`;
}

/** r||s, base64url. WebCrypto already returns this form. */
export async function sign(jwk, message) {
  const key = await subtle.importKey("jwk", jwk, { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"]);
  const signature = await subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, new TextEncoder().encode(message));
  return b64u(signature);
}

/** Reads the request out of a link fragment or query. Returns null if anything is missing. */
export function parseRequest(fragment) {
  const params = new URLSearchParams(fragment.replace(/^[#?]/, ""));
  const purpose = params.get("p");
  const request = {
    purpose,
    id: params.get("r"),
    topic: params.get("topic"),
    relay: params.get("relay"),
    fingerprint: params.get("fp"),
    newKey: params.get("n") || "",
  };
  const ok =
    ["pair", "unlock", "partner"].includes(purpose) &&
    request.id &&
    request.topic &&
    request.fingerprint &&
    /^https?:\/\//.test(request.relay || "") &&
    (purpose !== "partner" || request.newKey);
  return ok ? request : null;
}
