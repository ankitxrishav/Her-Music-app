# Her Music — Full Bug Audit & Fix Brief (for a coding agent)

**Repo:** `ankitxrishav/Her-Music-app` (Android, Kotlin, Jetpack Compose, Media3, Hilt)
**Audit type:** static code review of the cloned repo (commit `6e3a8a6`). Nothing was compiled or run on a device. Where something depends on runtime behaviour I mark it **[VERIFY]**.
**Scope read in full:** `couple/*`, `auth/CoupleAuthGuard.kt`, `playback/DownloadUtil.kt`, `AudioExportService.kt`, `ExoDownloadService.kt`, `QualityBadge.kt`, `utils/YTPlayerUtils.kt` (playback function), `utils/InnerTubeXResolver.kt` (head), `utils/LocalFileDownloader.kt`, `ui/screens/downloads/DownloadsViewModel.kt`, relevant parts of `CoupleSpaceScreen.kt`, `CoupleChatView.kt`, `CouplePlayTogetherCard.kt`, `MainActivity.kt`, `SongMenu.kt`, `PlayerSettings.kt`, manifest, repo root.
**Not reviewed:** Listen Together (`listentogether/*`), lyrics providers, Discord, AI playlists, equalizer, Cast, the rest of the UI. Do not assume they are clean.

All paths below are relative to `app/src/main/kotlin/com/fenrir/her/` unless stated.

---

## 0. TL;DR (what is actually broken)

1. **"Hi-Res / Lossless FLAC download" is not real.** The source is always YouTube Opus/AAC (~128–256 kbps). The export service transcodes it to FLAC/MP3-320/AAC-320 with ffmpeg. That produces a bigger file with **zero** quality gain (and generation loss for MP3/AAC). The UI text ("bit-perfect", "FLAC 24-bit", "CD") is false.
2. **Export (the only path that makes real files) fails or corrupts for most songs.** `YTPlayerUtils.playerResponseForPlayback` returns `contentLength = null`, so `AudioExportService` falls back to a fake 10 MB length, splits it into 4 ranges, and the later ranges start past EOF for any song under ~7.5 MB (HTTP 416 → whole export fails), or the file is zero-padded / truncated.
3. **Exports silently fail and kill each other.** No user-visible error, no foreground service, and the first finished export calls `stopSelf()` which cancels all other in-flight exports.
4. **The normal "Download" button saves into ExoPlayer's private cache, not a visible file.** The Downloads screen scans `Music/Her` which nothing writes to. Result: "I downloaded but nothing is there".
5. **Download quality setting is ignored** (`AudioQuality.OPUS` hardcoded), and the stream-URL cache never hits (expiry is computed wrong), so every chunk re-resolves the URL over the network.
6. **Her/Him tab:** "Send to Partner" only writes to the local phone. Radio broadcast and heart bursts never reach the partner. Live Sync stops when you leave the tab but the button still says "Connected". Push-song notifications only fire while the Couple screen is open. Chat can drop/duplicate messages. Daily-question answers never match across time zones.
7. **Security:** the Firebase Realtime DB is accessed with unauthenticated REST from a URL that is public in the repo, pair codes are 4 hex chars, and chat/playback is also published to public `ntfy.sh` topics derived from those codes. Anyone can read or wipe every couple's data.

---

## 1. Bug register

Severity: **C** = critical (feature broken / data exposure), **H** = high, **M** = medium, **L** = low.

### 1A. Her & Him (couple) tab

| ID | Sev | Where | Symptom | Root cause |
|---|---|---|---|---|
| C1 | C | `couple/CoupleSongShareHelper.kt` L249–283; used by `ui/menu/YouTubeSongMenu.kt` L374, `CoupleChatView.kt` L418 | "Send to Partner" shows toast "Sent … with love ❤️" but partner never gets it | `shareSong()` only edits local DataStore (`CoupleChatMessagesKey`, `CoupleSharedSongsKey`). It never calls `FirebaseCoupleSync.sendChatMessage` or `pushSong`. |
| C2 | C | `CoupleSpaceScreen.kt` ~L640–720; `FirebaseCoupleSync.fetchRadioBroadcast` | "Go Live" radio and "Tune in", heart bursts do nothing for partner | `fetchRadioBroadcast` is **never called anywhere**. `broadcastJson`/`heartBurstEpochMs` are local prefs; the heart handler only writes local prefs. |
| C3 | C | `CouplePlayTogetherCard.kt` (`val scope = rememberCoroutineScope()`, passed to `startLiveSyncFollow`); `CouplePlaybackSyncManager.kt` L128–194 | Live Sync works only while Couple tab is on screen; after navigating away it stops but button still shows "🟢 Connected" | Follow job is launched in the composable's scope (cancelled on leave). `_isLiveSyncing` is a singleton `MutableStateFlow` that is never reset when the job dies. |
| C4 | H | `CouplePlaybackSyncManager.kt` L159–162 | Follower restarts the song repeatedly / audio stutters when partner changes track | If `currentMeta.id != partner.songId`, it calls `playQueue(...)` then `delay(1000)`; the loop runs every ~1.1 s. Resolving a stream can take >2 s, so it re-queues again and again. |
| C5 | H | `CouplePlaybackSyncManager.kt` L164–183 | After partner closes the app, follower seeks forward forever / skips tracks | Last written state has `isPlaying=true`. `elapsed = now - timestamp` grows without bound; `targetMs` exceeds duration. No staleness check. |
| C6 | H | same, L164–167 | Always off by seconds, or ping-pong seeking | Position is extrapolated using **two phones' wall clocks** (`System.currentTimeMillis()` on both). Any clock skew becomes sync error. |
| C7 | H | `CoupleSpaceScreen.kt` L255–278; `FirebaseCoupleSync.pushSong/fetchLatestPushSong` | Push-song notification never arrives unless the Couple screen is open; two songs pushed quickly → first is lost | Polling `LaunchedEffect` lives inside the screen composable. `push_song.json` is a single overwritten slot. Dedupe uses sender-clock timestamp. No FCM / background worker. |
| C8 | H | `CoupleChatView.kt` L124–139, L150–161 | Messages vanish, duplicate, or never arrive; failed sends are silent | (a) `LaunchedEffect(spaceId)` captures a stale `messages` snapshot, merges against it and overwrites local state. (b) `sendMessage` ignores the Boolean result (and the result is always `true`, see C9). (c) polling exists only while the chat tab is composed. (d) every poll downloads the whole history. |
| C9 | H | `FirebaseCoupleSync.kt` L170, 283, 287, 291, 310, 358, 416, 465, 533, 536 and L91 | UI says "sent/connected" while nothing was stored | Writes use `client.newCall(req).execute().close()` and never check `isSuccessful`; functions return `true` regardless. `getWorkingFirebase` treats HTTP **401 as "working"** (L91), so permission-denied DBs are considered healthy. |
| C10 | H | `auth/CoupleAuthGuard.kt` L15–31; `CoupleSpaceScreen.kt` L217–253, L361–380, L1363–1372 | Pairing collisions, re-pair after unlink, two "Him" users can't see each other | (a) code = prefix + 4 hex chars (65,536 values) in one global `/invites/` namespace → collisions and guessable. (b) `registerInvite` does a full PUT of `matched=false` every 10 s and can overwrite a `matched=true` written by the partner. (c) Unlink clears local prefs only; old matched message still sits in ntfy cache and is found by `checkInviteMatch` → instant "zombie" re-pair **[VERIFY]**. (d) Identity = binary role (HER/HIM, default HIM). The follower reads `playback_<oppositeRole>`; if both users are HIM nobody ever sees the other. (e) `isWhitelisted()` always `true` (dead guard). |
| C11 | M | `CoupleSpaceScreen.kt` (`todayKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) … }`, `dayOfYear`) | Daily question answers never "both answered" for long-distance couples; key stuck after midnight | Date key uses each phone's local timezone and is computed once per composition, never refreshed. |
| C12 | M | `CoupleChatView.kt` L231 (`msg.sender.equals(myName)`); `CoupleSongShareHelper.kt` L252–254 | Own shared-song bubbles show on the partner's side; history flips after renaming | Bubble ownership is decided by display name. `shareSong` builds sender with `getMyName(role)` (no custom name) while chat uses `customMyName`. |
| C13 | M | `couple/CoupleChatModels.kt` L14, `CoupleSongShareHelper.kt` L23 | Every shared song is labelled "HI-RES" | Hardcoded default `formatBadge = "HI-RES"`. |
| C14 | M | `CouplePlaybackSyncManager.kt` L92–117; `FirebaseCoupleSync.broadcastLivePlayback` L518–542 | Battery/data drain; possible `ntfy.sh` 429s | Every 1.2 s + every player event: 1 ntfy POST + 2 Firebase PUTs (one to the shared legacy `live_playback`, which both partners overwrite). 5 s timeouts, executed sequentially. |
| C15 | M | `MainActivity.kt` ~L940–955 **and** `CoupleSpaceScreen.kt` L281–291 | Your broadcast to partner stops after you open then leave the Couple tab | Two places call the singleton `startBroadcaster`. `startBroadcaster` begins with `stopBroadcaster()`. The Couple screen's effect replaces MainActivity's job; when the screen leaves composition its scope cancels the job; MainActivity's effect does not re-run. Also the Player listener is not removed on cancel. |
| C16 | M | `FirebaseCoupleSync.fetchLivePlayback` L579–584 | Dead/wrong helper | Calls `fetchPartnerLivePlayback(spaceId, "")` → reads `playback_.json`. |
| C17 | M | `CoupleSpaceScreen`/`CoupleChatView` | Chat history grows without bound | Whole chat is one JSON string in DataStore, rewritten per message. Move to Room, paginate. |
| C18 | L | `FirebaseCoupleSync.submitDailyAnswer` | Legacy fields `ankitAnswer/ritikaAnswer` still read | Hardcoded personal names in shared code (`CoupleRole.ANKIT/RITIKA`). Remove before public release. |
| C19 | L | `PushSongNotificationManager.kt` | Notification "Listen Now" works but only when process is alive | See C7. |

### 1B. Downloads / Hi-Res

| ID | Sev | Where | Symptom | Root cause |
|---|---|---|---|---|
| D1 | C | `playback/AudioExportService.kt` L151–195; `utils/YTPlayerUtils.kt` (`playerResponseForPlayback`: `contentLength = null`) | Export fails ("Part 3 failed: HTTP 416") or file corrupt/truncated | `totalLength = contentLength ?: 10_000_000L`. File preallocated to 10 MB; 4 range parts computed from the fake length. |
| D2 | C | `AudioExportService.kt` L418–424; `ui/screens/settings/PlayerSettings.kt` L217–265, L453–474; `YTPlayerUtils` `maxKbps` | "Lossless FLAC / Hi-Res" is fake | Source is always Opus/AAC. `HI_RES_LOSSLESS` maps to the same 320 kbps cap as `HIGH`. ffmpeg `-c:a flac` just wraps decoded lossy PCM. |
| D3 | H | `playback/DownloadUtil.kt` L130 | "Download quality" setting does nothing for in-app downloads | `audioQuality = AudioQuality.OPUS` hardcoded. The pref only forms part of a cache-key string. `AudioExportService` L98 does the same. |
| D4 | H | `YTPlayerUtils.playerResponseForPlayback` | Downloaded songs show as "Unknown", duration 0, no thumbnail, wrong quality badge | Returns `videoDetails = null`, fake `FormatEntity` values (`itag=251`, `audioSampleRate=48000`, `bitrate` = the requested cap, `contentLength=null`). `DownloadUtil` L173–183 reads those nulls. |
| D5 | H | `DownloadUtil.kt` L120–123, L202–203 | Slow/failing downloads, 403s, heavy CPU/network | Cache stores `streamExpiresInSeconds * 1000L` (a duration, e.g. 21,600,000) and compares it to `System.currentTimeMillis()`. It is always "expired", so **every chunk** re-runs the resolver (`runBlocking` + PoToken). `songUrlCache` is a plain `HashMap` hit from 3 threads. |
| D6 | H | `AudioExportService.kt` L44–64, L129–138, L392 | Multi-song export: some songs stuck "Exporting…" forever; crash when app in background | `stopSelf()` in each export's `finally` stops the service when the first export ends; `onDestroy` cancels `serviceScope` for the rest (temp files + `ExportingSongIdsKey` never cleaned). `startService()` from background throws on API 26+; manifest says `foregroundServiceType=dataSync` but `startForeground()` is never called. |
| D7 | H | `AudioExportService.kt` L129–131; `ExoDownloadService.TerminalStateNotificationHelper` | User never learns a download failed | Export errors only go to Timber. `TerminalStateNotificationHelper` is **never registered** (no usages). |
| D8 | H | `AudioExportService.kt` L42, L166–170 | 403 on stream parts for some clients; ignores proxy/IPv4 settings | Bare `OkHttpClient()`; `PlaybackData.headers` (from InnerTubeX) are dropped; no retry/backoff. |
| D9 | M | `AudioExportService.kt` L184–187 | Slow export, jank | `updateExportProgress` does a DataStore `edit` (disk write) for every 128 KB read, from 4 coroutines. |
| D10 | M | `AudioExportService.kt` L266, L277; `DownloadsViewModel.kt` L222–223 | Same-title songs overwrite each other; Downloads screen shows "Unknown Artist" | Export writes `"$title.$ext"`; scanner expects `"Artist - Title.ext"`. No `.lrc` is ever written although the UI looks for it; `EmbedLyricsOnDownloadKey` is never read by any service; `hasLyrics = true` hardcoded in the VM. |
| D11 | M | `ui/menu/SongMenu.kt` L737–830; `DownloadsViewModel.refreshLocalFiles` | "Downloaded but nothing in my files" | Normal Download → ExoPlayer private cache. Real-file Export is gated behind `enableExportAsMp3` (default `false`) and writes to a SAF folder, while the Downloads screen scans `Music/<folderName>`. Two disconnected systems. |
| D12 | M | `DownloadsViewModel.kt` L80, L87, L92 vs `DownloadUtil.kt` L68–73 vs `echomusic/AudioDeviceBottomSheet.kt` L906–911 | Settings screen shows "Lossless FLAC" while code uses YouTube/MP3 | Default `DownloadQuality` is `LOSSLESS_FLAC` in the VM, `YOUTUBE` elsewhere. |
| D13 | M | `DownloadsViewModel.kt` L254–281 | "Delete" / "Clear all" leave ghosts | `deleteTrack` for DB tracks only flips `isDownloaded=false`; cached bytes and `DownloadManager` entry remain. `clearAllDownloads` has `val songs = …` + comment and does nothing to DB/DownloadManager. |
| D14 | M | `DownloadsViewModel.setDownloadFolder` L98–105 + `clearAllDownloads` L269–281 | **Data loss risk** | Folder name is not sanitised. `"."` or `".."` makes `File(musicDir, name).deleteRecursively()` wipe the whole Music dir (or its parent). |
| D15 | M | `DownloadsViewModel.openInFileManager` L283–293 | Button does nothing | `Uri.fromFile` → `FileUriExposedException` (swallowed by `runCatching`). Also direct `File` scanning of `Music/` is unreliable on Android 10+ scoped storage (empty after reinstall). |
| D16 | L | `utils/LocalFileDownloader.kt` L85–93, L98 | Leaked connection; corrupt file when overwriting | `response` never closed on failure; `openOutputStream(uri)` without `"wt"` may not truncate an existing file; partial file not deleted. |
| D17 | L | `DownloadUtil.kt` L127–134, L259–270 | No auto-retry; brief mobile-data leak with Wi-Fi-only | `getOrThrow()` throws a non-`IOException` so Media3 does not retry; `requirements` default to any network until the pref flow emits. |
| D18 | L | `DownloadsViewModel.kt` L161 | Legit tracks disappear | `distinctBy { title_artist }` merges different versions/albums. |
| D19 | L | `DownloadUtil.kt` L84–86 vs `MusicService.kt` L1000–1008 | The "read from playerCache" optimisation is probably a no-op | Player writes keys as `"${mediaId}_${audioQuality.name}"`; downloader reads key `mediaId`. **[VERIFY]** Do not "fix" by sharing keys across formats (would splice different codecs). |
| D20 | L | `YTPlayerUtils.kt` | Misleading info shown in UI | `streamExpiresInSeconds = 21600` is hardcoded rather than parsed from the URL (`expire=`). |

### 1C. Security / hygiene

| ID | Sev | Where | Issue |
|---|---|---|---|
| S1 | C | `FirebaseCoupleSync.kt` L43–67, whole file; `app/google-services.json` committed; **no `database.rules.json` in repo** | Unauthenticated REST access to `her-music-53197` RTDB from a public URL. For the app to work at all the rules must be open (`.read/.write: true`). Anyone can read, edit or delete every couple's chat/playback/answers. **[VERIFY]** in Firebase console → Realtime Database → Rules. |
| S2 | H | `FirebaseCoupleSync.publishRelay/pollRelay` | Chat, playback, invites also posted in plaintext to public `https://ntfy.sh/her_sync_53197_…` topics whose names are derived from guessable codes. Anyone who knows/guesses the topic can read it. ntfy.sh also rate-limits anonymous publishers **[VERIFY limits]**. |
| S3 | M | `AndroidManifest.xml` L54 | `allowBackup="true"` while chat history lives in DataStore. |
| S4 | L | repo root | `app/reply_next.json`, `app/yt_video_next.json`, `app/server.json` (raw YouTube responses incl. `visitorData`) and a binary `package/*.apk` are committed. Move APKs to GitHub Releases, delete the JSON dumps. |
| S5 | L | README | Claims features that are not wired (partner tune-in, heart bursts to partner, Hi-Res). Fix code or fix README. |

---

## 2. Fix specifications

Implement in the order of §3. Each item lists the exact change and acceptance criteria.

### 2.1 Downloads — make them real and honest

#### D1 — Correct content length (do this first)

Add a probe and use it everywhere a length is needed. Prefer `clen=` from the URL; fall back to a 1-byte ranged GET.

```kotlin
// utils/StreamLength.kt
fun probeContentLength(client: OkHttpClient, url: String, headers: Map<String, String>): Long {
  android.net.Uri.parse(url).getQueryParameter("clen")?.toLongOrNull()?.let { if (it > 0) return it }
  val req = Request.Builder().url(url).header("Range", "bytes=0-0")
    .apply { headers.forEach { (k, v) -> header(k, v) } }.build()
  client.newCall(req).execute().use { r ->
    if (!r.isSuccessful) throw java.io.IOException("Length probe failed: HTTP ${r.code}")
    r.header("Content-Range")?.substringAfter('/')?.toLongOrNull()?.let { return it }
    throw java.io.IOException("Server did not report total length")
  }
}
```

In `AudioExportService.downloadStream`:
- Delete the `?: 10_000_000L` fallback.
- `val totalLength = playbackData.format.contentLength?.takeIf { it > 0 } ?: probeContentLength(httpClient, playbackData.streamUrl, playbackData.headers.orEmpty())`
- Send `playbackData.headers` on every part request.
- Do **not** append `&range=` **and** a `Range` header; use the header, and fall back to the `&range=` query only if the first part gets 403/416.
- After `parts.awaitAll()`, `check(written.get() == totalLength)`; otherwise throw (do not convert truncated data).
- Retry each part up to 3 times with exponential backoff on `IOException`/5xx.

Also in `YTPlayerUtils.playerResponseForPlayback`, return `contentLength` from the same probe/`clen` so the DB `FormatEntity` is right.

**Acceptance:** exporting a 3-min and a 10-min track (and a very short one) each yields a playable file with exact expected byte length; no 416.

#### D2 — Stop pretending: remove fake lossless, add honest options

YouTube Music does not serve FLAC or 24-bit streams. Do not ship an option that implies it.

1. `core/.../constants/PreferenceKeys.kt`: replace `DownloadQuality` with:
   ```kotlin
   enum class DownloadQuality { ORIGINAL, MP3_COMPAT }   // keep old names as @Deprecated aliases mapped in a one-time DataStore migration
   ```
   - `ORIGINAL` = best available stream, **remuxed, no re-encode** (`-c:a copy`). Opus-in-WebM → `.opus`; AAC-in-MP4 → `.m4a`. Smaller, zero loss.
   - `MP3_COMPAT` = lossy→lossy MP3 (label it "for old devices; lowers quality").
   - Migration: `LOSSLESS_FLAC`, `HIGH_AAC`, `OPUS`, `YOUTUBE`, `MP3_320` → map to `ORIGINAL` (except `MP3_320` → `MP3_COMPAT`).
2. `AudioExportService.buildFfmpegCommand`: for `ORIGINAL` use `-c:a copy`. Pick the extension from `format.mimeType` (`audio/webm; codecs="opus"` → `opus`, `audio/mp4` → `m4a`). Attach cover art with `-disposition:v attached_pic` only for `m4a`/`mp3`.
3. `AudioQuality.HI_RES_LOSSLESS`: remove the enum value (migrate stored value → `HIGH`). Rename UI strings in `PlayerSettings.kt` L217–265 and L453–474, `AudioDeviceBottomSheet.kt` L875, to e.g. "Best available (up to ~256 kbps)". Remove the "bit-perfect / FLAC 24-bit / CD" copy everywhere.
4. `QualityBadge.kt`: a badge may say FLAC/Hi-Res only if the **decoder** reports FLAC (already handled by `buildLiveQualityFromDecoder`). Delete the string-heuristic paths that turn an arbitrary codec string containing "HI-RES"/"LOSSLESS" into a badge (`isFlacLikeCodec`).
5. If you genuinely want lossless: it requires a licensed source (e.g. an official API of a service the user subscribes to, or the user's own local FLAC files via `localmedia/*`). Do **not** add unofficial/pirate lossless scrapers. Tell the user plainly in-app that YouTube Music is lossy.

**Acceptance:** no UI string anywhere claims FLAC/Hi-Res/bit-perfect for YouTube content; exported `ORIGINAL` file bitrate equals the source stream; file size ≈ stream size.

#### D3 / D4 / D5 — Respect quality, return real metadata, fix URL cache

`utils/InnerTubeXResolver.Extracted` already has `kbps`, `mimeType`, `url`, `headers`. Extend it (and `YTPlayerUtils.playerResponseForPlayback`) to carry **real** values: `itag` (or codec), `sampleRate`, `contentLength`, `expiresAtEpochMs` (parse `expire=` from the URL), and title/duration/thumbnail from a metadata call (`playerResponseForMetadata` already exists — use it to fill `videoDetails`).

`DownloadUtil.kt`:
```kotlin
private val songUrlCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()
...
val quality = com.fenrir.her.constants.AudioQuality.HIGH  // map from DownloadQuality; no OPUS hardcode
songUrlCache["${mediaId}_${quality.name}"] =
  streamUrl to (playbackData.expiresAtEpochMs - 60_000L)   // absolute epoch, 1-min safety margin
```
Wrap the resolver failure so Media3 retries: `catch (e: Exception) { throw java.io.IOException(e) }` instead of `getOrThrow()`.

Stop upserting a `SongEntity` titled "Unknown" from the resolver; if the song isn't in the DB, enqueue a metadata fetch instead.

Wi-Fi-only: initialise `downloadManager.requirements` synchronously from a single blocking read of `DownloadOnWifiOnlyKey` before the manager starts (or construct with `Requirements.NETWORK_UNMETERED` and relax after the pref loads).

**Acceptance:** resolver is called once per song per ~5 h, not per chunk; downloaded songs have correct title/duration/thumbnail; changing the quality setting changes the downloaded bitrate.

#### D6 / D7 / D8 / D9 — Robust export service

Rewrite `AudioExportService` as a real foreground queue (or WorkManager `CoroutineWorker` with `setForeground`):
- `startForegroundService()` from callers; call `startForeground(id, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)` in `onStartCommand`.
- A single `Channel<ExportJob>` worker (concurrency 2). Keep a counter of active jobs; call `stopSelf()` only when the queue is empty (or use `stopSelf(startId)` semantic).
- On service start, clear stale `ExportingSongIdsKey`/`ExportProgressKey` left by a previous process death.
- Notifications: ongoing progress per batch, and a **failure notification with the reason** (and a Retry action). Also post a Toast/Snackbar from the UI layer when the user starts an export without a directory.
- Share one `OkHttpClient` with the app (proxy + DNS/IP-version like `DownloadUtil`), add headers from `PlaybackData.headers`.
- Throttle progress: publish via an in-memory `MutableStateFlow<Map<String,Int>>` and only persist at ≥2 % changes (or not at all).
- Register `ExoDownloadService.TerminalStateNotificationHelper` on the `DownloadManager` (e.g. in `DownloadUtil.init`) so failed Exo downloads notify.
- Disk-space check before starting (`StatFs` ≥ 3× expected size for transcodes, 1.2× for remux).

**Acceptance:** exporting 20 songs from a playlist with the app backgrounded completes all 20; killing network mid-way produces a visible failure + retry; no song stays "Exporting" after a process restart.

#### D10 / D11 / D12 — One download system, one source of truth

- Decide the model and implement it consistently. Recommended: **"Download" = file in a user-chosen SAF folder (default `Music/Her`) + in-app offline playback from that file**, using Export as the single path; keep ExoPlayer cache purely as a playback cache.
- If you keep the two-layer model, rename UI honestly: "Save for offline (in app)" vs "Export to files". Remove the `enableExportAsMp3` gate or default it to `true`, and show the export folder state in the Downloads screen.
- Filenames: `"{Artist} - {Title}.{ext}"`, sanitised (strip `\ / : * ? " < > |`, control chars, trailing dots, cap 120 chars). On collision append ` (2)` deterministically; do not overwrite.
- Lyrics: if `EmbedLyricsOnDownloadKey` is on, fetch lyrics via the existing `lyrics` module and either write USLT/`LYRICS` metadata through ffmpeg (`-metadata lyrics=…`) or a sibling `.lrc`. Set `hasLyrics` from reality, not `true`.
- Create **one** `DownloadSettingsRepository` exposing `downloadQuality` with a single default; make `DownloadsViewModel`, `DownloadUtil`, `AudioDeviceBottomSheet`, `PlayerSettings` read it.
- Scan exported files via `DocumentFile`/`MediaStore` (not `java.io.File` on `Music/`).

#### D13 / D14 / D15 — Deletion and folder safety

```kotlin
// DownloadsViewModel
fun setDownloadFolder(name: String) {
  val clean = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").trim('.', ' ')
  require(clean.isNotBlank() && clean != "." && clean != "..") ; // else fallback "Her"
  ...
}
```
- Never call `deleteRecursively()` on a user-derived path. Delete only the files the app created (track list from DB / a manifest file), one by one.
- `deleteTrack`/`clearAll`: also call `DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, id, false)` (or `sendRemoveAllDownloads`) **and** update the DB (`updateDownloadedInfo(id, false, null)` for every song).
- `openInFileManager`: use `DocumentsContract`/`Intent.ACTION_OPEN_DOCUMENT_TREE` with the persisted tree URI, or `FileProvider`. Remove `Uri.fromFile`.
- Replace `distinctBy{title_artist}` with `distinctBy{id}` plus a separate "duplicate" hint.

#### D16 — LocalFileDownloader

Wrap `response` in `use {}`; open output with `contentResolver.openOutputStream(uri, "wt")`; on any failure delete the partial file; make notification id unique per download; check `POST_NOTIFICATIONS` on API 33+.

---

### 2.2 Her & Him tab

#### Architecture change (needed for C3, C7, C8, C15 — do once)

Create a single app-scoped coordinator so nothing depends on which Compose screen is visible:

```kotlin
@Singleton
class CoupleSyncCoordinator @Inject constructor(
  @ApplicationContext private val ctx: Context,
  private val repo: CoupleRepository,                 // wraps Firebase; returns Result<T>, checks HTTP status
  private val database: MusicDatabase,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var jobs: List<Job> = emptyList()

  val partnerPlayback = MutableStateFlow<CoupleLivePlayback?>(null)
  val liveSyncFollowing = MutableStateFlow(false)     // derived from followJob?.isActive
  val incomingPush = MutableSharedFlow<CouplePushSong>(extraBufferCapacity = 8)
  val connection = MutableStateFlow(ConnectionState.UNKNOWN) // OK / OFFLINE / DENIED

  fun start(cfg: CoupleConfig, player: PlayerConnection) { stop(); jobs = listOf(
      scope.launch { broadcastLoop(cfg, player) },
      scope.launch { pushLoop(cfg) },
      scope.launch { chatLoop(cfg) },
      scope.launch { radioLoop(cfg) },
      scope.launch { dailyAnswersLoop(cfg) }) }
  fun stop() { jobs.forEach { it.cancel() }; liveSyncFollowing.value = false }
}
```
- Start it from `MusicService`/`App` when `CoupleIsPairedKey` is true; stop on unlink. Run loops while the app process lives; for a push that must arrive when the app is closed you need FCM (see S1 section) — otherwise document that notifications need the app/service running.
- Remove all sync `LaunchedEffect`s from `CoupleSpaceScreen`, `CoupleChatView`, `CouplePlayTogetherCard`, and the `startBroadcaster` call in `MainActivity` (and its `else → stopBroadcaster()`); screens only **collect** flows.
- Remove/detach the Player listener when the broadcaster stops.
- Prefer the Firebase Android SDK (`firebase-database`) with `ValueEventListener`/`ChildEventListener` instead of REST polling; it gives push-style updates, offline cache, `.info/serverTimeOffset`, and removes most of the polling code. If staying on REST, use the SSE stream (`Accept: text/event-stream`) for `messages`, `playback_*`, `push_songs`.

#### C1 — Make "Send to Partner" actually send

`CoupleSongShareHelper.shareSong` must, after writing the local message:
1. Read `CoupleSpaceIdKey`, `CoupleIsPairedKey`, `CoupleFirebaseUrlKey`. If not paired → toast "Pair with your partner first".
2. `val ok = repo.sendChatMessage(spaceId, message).isSuccess && repo.pushSong(spaceId, push).isSuccess`.
3. Only show "Sent …" if `ok`; otherwise mark the message `FAILED` and keep it in an **outbox** that the coordinator retries with backoff.
4. Build `sender` from the same source as chat (`customMyName` or the stable `senderId`, see C12).

Acceptance: song shared on phone A appears in B's chat + Songs tab + notification within ~10 s; airplane mode on A shows pending → delivered after reconnect.

#### C2 — Radio + hearts

- Call `fetchRadioBroadcast` in `radioLoop` (or listen to `spaces/{id}/radio`) and write it into `CoupleRadioBroadcastKey`.
- Heart: send an event `spaces/{id}/hearts/{pushId} = {from, ts}`; receiver sets `CoupleHeartBurstEpochKey = now` when it sees an unseen id from the partner. Do not write the heart to your **own** local pref as if it were received.
- "Stop broadcast" must write the empty session remotely (the Go-Live button does; the banner's `onStopBroadcast` does not — fix).
- Keep `RadioBroadcastSession` payload under a few KB (relay limit); do not embed queues.

#### C3 / C4 / C5 / C6 — Live-sync follower

Replace the loop body with:

```kotlin
private var lastRequestedSongId: String? = null
private var lastRequestedAt = 0L

private suspend fun followOnce(live: CoupleLivePlayback, pc: PlayerConnection, serverNow: Long) {
  val player = pc.player
  val ageMs = (serverNow - live.timestampEpochMs).coerceAtLeast(0)
  val fresh = ageMs < 15_000
  val playing = live.isPlaying && fresh

  val current = pc.service.currentMediaMetadata.value?.id
  if (current != live.songId) {
    val now = SystemClock.elapsedRealtime()
    if (lastRequestedSongId != live.songId || now - lastRequestedAt > 15_000) {   // C4: no re-queue storm
      lastRequestedSongId = live.songId; lastRequestedAt = now
      pc.playQueue(YouTubeQueue(WatchEndpoint(videoId = live.songId)))
    }
    return
  }
  val dur = live.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
  val target = (live.positionMs + if (playing) ageMs else 0L).coerceIn(0L, dur)       // C5: clamp/stale
  val drift = abs(player.currentPosition - target)
  if (drift > 1_800L) player.seekTo(target)
  if (playing && !player.isPlaying) player.play()
  if (!playing && player.isPlaying) player.pause()
}
```

**Server clock (C6):** write `timestampEpochMs` as Firebase server time (`{".sv":"timestamp"}` when using REST; SDK: `ServerValue.TIMESTAMP`) and compute `serverNow = System.currentTimeMillis() + serverOffset`, where `serverOffset` comes from `.info/serverTimeOffset` (SDK) or the HTTP `Date` header of the last Firebase response. Never subtract one phone's `currentTimeMillis()` from another's.

**Loop-safety:** when this device is *following*, suppress its own broadcaster (or mark `followingPartner=true` in its payload) so two followers can't chase each other. Expose `liveSyncFollowing` as `followJob?.isActive` so the button state is always truthful (C3). Persist the user's choice in `CoupleLiveSyncEnabledKey` (already declared, unused) and restore on start.

#### C7 — Push songs

- Data model: `spaces/{id}/pushes/{pushId}` (append-only, `pushId` from `push()` or a UUID). Receiver tracks `lastSeenPushId` (not a timestamp from the sender's clock) in DataStore.
- Poll/listen in the coordinator; emit to `incomingPush`; `PushSongNotificationManager` shows the notification; the existing `PLAY_PUSHED_SONG` handler in `MainActivity` stays.
- Real background delivery needs FCM (Cloud Function triggers on `pushes/*`) — requires the Blaze plan. If you don't want that, state in the UI: "Notifications arrive while Her Music is running."
- Request `POST_NOTIFICATIONS` at the moment the user pairs (Android 13+); currently it is silently skipped if not granted.

#### C8 — Chat

- Store messages in Room (`couple_messages`: `id` PK, `spaceId`, `senderId`, `text`, `songJson`, `ts`, `status` = PENDING/SENT/FAILED). UI collects `Flow<List<…>>`; no JSON-in-DataStore.
- `sendMessage`: insert as PENDING → coordinator outbox sends → on success mark SENT; on failure FAILED with a tap-to-retry bubble.
- Incoming: listener/SSE on `messages` ordered by key, `limitToLast(100)` for the first load, then `startAt(lastKey)`. Upsert by `id`; never overwrite local rows.
- Show unread badge + notification when a message arrives and chat isn't visible.

#### C9 — HTTP layer

Create `CoupleRepository` with one helper:

```kotlin
private suspend fun call(req: Request): Result<String> = withContext(Dispatchers.IO) {
  runCatching {
    client.newCall(req).execute().use { r ->
      when {
        r.code == 401 || r.code == 403 -> throw CoupleDeniedException()   // surface "permission denied"
        !r.isSuccessful               -> throw IOException("HTTP ${r.code}")
        else                          -> r.body?.string().orEmpty()
      }
    }
  }
}
```
- Replace every `execute().close()` and every `return true` with this. `getWorkingFirebase` must treat 401/403 as **not working**.
- Expose `connection: StateFlow<OK|OFFLINE|DENIED>` and show a small banner in the Couple screen ("Can't reach server" / "Server denied access").
- Raise client timeouts to 10–15 s for writes; do not run the 2–3 requests of one logical write sequentially on the broadcast loop.

#### C10 — Pairing & identity (redesign)

- **Identity:** stop using role as identity. Generate a stable `deviceUid` (UUID in DataStore, or Firebase Anonymous Auth `uid`). Role (Her/Him) becomes a cosmetic label that can be anything or custom; playback key = `uid`, not `playback_him`. This also fixes same-label couples.
- **Codes:** 8 chars from `SecureRandom` over Crockford base32 (≈40 bits), single-use, TTL 10 min, stored at `invites/{code}`. Claim via a transaction (`if matched==false → set matched, partnerUid, spaceId`). `spaceId` = random UUID, **not** derived from the codes.
- Never PUT over an invite that is `matched`. `registerInvite` must be `PATCH`/conditional.
- **Unlink:** write `spaces/{id}/unlinked = {by, ts}`; delete the invite nodes and the space data you own; clear local keys (`CoupleSpaceIdKey`, `CouplePartnerCodeKey`, `CoupleMyCodeKey`, chat table, shared songs, `lastSeenPushId`); partner's coordinator sees `unlinked` and shows "Your partner unlinked". Remove the old ntfy relay (see S2) so the zombie re-pair path disappears.
- Only regenerate `myCode` if it is blank or **unpaired**; never when `role` changes while paired.
- Delete `CoupleAuthGuard.isWhitelisted` and the `ANKIT`/`RITIKA` roles.

#### C11 — Daily question across time zones

Compute the key from a shared reference: `dateKey = LocalDate.now(ZoneOffset.UTC)` (or from server time), recomputed by a flow that ticks at the next UTC midnight. Pick the question as `QUESTIONS[(epochDay % size)]` from that same key so both phones show the same question. Replace `remember { … }` with a state that updates.

#### C12 / C13 — Bubble ownership and badge

- Add `senderId` (the `deviceUid`) to `CoupleChatMessage`/`SharedSongAttachment`; `isMe = msg.senderId == myUid`. Keep `sender` only as display name; migrate old messages by treating `sender == myName` as mine once.
- Change `formatBadge` default to `null`. Compute the badge from the song's real format (`FormatEntity`) or omit it.

#### C14 — Broadcast cadence

- Send on **events** (play/pause, seek discontinuity, track change) plus a heartbeat every ~8 s while playing, and one final `isPlaying=false` on stop/pause.
- One write per event to `playback/{uid}`; delete the legacy `live_playback` write and the dead `fetchLivePlayback`.
- Drop the ntfy relay (S2). Debounce discontinuities caused by the follower's own `seekTo`.

---

### 2.3 Security (S1–S5)

**S1 – Lock the database.** Minimum viable approach:
1. Add Firebase Anonymous Auth. REST requests append `?auth=<idToken>`; SDK handles it automatically.
2. Membership: on a successful claim, write `spaces/{spaceId}/members/{uid}=true` (both users).
3. Rules (`database.rules.json`, commit it):
```json
{
  "rules": {
    ".read": false, ".write": false,
    "invites": {
      "$code": {
        ".read": "auth != null",
        ".write": "auth != null && (!data.exists() || data.child('ownerUid').val() === auth.uid || (data.child('matched').val() === false && newData.child('matched').val() === true))",
        ".validate": "newData.hasChildren(['ownerUid','ts'])"
      }
    },
    "spaces": {
      "$spaceId": {
        ".read": "auth != null && root.child('spaces/'+$spaceId+'/members/'+auth.uid).exists()",
        ".write": "auth != null && root.child('spaces/'+$spaceId+'/members/'+auth.uid).exists()",
        "messages": { "$id": { ".validate": "newData.child('text').isString() && newData.child('text').val().length < 2000" } }
      }
    }
  }
}
```
(Adjust so the claim write is allowed before membership exists — e.g. only the claim transaction path.) Add App Check (Play Integrity) once stable. Rotate/restrict the API key in `google-services.json` to the package + SHA-1 in Google Cloud Console.
4. Optionally encrypt message bodies client-side with a key derived from a secret exchanged at pairing (so even DB admins can't read chats).

**S2 – Remove the ntfy relay** (`publishRelay`/`pollRelay`, all call sites, the `inv_…` fallback). If you must keep a fallback, use a random 128-bit topic stored only in the space record, never derived from codes.

**S3** – `android:allowBackup="false"` (or `dataExtractionRules` excluding the DataStore/Room files).

**S4** – Delete `app/reply_next.json`, `app/yt_video_next.json`, `app/server.json`; move `package/*.apk` to GitHub Releases; add them to `.gitignore`. Rewrite git history only if they contain anything private.

**S5** – After fixes, update README claims to match reality (and remove Hi-Res claims).

---

## 3. Implementation order (PR plan)

1. **PR-1 Hotfix downloads:** D1, D5, D6 (foreground + queue), D7 (failure notifications), D8 (headers/retry), D9. *Result: Export works reliably.*
2. **PR-2 Honest quality:** D2, D3, D4, D12 (single settings source), copy changes, enum migration.
3. **PR-3 Download UX/model:** D10, D11, D13, D14, D15, D16, D18.
4. **PR-4 Couple HTTP layer + outbox:** C9, C1, C8 (Room), C12, C13. *Result: sharing and chat are truthful and reliable.*
5. **PR-5 Coordinator:** C3, C15, C7, C2, C16, C14, plus follower rewrite C4/C5/C6.
6. **PR-6 Pairing/identity + security:** C10, C11, S1, S2, S3, S4, C17, C18. This changes the data schema — ship with a migration screen ("re-pair your partner").
7. **PR-7 Docs:** README, privacy policy (it must mention Firebase + what is stored).

---

## 4. Test plan / acceptance checklist

**Downloads**
- [ ] Export 3-min, 10-min, and a 30-s track → playable, exact size, correct tags + cover.
- [ ] Export 20 tracks with app backgrounded + screen off → all complete, notification progress, final summary.
- [ ] Airplane mode mid-export → visible failure with Retry; no stuck "Exporting".
- [ ] Two songs same title, different artists → two files.
- [ ] Settings show only honest options; no "FLAC/Hi-Res" for YouTube content.
- [ ] Changing quality changes output; default is identical in every screen.
- [ ] Delete/clear removes file, DB flag, Exo entry; folder name `.` / `..` rejected.
- [ ] Wi-Fi-only on → no mobile-data downloads right after cold start.

**Couple**
- [ ] A shares song → B gets chat message + Songs entry + notification (both devices on different networks).
- [ ] B closes the Couple tab, keeps browsing → Live Sync keeps following; button reflects true state.
- [ ] A force-closes the app → B's follower stops extrapolating within 15 s.
- [ ] Phones with clocks 2 minutes apart still sync within ~2 s.
- [ ] Partner changes track and resolve takes >3 s → track starts once, no restart loop.
- [ ] Radio: A "Go Live" → B sees banner and Tune-in plays the same song; hearts appear on B's screen.
- [ ] Chat: send while offline → pending → delivered; no duplicates after 10 min of polling; unread badge works.
- [ ] Two "Him" (or two custom-label) users can pair and see each other.
- [ ] Unlink on A → B informed; re-pairing requires a new code.
- [ ] Phones in different time zones answer the same daily question on the same date key.
- [ ] Database rules: a third client with the URL but no membership gets `Permission denied`.

---

## 5. Things I could not verify (check before trusting)

- Actual Firebase **rules** and ntfy.sh anonymous rate limits.
- Whether `ChunkingDataSource` re-invokes the resolver per chunk (this makes D5 worse; the cache bug is real either way).
- Whether `InnerTubeXResolver` URLs always carry `clen=` (fallback probe covers it).
- Behaviour on Android 14/15 foreground-service-type restrictions for `dataSync` (6-hour cap on 15).
- Runtime reproduction of the zombie re-pair (C10c) and of C15.
