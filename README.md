# File Manager & Storage Cleaner (Android)

Native Kotlin app using Jetpack Compose + Material 3, MVVM, Hilt for DI,
Room for local caching (recycle bin metadata, vault index), and
WorkManager for background scans.

## Opening the project

1. Open this folder in Android Studio (Koala/2024.1 or newer).
2. Android Studio will auto-generate the Gradle wrapper jar on first sync
   (this environment has no network access, so `gradle/wrapper/gradle-wrapper.jar`
   isn't included — Android Studio fetches it automatically, or run
   `gradle wrapper --gradle-version 8.7` once if using the command line).
3. Sync Gradle, then Run on a device/emulator running API 26+.

## Architecture

- `ui/` — Compose screens, organized by feature (`home`, `files`, `cleaner`, `vault`, `settings`, `recyclebin`), plus shared `components/` and `theme/`.
- `data/model/` — plain data classes shared across features (`FileEntry`, `FileCategory` + classifier).
- `data/repository/` — the single source of truth each ViewModel reads from (`FileOperationsRepository`, `StorageStatsRepository`, `RecycleBinRepository`).
- `data/scanner/` — background scanning engines (duplicate files/folders, empty folders, large files, junk/cache).
- `data/local/` — Room database (currently just the Recycle Bin table).
- `di/` — Hilt modules.
- `util/` — cross-cutting helpers (`PermissionUtils`, `FormatUtils`, `ShareUtils`).

## Status — built so far (Modules 1–8)

- **Module 1**: Project scaffold, storage permission gate, bottom nav shell, Home dashboard (real `StatFs` + `MediaStore` stats).
- **Module 2 — File Browser (§3, §20, §21)**: full folder navigation with back-stack, create/rename/copy/move/delete, single-file share via `FileProvider`, multi-select with a contextual action bar, list/grid toggle, sort by name/size/date/type, in-folder recursive search, file info dialog, delete confirmation with total size.
- **Category Detail (§5)**: Home's category cards open a device-wide, category-filtered file list.
- **Module 3 — Duplicate File Scanner (§6)**: two-phase engine — cheap size-bucket pass first, then SHA-256 hashing only within buckets that have 2+ files, streamed and cancellable. Expandable groups, "select all but oldest," never auto-deletes.
- **Module 4 — Duplicate Folder Scanner (§7)**: compares folders by content, not name — size prefilter, then a bottom-up content signature (memoized across shared subtrees).
- **Module 5 — Empty Folder Scanner (§8)**: single cheap pass; a folder qualifies only if it has zero children of any kind.
- **Module 6 — Large File Scanner (§9)**: configurable threshold (50/100/250/500 MB, 1 GB, or custom); full file actions (open/share/rename/move/delete). Introduced the reusable `FolderPickerDialog`.
- **Module 7 — Recycle Bin (§19)**: every delete across the app now routes through `RecycleBinRepository` instead of touching files permanently. Items physically move to the app's private external-files directory and are tracked in Room with original location, size, and deleted date. File Browser shows an inline "Undo" snackbar; the Recycle Bin screen supports restore, permanent delete, and empty-bin.
- **Module 8 — Junk/Cache Cleaner (§14, §15)**: honest about Android's real constraints — no third-party app can read another app's private cache since Android 11, so this never claims to. Covers temp/log/backup files, interrupted downloads, zero-byte files, leftover APK installers (confirmed via `PackageManager`, not guessed), legacy `.thumbnails` caches, and this app's own cache. Grouped by category with the reason shown per item; deletes go through the same Recycle Bin.
- **Module 9 — Private Vault (§10–§13)**: files are genuinely AES-256-GCM encrypted with a key generated inside the Android Keystore (`VaultCryptoRepository`) — never just renamed or hidden, and the key never leaves secure keystore. The PIN/password/pattern itself is never stored: only a PBKDF2 hash (120k iterations) inside `EncryptedSharedPreferences`, which is itself Keystore-backed AES-256 (`VaultCredentialRepository`). Supports PIN, password, or a tap-sequence pattern grid, plus biometric unlock via `BiometricPrompt` with automatic fallback. The vault auto-locks the instant the whole app backgrounds (`VaultSessionManager` + a `ProcessLifecycleOwner` observer in `FileManagerApplication`) and requires re-auth on return. Setup shows a one-time recovery code (hashed the same way) — the file-encryption key is intentionally independent of the PIN, so a recovery-based PIN reset never puts existing vault files at risk, and the recovery screen states that plainly. Adding a file encrypts it into the app's private storage and deletes the unencrypted original; removing it decrypts back to its original location and deletes the vault copy — no unencrypted copy ever exists in both places at once.

- **Module 10 — Settings (§27)**: every setting here has a real, wired effect — nothing decorative. Appearance (Light/Dark/System) is read live by `MainActivity` via `ThemeViewModel`. File Manager's default view (List/Grid) seeds `FilesViewModel`'s initial state; "show hidden files" is read by `FileOperationsRepository.listDirectory` itself. Cleaner's "confirm before deletion" toggle is wired into the File Browser's delete flow (skips straight to the Recycle Bin when off — still fully recoverable either way). Vault settings include changing the PIN/password/pattern (requires the current credential), toggling biometric, a real auto-lock timeout (0/30s/1min/5min — `VaultSessionManager` + `FileManagerApplication`'s lifecycle observer track elapsed background time against it), and regenerating the recovery code. All settings persist via Jetpack DataStore (`AppSettingsRepository`).


## Not yet built (placeholder screens exist so the app runs end-to-end)

Planned module order:
1. ~~File Browser~~ ✅
2. ~~File Search + Category Detail views~~ ✅
3. ~~Duplicate File Scanner~~ ✅
4. ~~Duplicate Folder Scanner~~ ✅
5. ~~Empty Folder Scanner~~ ✅
6. ~~Large File Scanner~~ ✅
7. ~~Recycle Bin~~ ✅
8. ~~Junk/Cache Cleaner~~ ✅
9. ~~Private Vault~~ ✅
10. ~~Settings~~ ✅
11. ~~Polish pass~~ ✅

Each module is delivered as real, working code against actual device storage — no mocked scan results.

**Scope note on Module 7**: the File Browser shows an inline "Undo" snackbar right after deleting (fastest path back). The scanner screens (Duplicate Files, Duplicate Folders, Empty Folders, Large Files, Junk) route deletes through the same Recycle Bin — fully recoverable from the Recycle Bin screen — but don't show their own inline Undo snackbar; adding that per-screen is straightforward if wanted later.

**Scope note on Module 9**: the Vault currently supports individual files (folders can be added file-by-file via the multi-select file picker) — adding whole folders recursively is a reasonable follow-up but out of scope for this pass. Pattern lock is a tap-sequence on a 3x3 grid rather than a continuous drag gesture — a deliberate reliability trade-off, still a real working pattern input, not merely decorative.

## Polish pass (§25 + general UX)

- **Animations**: `Modifier.animateItem()` on every scrollable list that items can disappear from (File Browser, both duplicate scanners, Empty Folder, Large File, Junk, Recycle Bin, Vault contents) — deletions and restores now animate out/in instead of jumping. `Crossfade` between scan phases (scanning → results) on all five scanner screens.
- **Empty states**: consistent 48dp icon size across every "nothing found" state (previously default 24dp, inconsistent).
- **Error handling (§25)**: `VaultLockScreen` now handles `BiometricPrompt` failure explicitly — hardware unavailable, no biometrics enrolled, or lockout all surface a message instead of silently doing nothing; user-cancel and "use PIN instead" are correctly treated as non-errors. Every scanner and file operation already ran through `runCatching` per-item (permission denied, file moved/deleted mid-scan, protected directories) from the modules that introduced them — this pass specifically closed the one gap (biometric errors) that hadn't been handled yet.
