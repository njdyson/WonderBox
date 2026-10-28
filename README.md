# Wonder Box

A Fire TV Stick app for two children, with a phone app for the parent. The parent picks
YouTube videos on the phone; the children watch them on the TV, and nothing else from
YouTube can be reached. Family photos from the phone show as a slideshow on the TV.
Simple learning games come later.

| Folder | What it is |
|---|---|
| `app/` | The TV app: Kotlin and Jetpack Compose for TV, sideloaded. |
| `server/` | The parent server: PocketBase with the API in `pb_hooks` and the phone app in `pb_public`. See [server/README.md](server/README.md). |
| `.github/workflows/build-apk.yml` | Builds the APK on every push to `main` and publishes it to the `apk-latest` release. Pull requests are compiled too, without publishing. |

## The TV app

- **Link:** on first start the TV shows a code and a QR code. The parent scans it, signs in
  to the phone app and approves it. Check for update is on this screen too.
- **Who's watching?** one big face per child. The focused name is read aloud.
- **Videos:** that child's videos as big thumbnails, three across. OK plays.
- **Photos** (a row under each child's videos, once there are any; every child sees the same
  ones): OK on a photo shows it full screen. Left and right flip through them, OK plays or
  stops a slideshow, Back goes back to the child's screen.
- **Player:** full screen. OK pauses, left and right skip 10 seconds, Back closes. Paused
  and finished videos are covered, so YouTube's suggestions never show. A video that won't
  play gets a friendly screen; it never opens the YouTube app.
- **Grown-ups** (small button, top right of the faces screen, behind a sum): Check for
  update, reading titles aloud on or off, refresh, unlink.

## Getting it onto the Fire Stick (once)

1. On the stick: **Settings > My Fire TV > Developer options**, turn on
   **Apps from Unknown Sources** (or allow it for the Downloader app).
2. Install the **Downloader** app, and open
   `https://github.com/njdyson/WonderBox/releases/latest/download/WonderBox.apk`.
3. Install it. From then on, use **Check for update** in the app; it installs over itself
   because every build is signed with the same committed key (`app/debug.keystore`).
   Don't delete or replace that file, or each update will need an uninstall first.

## Building locally

Needs JDK 17 and the Android SDK (`platforms;android-35`, `build-tools;35.0.0`):
```
./gradlew assembleDebug testDebugUnitTest
```
`WONDERBOX_SERVER_URL=http://…` builds an app that talks to a test server instead of
https://wonder.psybob.uk.
