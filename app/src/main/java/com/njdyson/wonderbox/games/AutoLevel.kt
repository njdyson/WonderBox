package com.njdyson.wonderbox.games

import com.njdyson.wonderbox.data.GameSettings

/**
 * Auto difficulty: a game the parent set to "Auto" moves up a level when the child finds it
 * easy and down when they struggle. Each finished round is judged [Result.Easy], [Result.Ok],
 * [Result.Hard] or [Result.TooHard]; two easy rounds in a row go up, two hard ones (or one where
 * they were mostly guessing) go down. The TV decides and logs `level.change`, which the server
 * saves as the child's new level (pb_hooks/wonderbox_lib.js autoLevel).
 */
object AutoLevel {
    enum class Result(val code: Char) { Easy('E'), Ok('O'), Hard('H'), TooHard('T') }

    /** The games with levels that can be on auto, as their [GameEntry.key]s. */
    val GAMES = listOf("maths", "spelling", "clock", "flags", SCRAMBLE)

    /**
     * A round of questions: [goal] right answers, with [wrong] wrong tries on the way. Picking at
     * random from 3 or 4 answers comes to a wrong try or more for each right one.
     */
    fun quiz(goal: Int, wrong: Int): Result = when {
        wrong <= goal / 10 -> Result.Easy
        wrong >= goal -> Result.TooHard
        wrong * 2 >= goal -> Result.Hard
        else -> Result.Ok
    }

    /** The word game's levels 1 and 2: [words] put right, [missed] shown after 3 tries, [tries] wrong tries in all (full but wrong, or a letter out of order). */
    fun unjumble(words: Int, missed: Int, tries: Int): Result = when {
        missed == 0 && tries <= 1 -> Result.Easy
        missed * 2 >= words.coerceAtLeast(1) -> Result.TooHard
        missed > 0 || tries >= words * 2 -> Result.Hard
        else -> Result.Ok
    }

    /** The word game's levels 3 to 5: found the word using every letter, and how often they asked for new letters. */
    fun makeWords(gotLongest: Boolean, skips: Int): Result = when {
        gotLongest && skips == 0 -> Result.Easy
        skips >= 3 -> Result.TooHard
        skips >= 2 -> Result.Hard
        else -> Result.Ok
    }

    /**
     * The level after a round at [level] (of [levels]) that went as [result], with the rounds
     * before it at this level ([history], oldest first as [Result.code]s). Returns the new level
     * and the history to keep: empty after a change, so each level gets a fair go.
     */
    fun step(level: Int, levels: Int, history: String, result: Result): Pair<Int, String> {
        val recent = (history + result.code).takeLast(2)
        val next = when {
            result == Result.TooHard -> level - 1
            recent == "EE" -> level + 1
            recent.length == 2 && recent.all { it == 'H' || it == 'T' } -> level - 1
            else -> level
        }.coerceIn(1, levels)
        return if (next != level) next to "" else level to recent
    }
}

/** [game]'s level and how many levels it has, when the parent has put it on auto; null otherwise. */
fun GameSettings.autoLevel(game: String): Pair<Int, Int>? = when (game) {
    "maths" -> maths().takeIf { it.auto }?.let { maths.coerceIn(1, MathsSetup.PRESETS.size) to MathsSetup.PRESETS.size }
    "spelling" -> spelling().takeIf { it.auto }?.let { spelling.coerceIn(1, SpellingSetup.PRESETS.size) to SpellingSetup.PRESETS.size }
    "clock" -> clock().takeIf { it.auto }?.let { clock.coerceIn(1, ClockSetup.PRESETS.size) to ClockSetup.PRESETS.size }
    "flags" -> flags().takeIf { it.auto }?.let { flags.coerceIn(1, FlagSetup.PRESETS.size) to FlagSetup.PRESETS.size }
    SCRAMBLE -> scramble.takeIf { it.auto }?.let { it.level.coerceIn(1, ScrambleSetup.LEVELS) to ScrambleSetup.LEVELS }
    else -> null
}

/** These settings with [game] (on auto) moved to [level]'s setup, as the server saves it. */
fun GameSettings.withLevel(game: String, level: Int): GameSettings = when (game) {
    "maths" -> copy(maths = level, mathsSetup = MathsSetup.preset(level).copy(on = maths().on, auto = true))
    "spelling" -> copy(spelling = level, spellingSetup = SpellingSetup.preset(level).copy(on = spelling().on, auto = true, myWords = spelling().myWords))
    "clock" -> copy(clock = level, clockSetup = ClockSetup.preset(level).copy(on = clock().on, auto = true))
    "flags" -> copy(flags = level, flagSetup = FlagSetup.preset(level).copy(on = flags().on, auto = true))
    SCRAMBLE -> copy(scramble = scramble.copy(level = level))
    else -> this
}
