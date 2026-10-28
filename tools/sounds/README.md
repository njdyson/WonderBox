# Game sound effects

The clips in `app/src/main/res/raw/sfx_*.ogg` are all CC0 (public domain, no credit needed;
credited here anyway). Each was made mono, 44.1 kHz Ogg Vorbis (quality 3) and scaled to peak at
-1.5 dB with ffmpeg; the trims are noted below.

| File | Source | By |
| --- | --- | --- |
| sfx_move | Interface Sounds `select_001` | [Kenney](https://kenney.nl/assets/interface-sounds) |
| sfx_right | Interface Sounds `confirmation_001` | Kenney |
| sfx_wrong | Interface Sounds `error_004` | Kenney |
| sfx_finish_silly | Digital Audio `powerUp7` | [Kenney](https://kenney.nl/assets/digital-audio) |
| sfx_silly_4 | Digital Audio `pepSound3` | Kenney |
| sfx_wrong_silly_1 | Digital Audio `lowDown` | Kenney |
| sfx_fart_1 to 5 | [Gastric Distress](https://opengameart.org/content/gastric-distress), cut at 0-1.26 s, 2.09-3.0 s, 10.37-11.14 s, 14.74-15.71 s and 16.98-17.68 s, 0.1 s fade out | LFA |
| sfx_boing_1 | [Boing](https://opengameart.org/content/boing), first 1.6 s, 0.4 s fade out | aeva |
| sfx_boing_2 | [Boings](https://opengameart.org/content/boings) `bung_01` | ezduzziteh |
| sfx_silly_1 to 3, sfx_wrong_silly_2 and 3 | [Silly sound effects](https://opengameart.org/content/silly-sound-effects) `booleh`, `bloop`, `plup`, `uah`, `blah` | ezduzziteh |
| sfx_squeak_1 to 3 | [Squeak toy sounds](https://opengameart.org/content/squeak-toy-sounds) `squeak1`, `squeak2`, `squeak3` | bart |

The calm finish and the jigsaw's pick-up and put-down sounds are synthesised on the TV
(`ui/Chimes.kt`). Which sound plays when is in `ui/Sounds.kt`.
