# `media/` — Chat audio playback & secure media pipeline

> **Anti-doomscrolling · Stop scrolling, start living.**  
> The `media/` package owns **platform media playback** for chat. Encryption, vault storage, and upload live in `util/`, `crypto/`, and `data/api/ChatApiClient` — this module is the **playback surface** after decryption.

---

## Purpose

| Responsibility | Owner |
|----------------|-------|
| Voice message **playback** UI state | `media/ChatAudioPlayer.kt` |
| Profile photo **vault** (disk cache) | `util/ProfileMediaVault.kt` |
| Chat attachment **vault** (decrypted files) | `util/ChatMediaVault.kt` |
| Outbound image **compression** | `util/ChatOutgoingImageCompression.kt` |
| Upload/download **HTTP** | `data/api/ChatApiClient.kt` |
| Encrypt/decrypt **bytes** | `crypto/`, `chat/attachments/AttachmentCrypto.kt` |

`media/` is intentionally thin: one `expect` composable factory and a small player interface.

---

## Architecture

### Inbound media path (chat)

```
ChatApiClient.downloadUrlBytes(url)
        │
        ▼
AttachmentCrypto.decrypt(...)
        │
        ▼
util/writeChatMediaVaultFile(messageId, bytes, ext)  →  file:// URI
        │
        ├──► media/rememberChatAudioPlayer(localFilePathForPlayback)
        ├──► ui/chat/ChatPhotoBubble (image bitmap cache)
        └──► ui/chat/ChatAttachmentBubble (files)
```

### Outbound media path

```
ui/chat/ChatMediaPickers / ChatFilePickers
        │
        ▼
util/ChatOutgoingImageCompression (images)
        │
        ▼
AttachmentCrypto.encrypt(...)
        │
        ▼
ChatApiClient encrypted multipart upload
        │
        ▼
Message row with encrypted URL + metadata
```

### `ChatAudioPlayer`

```kotlin
interface ChatAudioPlayer {
    val isPlaying: Boolean
    val positionMs: Long
    val durationMs: Long
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun dispose()
}

@Composable
expect fun rememberChatAudioPlayer(
    mediaUrl: String,
    durationHintMs: Long = 0L,
    localFilePathForPlayback: String? = null,
): ChatAudioPlayer
```

| Parameter | Use |
|-----------|-----|
| `mediaUrl` | Remote URL fallback (rare after vault write) |
| `durationHintMs` | iOS duration when player reports 0 initially |
| `localFilePathForPlayback` | **Preferred** — decrypted vault path |

**Android actual** — `MediaPlayer` / ExoPlayer bridge in `androidMain`.  
**iOS actual** — `AVAudioPlayer` bridge in `iosMain`.

Rendered by `ui/chat/ChatAudioBubble.kt` with waveform/scrubber UI.

### Profile media vault (`util/ProfileMediaVault.kt`)

Profile avatars and cover media use a **separate vault** from chat:

```kotlin
fun profileMediaVaultId(cacheKey: String): String  // FNV-style hash
expect fun readProfileMediaVaultBytes(vaultId, extension): ByteArray?
expect fun writeProfileMediaVaultBytes(vaultId, bytes, extension): Boolean
expect fun profileMediaVaultLocalPath(vaultId, extension): String?
```

Downloaded profile images decrypt once, then serve from disk on subsequent profile sheet opens.

### Chat media vault (`util/ChatMediaVault.kt`)

| API | Role |
|-----|------|
| `chatMediaVaultDirectory()` | Platform cache dir |
| `writeChatMediaVaultFile` | Post-decrypt persist |
| `readChatMediaVaultBytes` | Re-read without network |
| `chatMediaVaultLocalPath` | Path for player/gallery |
| `chatMediaVaultExtensionForMessage` | Extension from mime/url |

Extensions cover images, audio, video, documents (pdf, docx, etc.).

### Dispatcher

`util/ChatMediaDispatcher.kt` — dedicated dispatcher for encrypt/decrypt/upload to avoid blocking UI thread.

---

## Constraints

1. **Never play encrypted bytes directly** — always decrypt to vault first.
2. **Dispose players** — `ChatAudioPlayer.dispose()` on bubble leave; avoid leaking platform audio handles.
3. **Vault is cache, not backup** — cleared on app uninstall; messages re-download from server URLs.
4. **Large files** — file pickers validate via `ChatAttachmentValidator` before encrypt.
5. **iOS duration hint** — required for slider UX when `AVAudioPlayer` delays duration reporting.
6. **Secure temp audio** — `ui/chat/SecureChatAudioFiles.kt` manages recording temp files separately from vault.

---

## Related files

| Path | Role |
|------|------|
| `media/ChatAudioPlayer.kt` | expect player + interface |
| `androidMain/.../ChatAudioPlayer.android.kt` | Android actual |
| `iosMain/.../ChatAudioPlayer.ios.kt` | iOS actual |
| `util/ChatMediaVault.kt` | Chat attachment disk cache |
| `util/ProfileMediaVault.kt` | Profile image disk cache |
| `util/ChatOutgoingImageCompression.kt` | Outbound image size limits |
| `util/ChatMediaDispatcher.kt` | IO dispatcher |
| `util/LruMemoryCache.kt` | In-memory bitmap LRU (chat images) |
| `data/api/ChatApiClient.kt` | Upload/download HTTP |
| `viewmodel/ChatViewModel.kt` | Orchestrates media pipeline |
| `ui/chat/ChatAudioBubble.kt` | Voice message UI |
| `ui/chat/ChatPhotoBubble.kt` | Image bubble |
| `ui/chat/ChatAttachmentBubble.kt` | Generic file bubble |
| `ui/chat/ChatMediaPickers.kt` | Gallery/camera pick |
| `ui/chat/VoiceMessageRecordDialogLayout.kt` | Record UI |
| `viewmodel/SecureChatMediaHost.kt` | Secure preview host composable |

---

## What Click Users Experience

### Connect in person / Scan QR / Group connect
No media until chat exists.

### Private encrypted chat
Foundation for all media features — messages are E2EE; media ciphertext stored on server.

### Send photos / files / voice notes
**Core media UX.**

- **Photos** — pick from gallery or camera, compress, encrypt, upload; recipients see inline photo bubbles with tap-to-expand (`GlassFullscreenMediaOverlay`).
- **Files** — document picker, encrypted upload, filename + size in attachment bubble; save to downloads via `ChatAttachmentSaver`.
- **Voice notes** — hold-to-record dialog, encrypt, upload; recipients play via `ChatAudioPlayer` with scrubber.

### Emoji reactions / Typing & read receipts
Not media playback.

### Voice & video calls
**Live calls** use LiveKit (`calls/`) — separate from async voice **messages** in this module.

### Memory Capsules
May include photos attached to capsule metadata — uses same crypto pipeline when in chat context.

### 48-hour gentle archive
Archived chats retain vault cache until cleared; media may require re-download if cache evicted.

### Connection map & timeline / Rate the vibe / QR / Availability / Match alerts
Unrelated to media playback.

### Community Hubs / Map beacons / Global search
Hub chat may support lighter media — primarily 1:1 E2EE in `ChatViewModel`.

### Core connections / Collaboration / disposable rolls
**Disposable camera** captures photos in `ui/camera/` — processed by `DisposableRollFilterProcessor`, uploaded via collaboration flow (related pipeline, separate entry).

### Ghost mode
Media send/download requires network; ghost mode blocks background sync not already-cached playback.

### Block & report
Blocked users' media URLs should not be fetched.

### Profile & interests
**Profile media vault** caches avatar/cover for fast `ProfileBottomSheet` render.

### Onboarding / Auth / Push / Deep links / Web / Business
Infrastructure.

### Event reminders
Unrelated.

### Achievements & stats
Unrelated.
