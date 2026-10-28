# Wonder Box

A Fire TV Stick app for two children, with a phone app for the parent. The parent picks
YouTube videos on the phone; the children watch them on the TV, and nothing else from
YouTube can be reached. Whole channels can be added, music gets its own screen, and "treat"
videos are won by finishing games and saved up to watch later.
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
- **A child's home page:** big tiles on a screen of their own colour so they can tell it's
  theirs: Quizzes (Maths, Spelling, Clock, Flags and the parent's quizzes), Games (jigsaws), Videos, Music
  and Photos, always in that order, leaving out any with nothing in them, then Treats. Each
  opens its own screen; Videos and Music are rows, like a TV app (their own videos, then a
  row for each channel). Treat videos never show there: finishing a game (10 right answers
  by default) wins a treat, and the child picks one to save (or saves the pick for later)
  and carries on learning. Saved treats wait in the Treats section (its tile counts them)
  and watching one uses it up; a won treat not picked yet can be picked there too. The parent chooses which
  games win a treat: maths, spelling, the clock, flags, quizzes and hangman until changed, not
  jigsaws or memory pairs. Those games have a star on their tile.
- **Games:** answers are big buttons picked with the remote, and every question is read
  aloud. A wrong answer greys out and they try again. Maths has six levels (counting to 5 up
  to the 2, 5 and 10 times tables), spelling five (first letter up to spelling the whole
  word). In the phone app each child gets a level, or their own mix: which kinds of question
  (counting, adding, taking away, adding 3 numbers, missing numbers, number bonds, doubles,
  halves, 1 or 10 more and less, biggest number, times tables; first or missing letter,
  which is spelt right, spell the word, and picking or spelling a word that's only said
  aloud), numbers up to 5 to 100, which times
  tables, how many answers to pick from, and their own spelling words (school spellings,
  names) with pictures, or UK phonics Phase 2 words (from the school's sound button sheets),
  shown with sound buttons: a dot under each letter sound and a line under "ck", "ff", "ll"
  and "ss". A "Hear it again" button repeats the question. Maths or spelling
  can be turned off for a child.
- **Clock:** a clock face with both hands; pick the time, said the British way ("quarter
  past 3", "20 to 5"). Five levels, from o'clock to any minute, or a mix of o'clock, half
  past, quarter past and to, five minutes and any minute.
- **Flags:** see a flag and pick the country (names are read out as they're focused), or
  hear a country and pick its flag. Five levels: the UK's nations and Ireland, well-known
  flags, both ways round, Europe, then the whole world; or the parent's own mix of regions.
- **Quizzes:** the parent writes multiple-choice quizzes in the phone app, each question
  with 2 to 4 answers in words, pictures (Fluent Emoji clipart, an uploaded picture, or an
  emoji) or both. Each quiz is a tile next to Maths and Spelling, under Quizzes, for the children it's for;
  answering every question wins it (and a treat). Answers are shuffled and read out as
  they're focused.
- **Jigsaws:** a Jigsaw tile under Games (once there are family photos) turns a photo the child picks
  into a jumbled jigsaw, 2 x 2 up to 5 x 5 pieces (set per child in the phone app, 3 x 3 by
  default, or off). Played with just the arrows and OK: OK picks a piece up, the arrows carry
  it, OK puts it down and it swaps with the piece there. Pieces click into place with a soft
  chime and stay put, the whole picture is shown alongside, and after a while a carried
  piece's place is hinted. Finishing one wins a treat. Back puts a carried piece down.
- **Photos** (a tile on each child's home page once they have any; each photo is for one or
  more children): a grid of them, and OK on one shows it full screen. Left and right flip
  through them, OK plays or stops a slideshow, Back goes back to the grid.
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
