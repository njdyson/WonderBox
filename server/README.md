# Wonder Box server

A [PocketBase](https://pocketbase.io) app (one program, SQLite database) that holds the
children and their videos, serves the phone app, and lets TVs link to the account.

| Folder | What it is |
|---|---|
| `pb_migrations/` | Database setup: `children` (with game levels and setups), `quizzes` and `quiz_images`, `videos`, `channels`, `photos`, `devices` (linked TVs), `pairings` (link codes), `events` (the activity log, see [EVENTS.md](EVENTS.md)). No public sign-up. |
| `pb_hooks/` | The API under `/api/wonder/…`: children, checking and adding videos and channels, family photos (`photos.pb.js`), quizzes and their pictures (`quizzes.pb.js`), linking TVs, the activity log, and a job that fetches channels' new videos every six hours. |
| `pb_public/` | The phone app (PWA): sign in, paste a YouTube video or channel link, choose who it's for, mark it as music or a treat, order and remove, each child's games and the parent's quizzes (`quizzes.js`, with the clipart catalogue in `clipart/`), family photos, stats (`stats.js`), linked TVs. |
| `deploy/` | Install script and the nginx snippet for Plesk. |
| `tests/` | `api_test.py`: checks every route against a running server. |

## Checking videos when they're added

The phone app checks each link before adding it, so the TV never meets a video that
can't play. Without a key it uses YouTube's oEmbed, which refuses private, deleted and
not-embeddable videos. With a **YouTube Data API key** it also refuses age-restricted and
UK-blocked videos and live streams that haven't started. To add one: Google Cloud console,
create a project, enable **YouTube Data API v3**, create an API key (restrict it to that
API), then run the install task again with `YOUTUBE_API_KEY='…'` added (step 4). The key
is kept in the data folder, not in Git.

## Channels and playlists

Paste a channel link (`youtube.com/@name`, `/channel/UC…`, `/c/…` or `/user/…`) and the
channel's newest videos come with it, without Shorts. A playlist link
(`youtube.com/playlist?list=…`) works the same way, with the playlist's videos in its own
order, Shorts included; playlists are kept in the `channels` collection with `kind`
"playlist". A video link with a playlist attached is just that video, and mixes (`list=RD…`)
can't be added because they're different for every viewer. The server fetches new ones every six
hours, or straight away from the channel's ⚙ in the phone app. With the API key it lists
the latest 50 and leaves out any that won't play on the TV. Without it, YouTube's feed only
has the latest 15 (fewer once Shorts are taken out), unchecked; videos seen before are kept,
so the list grows to 50 over time.

## Searching YouTube

Typing words instead of a link searches YouTube (videos, channels or playlists) from the
phone app, with SafeSearch on strict; a result is checked and added like a pasted link.
Search needs the API key. Each search uses 100 of the key's 10,000 free daily units (about
100 searches a day, fewer with channel refreshes), so the server keeps each search's results
for an hour.

## Family photos

The phone app shrinks each photo before sending it: a JPEG at most 1920 pixels on its
longest side (plenty for a 1080p TV) and a 400 pixel thumbnail, typically a few hundred KB
in all instead of several MB. The originals stay on the phone. The files live in the data
folder under `storage/` and are protected: they're only served through
`/api/wonder/photos/{id}/image` to the signed-in parent and their linked TVs, never by a
public link. Every child sees the same photos.

## Games and quizzes

Each child's `games` in the library has their levels and round size, plus `mathsSetup`,
`spellingSetup`, `clockSetup` and `flagSetup`: exactly what each game asks (see `pb_hooks/quizzes_lib.js`). Picking a level
in the phone app sets that level's setup; changing any part makes it the child's own mix.
`POST /api/wonder/children/games` takes any of `{id, maths, spelling, clock, flags, roundSize,
mathsSetup, spellingSetup, clockSetup, flagSetup, treatGames, jigsaw, sounds}`. `treatGames` lists the
games that win a treat (`maths`, `spelling`, `clock`, `flags`, `quizzes`, `hangman`, `memory`,
`jigsaw`); until it's set, all but memory pairs and jigsaws. The clock and `treatGames` are
kept in `children.game_setup` (as `clock`, `clockLevel`, `flags`, `flagLevel` and `treats`). The TV makes the questions up from the setup. `jigsaw` is the
photo jigsaw's pieces along each side, 2 to 5 (3 by default), or 0 for none; it's stored in
`children.jigsaw_size`, where 0 means not set and 1 means off.

Quizzes are in the library as `quizzes`: `{id, title, icon, children, shuffle, questions:
[{text, picture, answers: [{text, picture}], correct}]}`. Save with `POST
/api/wonder/quizzes/save` (with `id` to change one), `…/remove`, `…/move`. A picture is an
emoji or a quiz image's path, `/api/wonder/quiz-images/{id}`, served (protected, like the
photos) to the parent and their TVs. The phone app uploads pictures to
`/api/wonder/quiz-images/add` (multipart `image`, optional `source`): uploads are shrunk to
512 pixels; clipart is copied from Fluent Emoji (jsDelivr) once, by its `source`. Pictures no
quiz or spelling word uses are deleted a day later.

## Setting it up on the Plesk server

Same as BlockbusterTV, with its own subdomain, folder, port (8096) and service (`wonderbox`).
After the one-time setup in step 4, each deploy updates the server by itself: when a
deploy changes the API code or migrations, the service restarts about ten seconds later.
The database lives in a separate folder that deploys never touch.

1. **Subdomain:** **Websites & Domains > Add Subdomain**, `wonderbox` under `psybob.uk`,
   default document root. Make sure DNS has a record for it.
2. **HTTPS:** on the subdomain, **SSL/TLS Certificates > Let's Encrypt**, issue a
   certificate, and turn on the HTTP to HTTPS redirect.
3. **Git:** on the subdomain, **Git > Add Repository**:
   - Remote Git hosting, URL `https://github.com/njdyson/WonderBox.git` (public, no key needed)
   - Deployment mode: **Automatic**, branch **`main`**
   - Deployment path: the subdomain's folder (the default) is fine: nginx sends every
     request to PocketBase, so the files are never served directly.

   To deploy on every push, copy the **Webhook URL** Plesk shows into GitHub: the repo's
   **Settings > Webhooks > Add webhook** (content type `application/json`).
4. **Start the server** (once, as root): **Tools & Settings > Scheduled Tasks > Add Task**,
   system user **root**, **Run a command**, with this command (your email, a new password,
   and optionally the YouTube key), press **Run Now**, then delete the task (it holds the password):
   ```
   ADMIN_EMAIL='you@example.com' ADMIN_PASSWORD='a-new-password' YOUTUBE_API_KEY='' APP_DIR=/var/www/vhosts/psybob.uk/wonder.psybob.uk bash /var/www/vhosts/psybob.uk/wonder.psybob.uk/server/deploy/install.sh
   ```
   It downloads PocketBase, sets it up as a service that starts with the server, adds the
   auto restart after deploys, and creates the dashboard admin. The database goes in
   `/var/www/vhosts/psybob.uk/wonderbox-data`. Running it again is safe.
5. **Connect the subdomain to it:** on the subdomain, **Apache & nginx Settings**, paste
   `deploy/nginx-directives.conf` into **Additional nginx directives**, and save.
6. **Check:** https://wonder.psybob.uk shows the sign-in page, and
   https://wonder.psybob.uk/_/ the PocketBase dashboard. Also open
   https://wonder.psybob.uk/api/wonder/library: it should say "Sign in" **as JSON**. If you
   get the app page instead, the hooks aren't loaded: restart the service
   (`systemctl restart wonderbox`, or run the step 4 task again).
7. **Create your account:** in the dashboard, **Collections > users > New record**
   (email and password). There is no public sign-up.

Recommended in the dashboard (**Settings**): set the application URL, turn on rate limits,
add `X-Real-IP` as the trusted proxy header, and schedule backups.

A different address? Change `SERVER_URL` in `app/build.gradle.kts` (the TV app has it
built in) and the paths above.

## Running it locally

Download PocketBase, then from `server/`:
```
pocketbase migrate up --dir=/tmp/wonderbox-data --hooksDir=pb_hooks --migrationsDir=pb_migrations --publicDir=pb_public
pocketbase superuser upsert admin@example.com somepassword --dir=/tmp/wonderbox-data
pocketbase serve --dir=/tmp/wonderbox-data --hooksDir=pb_hooks --migrationsDir=pb_migrations --publicDir=pb_public
python3 tests/api_test.py http://127.0.0.1:8090 admin@example.com somepassword
```
