# Click Platforms — E2EE Threat Model (internal)

Audience: Click engineers, external security reviewers. Not user-facing.

Last updated: 2026-04-16. Owner: platform security lead.

This document captures **what the end-to-end encryption (E2EE) stack in
`compose.project.click.click.crypto.MessageCrypto` protects against, what it
does NOT, and the explicit trade-offs baked into the design.** It is the
single source of truth when a reviewer asks _"is this really E2EE?"_

---

## TL;DR

- 1:1 and group chat messages are encrypted client-side with **AES-256-CBC +
  HMAC-SHA256 (encrypt-then-MAC)**.
- Keys are **deterministically derived** from connection context
  (`connectionId`, sorted `userIds`, public salt) — not from device-bound
  private keys. Anyone who can observe those three inputs can reconstruct
  the pairwise keys.
- Group master keys are **distributed by sealing them per member over the
  1:1 channel** (same pairwise derivation).
- Hub broadcast messages use a key derived from `hubId` alone. A client
  that knows `hubId` (and can read `hub_messages`) can decrypt.
- Keys live **only in process memory** (`SupabaseChatRepository.chatCryptoCache`);
  they are wiped on sign-out (`clearSessionCaches`). Nothing touches the
  Keystore / Keychain.
- On-device ciphertext-at-rest is NOT covered by this module.

If the threat model you care about requires protection against **a
compromised Click backend**, read the "Known limitations" section before
relying on this scheme.

---

## 1. Primitives

| Role | Primitive | Source |
|------|-----------|--------|
| Symmetric cipher | AES-256 in CBC mode (PKCS#7) | `PlatformCrypto.aesCbcEncrypt/Decrypt` |
| MAC | HMAC-SHA256 | `PlatformCrypto.hmacSha256` |
| Hash / KDF | SHA-256 (chained) | `PlatformCrypto.sha256` |
| Random | CSPRNG via `secureRandomBytes` | OS: `SecRandomCopyBytes` / `SecureRandom` |
| IV length | 16 bytes | random per message |
| Tag length | 32 bytes (HMAC output) | appended before ciphertext |

Wire format for a message (Base64-encoded after the prefix):

```
1:1    "e2e:"      + Base64( IV[16] || HMAC[32] || ciphertext )
Group  "e2e_grp:"  + Base64( IV[16] || HMAC[32] || ciphertext )
```

Encrypt-then-MAC order is chosen intentionally over MAC-then-encrypt
(protects against padding-oracle on CBC) and over GCM (GCM is not yet
available in all our KMP targets' `expect/actual` paths — CBC+HMAC is a
conservative, audited substitute).

## 2. Key derivation

### 2.1 1:1 pairwise keys

```
master  = SHA-256( SALT || sorted(userId_1, userId_2) || connectionId )
encKey  = SHA-256( master || 0x01 )
macKey  = SHA-256( master || 0x02 )
```

- `SALT = "click-platforms-e2ee-v1-2024"` (public, baked into the binary).
- `userIds` are sorted → order-independent derivation.
- No user-typed secret, no device-bound key, no server-held key material.

### 2.2 Group master keys (clique / group chats)

1. Group creator calls `generateGroupMasterKey()` → 32 bytes from the
   platform CSPRNG.
2. For each member, the creator seals the master key as a normal **1:1
   message** (`encryptContent` over the Base64-encoded master bytes) using
   the pairwise key for the `(creator, member)` connection.
3. Each member unwraps it on first receipt, caches the 32-byte master in
   `chatCryptoCache`, and uses `deriveMessageKeysFromGroupMaster(master)`
   for subsequent reads/writes.

Rotation: a new random master is sealed to all members when membership
changes (add/remove). Old masters are retained in memory for backfill
decrypt; they are NOT persisted and NOT zero-filled on logout beyond
`clearSessionCaches()`.

### 2.3 Hub broadcasts

```
master = SHA-256( SALT || "hub-broadcast:" || hubId )
encKey / macKey derived identically to §2.1
```

Geofence / gatekeeper RLS policies on `hub_messages` limit **who can read
or post**; the crypto layer guarantees that the _server operator_ cannot
read content without also knowing `hubId`.

## 3. What this scheme protects against

1. **Passive network observer (ISP, café Wi-Fi, on-path attackers)**
   - TLS already covers transit; layered on top of that, ciphertext is
     opaque to any entity holding only TLS bytes.
2. **Compromised caches of message rows (e.g. dumped Supabase backup)**
   - Row content is `"e2e:" + Base64(IV||MAC||ciphertext)`. Without the
     connection context, rows cannot be decrypted.
3. **Accidental plaintext leaks to logs / analytics**
   - `SupabaseChatRepository` scrubs exception messages via
     `redactedRestMessage()`; no ciphertext is ever logged.

## 4. Known limitations (explicit trade-offs)

### 4.1 Deterministic pairwise keys ⇒ server operator can decrypt
`connectionId`, `userIds`, and `SALT` are all known to the Click backend.
A malicious (or compromised) backend operator — or anyone with read
access to Postgres — can derive the pairwise key for any 1:1 chat and
decrypt.

**Mitigation**: this is a deliberate compromise for UX (no key exchange
UI, no lost-device data loss). The product threat model explicitly
accepts _insider-at-backend_ as a non-goal.

**Possible future hardening**: upgrade §2.1 to derive from an X25519
handshake whose private halves live in `AndroidKeyStore` / iOS
`Keychain`. Would require a key-exchange commit at connection time and
a lost-device recovery story.

### 4.2 No forward secrecy
CBC+HMAC with a single static pairwise key ⇒ compromise of that key
reveals **all** past and future messages in the chat.

**Mitigation**: rotate `SALT` to `v2` on any credible key-leak incident;
old messages become undecryptable but at least new traffic is fresh.
Double-Ratchet is the intended long-term fix.

### 4.3 Group master keys live in memory for session lifetime
`chatCryptoCache` is an in-process `Map<String, ByteArray>` guarded by a
`Mutex` (R0.4). A dumped process heap leaks all master keys the user has
opened this session.

**Mitigation**: `clearSessionCaches()` wipes the cache on logout and on
app foreground-recovery transitions. There is no secure-enclave-backed
storage; this is acceptable because the master keys are already
server-derivable via §4.1.

### 4.4 Hub broadcasts are "anyone with hubId"
§2.3 keys are effectively public: the server enforces access control,
not the crypto layer. This is by design for ephemeral/geo-scoped hubs.

### 4.5 Ciphertext-at-rest on device
This module does NOT handle at-rest DB encryption. If the app uses a
local SQLite / Room cache, those rows may be plaintext. Whole-disk
encryption (enforced at device-unlock on iOS; Android file-based
encryption) is the only defence.

## 5. Salt rotation policy

The constant `E2EE_SALT = "click-platforms-e2ee-v1-2024"` is a version
marker, NOT a secret. Rotate ONLY when:

1. A cryptographic primitive is broken (e.g. SHA-256 weakened) → bump
   primitive + salt together.
2. Evidence of key material leakage (stolen DB dump, CI secret leak).
3. Client-side bug causes key reuse across distinct connections.

Rotation is a **breaking change**: everyone on `v1` cannot decrypt `v2`
content. The planned path is a dual-read window:

- Deploy clients that can decrypt both `v1` and `v2` salts.
- After a soft-launch window, deploy clients that only encrypt `v2`.
- After retention window, deploy clients that no longer decrypt `v1`.

## 6. Test coverage

- `MessageCryptoVectorsTest` — deterministic vectors on the pairwise and
  group KDFs (`click/composeApp/src/commonTest/.../crypto/`).
- `SupabaseChatRepositoryCryptoTest` — asserts that `sendMessage`
  encrypts before `.insert(...)` reaches postgrest and that `fetchMessages`
  decrypts before pushing to UI state.
- `ConnectionViewModelHandshakeTest` — asserts that `cacheEncryptionKeys`
  is invoked before any chat send attempt post-handshake.

If a reviewer adds a new wire-format prefix, it MUST land with a
corresponding test vector — a single golden ciphertext per prefix.

## 7. Open items (non-blocking)

- [ ] Wire `AndroidKeyStore`-backed X25519 for pairwise handshake (§4.1).
- [ ] Double-Ratchet (§4.2) for forward secrecy.
- [ ] Optional at-rest cache encryption once an offline queue lands.

---

Questions / audits: file under `security/` with owner = platform security
lead. Do not copy this file into public-facing docs.
