# Wonder Box

A Fire TV Stick app for two children, with a phone app for the parent. The parent picks
YouTube videos on the phone; the children watch them on the TV, and nothing else from
YouTube can be reached. Whole channels can be added, music gets its own row, and "treat"
videos are unlocked by finishing a maths or spelling game.
Family photos from the phone show on the TV as a slideshow.

| Folder | What it is |
|---|---|
| `app/` | The TV app: Kotlin and Jetpack Compose for TV, sideloaded. |
| `server/` | The parent server: PocketBase with the API in `pb_hooks` and the phone app in `pb_public`. See [server/README.md](server/README.md). |
| `.github/workflows/build-apk.yml` | Builds the APK on every push to `main` and publishes it to the `apk-latest` release. Pull requests are compiled too, without publishing. |

## The TV app

- **Link:** on first start the TV shows a code and a QR code. The parent scans it, signs in
  to the phone app and approves it. Check for update is on this screen too.
- **Who's watching?** one big, bobbing face per child: their own picture (uploaded in the
  phone app) or an animal. The screen turns the focused child's colour and their name is
  read aloud.
- **A child's screen:** rows, like a TV app, on a screen of their own colour so they can tell
  it's theirs. First the games (Maths, Spelling and any quizzes the parent wrote for them),
  then
  their videos, their music, and a row for each channel. Treats come last, locked: finishing
  a game (10 right answers by default) unlocks one, and the child picks any treat.
- **Games:** answers are big buttons picked with the remote, and every question is read
  aloud. A wrong answer greys out and they try again. Maths has six levels (counting to 5 up
  to the 2, 5 and 10 times tables), spelling five (first letter up to spelling the whole
  word). In the phone app each child gets a level, or their own mix: which kinds of question
  (counting, adding, taking away, missing numbers, biggest number, times tables; first or
  missing letter, which is spelt right, spell the word), numbers up to 5 to 100, which times
  tables, how many answers to pick from, and their own spelling words (school spellings,
  names) with pictures. Maths or spelling can be turned off for a child.
- **Quizzes:** the parent writes multiple-choice quizzes in the phone app, each question
  with 2 to 4 answers in words, pictures (Fluent Emoji clipart, an uploaded picture, or an
  emoji) or both. Each quiz is a tile next to Maths and Spelling for the children it's for;
  answering every question wins it (and a treat). Answers are shuffled and read out as
  they're focused.
- **Jigsaws:** a Jigsaw tile (once there are family photos) turns a photo the child picks
  into a jumbled jigsaw, 2 x 2 up to 5 x 5 pieces (set per child in the phone app, 3 x 3 by
  default, or off). Played with just the arrows and OK: OK picks a piece up, the arrows carry
  it, OK puts it down and it swaps with the piece there. Pieces click into place with a soft
  chime and stay put, the whole picture is shown alongside, and after a while a carried
  piece's place is hinted. Finishing one wins a treat. Back puts a carried piece down.
- **Photos** (a row on each child's screen, before the treats, once there are any; every
  child sees the same ones): OK on a photo shows it full screen. Left and right flip through
  them, OK plays or stops a slideshow, Back goes back to the child's screen.
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

The unit tests include `ScreenshotsTest`, which renders the main TV screens to
`app/build/screenshots/` (Robolectric and Roborazzi, no emulator needed). The Build APK
workflow uploads them as the `tv-screenshots` artifact, so every pull request comes with
pictures of the TV.

## Artwork and fonts

- Avatar animals: [Fluent Emoji](https://github.com/microsoft/fluentui-emoji) (3D style) by
  Microsoft, MIT licence. Copies in `app/src/main/assets/avatars/` and
  `server/pb_public/avatars/`, named by code point.
- Font: [Fredoka](https://fonts.google.com/specimen/Fredoka), SIL Open Font Licence, in
  `app/src/main/res/font/` (medium and bold cut from the variable font).
- Licence texts ship in the APK under `assets/licences/`.
