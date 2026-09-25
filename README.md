# PaperRadar

**Get notified when new papers by the authors and on the topics you follow are published.**

PaperRadar is an Android app that checks **arXiv** and **Crossref** (journal publications) in the background for the authors and keywords you watch. It collects new matches into one feed and sends you a notification. You can read open-access PDFs inside the app and open paywalled journal articles through your institution's **EZproxy**.

---

## Features

- **Author and keyword watches.** Follow a researcher by name or track a keyword or phrase.
- **Two sources.** Preprints come from arXiv and published journal articles come from Crossref.
- **Precise matching.** arXiv and Crossref searches are fuzzy, so PaperRadar re-checks every result on the device:
  - *Authors* match only when a single author on the paper has both the watched first name (or its initial) and the watched last name. A watch for "Michael I. Jordan" won't fire for some other Michael or some other Jordan.
  - *Keywords* are case-sensitive. A watch for `Ising` won't fire on "rising".
- **Background checks** run every 1, 3, 6, 12 or 24 hours (6 by default). Scheduled checks wait for a network connection and skip runs when the battery or storage is low.
- **One summary notification per check** lists everything new.
- **Feed.** Unread papers are highlighted. Each card shows its source, publication date and the watch it matched. Tap ✕ to remove a false match.
- **In-app PDF reader** with swipe to turn pages and pinch to zoom (up to 4×). Downloaded PDFs are cached on the device.
- **EZproxy support.** Journal articles open through your library's proxy so your institution's subscriptions apply. arXiv links never go through the proxy because they are already open access.
- **Bounded storage.** The app keeps the 500 most recently published papers and deletes their cached PDFs when they're trimmed.

---

## Installing

PaperRadar isn't on the Play Store. It's shared as an APK file.

**Requirements:** Android 16 (API 36) or newer.

1. Copy `app-release.apk` to your phone, for example through Google Drive or a messaging app. Gmail blocks APK attachments.
2. Open the file. If Android asks, allow installs from that app (Files, Drive, and so on) under **Install unknown apps**.
3. Tap **Install**.

When the app first opens, allow notifications so you get alerts about new papers.

---

## Getting started

1. **Add watches.** Go to the **Watches** tab, tap **+**, choose *Author* or *Keyword*, and type a name or phrase.
2. **Run your first check.** Go back to the feed and tap the refresh button. A progress bar shows how far the check has gotten. Crossref results usually show up within seconds, and arXiv results take longer.
3. **Read.** Tap a paper to see its details and abstract, then:
   - **Read PDF** opens it in the built-in reader when a PDF is available.
   - **Open via EZproxy** opens a journal article through your library login.
   - **Open publisher page (no proxy)** opens the original page directly.

### Recommended settings

On the **Settings** tab:

| Setting | What it does |
|---|---|
| **Notifications** | Turns new-match alerts on or off. |
| **Check frequency** | How often background checks run. |
| **Background reliability** | Tap **Allow background checks** to exempt PaperRadar from battery optimization. Many phones delay or stop background work otherwise, so this is strongly recommended. |
| **EZproxy** | Your library's login URL, with `{url}` where the article link goes. |
| **Manual check** | Runs a check right away. |

### Setting up EZproxy

Look up your library's EZproxy login address. It usually looks like this:

```
https://libezproxy.your-school.edu/login?url={url}
```

Paste it into **Settings → EZproxy** and tap **Save**. If you leave out `{url}`, the article link is added to the end, so a bare stem such as `https://libezproxy.your-school.edu/login?url=` also works.

---

## How it works

```
 Watches (Room DB)
        │
        ▼
 LiteratureCheckWorker  ◄── WorkManager (periodic, or "check now")
   ├── Crossref lane: up to 4 requests at a time
   └── arXiv lane: author watches batched 5 per query, 3 s between requests
        │
        ▼
 AuthorMatcher / KeywordMatcher  (re-check each result on the device)
        │
        ▼
 Papers (Room DB) ──► Feed UI
        │
        └──► Notification summarizing new papers
```

- Crossref and arXiv are checked **in parallel**, so the slower, rate-limited arXiv doesn't hold up journal results.
- Each request is retried once. If arXiv still fails, the remaining arXiv batches are skipped until the next run so the app doesn't keep sending requests to a struggling or rate-limiting server.
- If every request fails (usually because there's no connection), a scheduled run is retried after a few minutes with exponential backoff.
- A paper is saved as soon as it's found. If the system stops a check partway through, a notification is still sent for the papers already saved.

---

## Building from source

**Tech stack:** Kotlin · Jetpack Compose (Material 3) · Room · WorkManager · DataStore · OkHttp · Navigation Compose · Custom Tabs

**Requirements:** A recent Android Studio release with the Android SDK 37 platform installed.

```bash
# Debug build
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Release build (signed if a keystore is configured, see below)
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

On Windows, use `gradlew.bat` in place of `./gradlew`.

### Release signing

Release builds are signed with the keystore described in `keystore/keystore.properties`:

```properties
storeFile=keystore/paperradar-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

The `keystore/` folder is in `.gitignore`. **Never commit it**, and keep a backup. Updates to installed copies must be signed with the same key. Without this file, `assembleRelease` produces an unsigned APK.

### Project layout

```
app/src/main/java/com/example/relevantreasearchupdates/
├── data/            Room entities & DAOs (Paper, Watch), settings, retention
├── network/         ArxivClient, CrossrefClient, FileDownloader
├── work/            LiteratureCheckWorker, WorkScheduler, StaleMatchCleaner
├── notifications/   NotificationHelper
├── util/            AuthorMatcher, KeywordMatcher, EzproxyUrlBuilder
└── ui/              feed, watches, paperdetail, pdfviewer, settings, navigation, theme
```

---

## Privacy

PaperRadar has no accounts, analytics or backend server. Your watches, saved papers and settings stay on your device. The app only connects to arXiv, Crossref, and the publisher or proxy pages you choose to open.

---

## Acknowledgements

Paper metadata comes from the [arXiv API](https://info.arxiv.org/help/api/) and the [Crossref REST API](https://www.crossref.org/documentation/retrieve-metadata/rest-api/). Thank you to arXiv for use of its open access interoperability.
