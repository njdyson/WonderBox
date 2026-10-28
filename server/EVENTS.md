# The activity log

Everything the children do on the TV can be logged as an **event**, so the phone app can
show stats (what was watched and for how long, how the quizzes went) and later features
have history to build on. One table, `events`, holds every kind; nothing on the server
needs to change to log a new kind of event.

## Logging from the TV

```kotlin
import com.njdyson.wonderbox.data.EventLog
import kotlinx.serialization.json.put

EventLog.log("quiz.answer", child.id, subject = "maths.add", value = 1.0) {
    put("question", "2 + 3")
    put("answer", "5")
}
```

`EventLog.log` returns straight away and never throws. Events are kept in a file on the TV
and sent in batches a few seconds later (and whenever the app comes back to the
foreground), so they survive the network being down. Each event has:

| Field | What it is |
|---|---|
| `type` | Dotted and lower case: `video.play`, `quiz.answer`. Required. |
| `child` | The child using the TV (their id), or none for events that aren't about one child. |
| `subject` | What it was about: a YouTube ID, a game, a photo. Up to 200 characters. |
| `value` | One number worth adding up: seconds, 1 or 0 for right or wrong, a score. |
| `data` | Anything else, as a JSON object (up to 8 KB). Kept as sent. |

The time (`at`) and an id are added by `EventLog`.

## Event types

Use these where they fit, so the stats pick them up; add new ones freely (and to this
list). The stats count every type and add up its `value` even if they know nothing else
about it.

| Type | Subject | Value | Data | When |
|---|---|---|---|---|
| `app.start` | | | | The TV app opens (no child). |
| `profile.pick` | | | | A child picks their face. |
| `video.play` | YouTube ID | | `title`, `channel` | A video starts playing. |
| `video.watch` | YouTube ID | seconds actually playing | `title`, `channel`, `position`, `duration` (seconds), `finished` (true/false), and how smoothly it played: `stalls` (times it stopped to buffer), `frames` and `dropped` (video frames shown and skipped), `quality` (e.g. `1920x1080`), `codec` | The player closes. |
| `channel.open` | channel id | | `name` | A channel is opened. |
| `quiz.answer` | game id: `maths`, `spelling`, `clock`, or `quiz/<id>` for a parent's quiz | 1 right, 0 wrong | `question`, `answer`, `expected`, `level` (0 for the parent's own setup or a quiz), `kind` (`add`, `sub`, `add3`, `missing`, `bonds`, `double`, `half`, `more`, `bigger`, `times`, `count`, `first`, `missing`, `pick`, `build`, `hear`, `hearbuild`, `oclock`, `half`, `quarter`, `five`, `minute` (clock), `quiz`), `title` (quizzes) | Each answer. |
| `quiz.finish` | game id | score (number right) | `total`, `wrong`, `seconds`, `level`, `title` (quizzes) | A round ends. |
| `jigsaw.start` | photo id | | `size` (pieces along each side) | A photo jigsaw starts. |
| `jigsaw.finish` | photo id | number of pieces | `size`, `moves` (swaps), `seconds` | A photo jigsaw is finished. |
| `reward.earn` | `treat` | | `reason` (`quiz.finish` or `jigsaw.finish`), `game` (`jigsaw` for jigsaws) | A finished game round or jigsaw wins a treat to pick (only the games the parent chose). |
| `reward.unlock` | what was unlocked, e.g. a treat video's YouTube ID | | `reason`, `tier` | A treat or reward is unlocked. |
| `photo.view` | photo id | seconds looked at | `album` | A photo is shown. |

## The API

All under `/api/wonder`. TVs send their `X-Device-Key`; the phone app its sign-in token.

- `POST /events` with `{"events": [{id, type, child, subject, value, data, at}, ...]}` (up
  to 500), or one event on its own. Answers `{"saved": n, "skipped": n}`. Events with a bad
  type, a child that isn't this family's, or an id already saved are skipped rather than
  refused, so a retry or one bad event never blocks a TV's queue. TV or phone app.
- `GET /events?child=&type=&since=&limit=` the raw events, newest first. `type=video.`
  (with the dot) means every `video.*` event. Phone app only.
- `GET /stats?days=7&tz=<Date.getTimezoneOffset()>` totals per child over the last few
  days: watch time, plays, answers and right answers, a day-by-day list, top videos and
  channels, each game's answers, rounds and best score (with a quiz's `title`), and `byType` (count and total
  `value` of every event type). Phone app only.
