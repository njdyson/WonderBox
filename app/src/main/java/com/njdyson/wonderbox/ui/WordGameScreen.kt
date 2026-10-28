package com.njdyson.wonderbox.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.EventLog
import com.njdyson.wonderbox.games.AutoLevel
import com.njdyson.wonderbox.games.MakeWords
import com.njdyson.wonderbox.games.RecentTopics
import com.njdyson.wonderbox.games.SCRAMBLE
import com.njdyson.wonderbox.games.Unjumble
import com.njdyson.wonderbox.games.WordList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * The word game. Levels 1 and 2: a word's letters are jumbled and the child picks them back
 * into order with the arrows and OK, hearing the word and seeing its picture. Levels 3 to 5:
 * make as many words as you can from a handful of letters, with a cheer for one that uses
 * them all. Finishing wins a treat (when [hasTreats]), like hangman.
 */
@Composable
fun WordGameScreen(
    app: AppState,
    child: Child,
    hasTreats: Boolean,
    onFinished: () -> Unit,
    onPickTreat: () -> Unit,
    onBack: () -> Unit,
    random: Random = Random,
    /** Letters (by position) to pick straight away, and how many words to have found (for screenshots). */
    startPicks: List<Int> = emptyList(),
    startFound: Int = 0,
) {
    val setup = child.games.scramble
    val context = LocalContext.current
    val wordList = remember { WordList.load(context) }
    val recent = remember { RecentTopics.forGame("${child.id}/$SCRAMBLE") }
    var game by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var startedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    // The level the game moved up to after this one, when it's on auto.
    var levelUp by remember { mutableStateOf<Int?>(null) }
    DisposableEffect(Unit) { onDispose { app.stopSpeaking() } }

    fun finish(found: Int, result: AutoLevel.Result, data: JsonObjectBuilder.() -> Unit) {
        EventLog.log("scramble.finish", child.id, subject = SCRAMBLE, value = found.toDouble()) {
            put("level", setup.level)
            put("seconds", (System.currentTimeMillis() - startedAt) / 1000)
            data()
        }
        levelUp = app.autoLevel(child.id, SCRAMBLE, result)?.takeIf { it > setup.level }
        app.sounds.play(Sounds.Effect.Finish, child)
        finished = true
        onFinished()
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Lagoon, Ink)))) {
        if (finished) {
            Finished(child, hasTreats, onPickTreat, onPlayAgain = {
                startedAt = System.currentTimeMillis()
                levelUp = null
                game++
                finished = false
            }, onBack = onBack, app = app, levelUp = levelUp)
        } else {
            key(game) {
                if (setup.makeWords) {
                    MakeWordsGame(app, child, wordList, recent, random, startPicks, startFound, onDone = { round, skips ->
                        finish(round.found.size, AutoLevel.makeWords(round.gotLongest, skips)) {
                            put("letters", round.letters.joinToString(""))
                            put("longest", round.gotLongest)
                        }
                    })
                } else {
                    UnjumbleGame(app, child, recent, random, startPicks, onDone = { missed, tries ->
                        finish(setup.goal, AutoLevel.unjumble(setup.goal, missed, tries)) { put("missed", missed) }
                    })
                }
            }
        }
    }
}

/** Levels 1 and 2: [ScrambleSetup.goal] jumbled words, one after another. */
@Composable
private fun UnjumbleGame(app: AppState, child: Child, recent: RecentTopics, random: Random, startPicks: List<Int>, onDone: (missed: Int, tries: Int) -> Unit) {
    val setup = child.games.scramble
    val words = remember { Unjumble.words(setup, child.games.spelling().myWords) }
    fun nextWord(): Unjumble {
        val (word, picture) = recent.fresh({ words.random(random) }) { it.first }
        return Unjumble.new(word, picture, setup, random)
    }
    var current by remember { mutableStateOf(nextWord()) }
    var found by remember { mutableIntStateOf(0) }
    var missed by remember { mutableIntStateOf(0) }
    var tries by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PlayHeader(child, "🔠", "Word jumble", "") }
            Progress(found, setup.goal)
        }
        key(current) {
            OneWord(app, child, current, setup.say, if (found == 0) startPicks else emptyList(), onOver = { solved ->
                EventLog.log("scramble.word", child.id, subject = current.word, value = if (solved) 1.0 else 0.0) {
                    put("misses", current.misses)
                    put("level", setup.level)
                    put("said", setup.say)
                }
                if (solved) found++ else missed++
                tries += current.misses + current.wrongs
                if (found >= setup.goal) onDone(missed, tries) else current = nextWord()
            })
        }
    }
}

/** One jumbled word: its picture, the gaps, the letters, and buttons to take one back or hear it. */
@Composable
private fun OneWord(app: AppState, child: Child, game: Unjumble, say: Boolean, startPicks: List<Int>, onOver: (solved: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    // Unjumble changes in place; reading this redraws.
    var changes by remember { mutableIntStateOf(0) }
    var wrongs by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    var moved by remember { mutableStateOf(false) }

    fun ask() = app.say(if (say) "Can you spell ${game.word}?" else "Put the letters in order to make a word!")

    LaunchedEffect(Unit) {
        startPicks.forEach { game.pick(it) }
        changes++
        first.requestFocusWhenReady()
        delay(250)
        ask()
    }

    fun pick(tile: Int) {
        if (over) return
        val result = game.pick(tile)
        changes++
        when (result) {
            Unjumble.Pick.Placed -> app.sounds.chime(Chimes.Sound.Pick, child)
            Unjumble.Pick.Again -> app.sounds.chime(Chimes.Sound.Drop, child)
            Unjumble.Pick.Wrong -> {
                wrongs++
                app.sounds.play(Sounds.Effect.Wrong, child)
                app.say(if (wrongs >= 2) "Try the shiny one!" else "Not that one yet. Try another!")
            }
            Unjumble.Pick.Missed -> {
                wrongs++
                app.sounds.play(Sounds.Effect.Wrong, child)
                if (game.misses >= 3) {
                    // Show it, then on to another, as hangman does when the balloons run out.
                    over = true
                    app.say("It was ${game.word}! Let's try another one.")
                    scope.launch {
                        delay(4_000)
                        onOver(false)
                    }
                } else {
                    app.say(if (game.placed.isEmpty()) "Not quite! Try again." else "Nearly! The first letters are right.")
                }
            }
            Unjumble.Pick.Solved -> {
                over = true
                app.sounds.play(Sounds.Effect.Right, child)
                app.say(listOf("Yes! ${game.word}!", "Well done! ${game.word}!", "Brilliant! ${game.word}!").random())
                scope.launch {
                    delay(2_200)
                    onOver(true)
                }
            }
        }
    }

    changes.let { }
    val solved = game.solved
    val built = game.built()
    // After two wrong tries, the next letter to pick glows.
    val hint = if (wrongs >= 2 && !over) game.nextTile() else null
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        // Many phonics words have no picture: then it's just the word said aloud.
        if ((say || over) && game.picture.isNotBlank()) QuizPicture(app, game.picture, 110.dp)
        Gaps(
            if (over && !solved) game.word else built,
            game.word.length,
            color = when {
                solved -> Leaf
                over -> Coral
                else -> Color.White
            },
        )
        Text(
            when {
                solved -> "You did it!"
                over -> "It was ${game.word}"
                else -> "Pick the letters in order"
            },
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            game.tiles.forEachIndexed { i, letter ->
                LetterTile(
                    letter,
                    size = 88,
                    used = game.used(i),
                    glow = i == hint,
                    modifier = (if (i == 0) Modifier.focusRequester(first) else Modifier).onFocusChanged {
                        if (it.isFocused) {
                            if (moved) app.sounds.play(Sounds.Effect.Move, child)
                            moved = true
                        }
                    },
                    onClick = { pick(i) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SmallButton("Take one back") {
                if (!over) {
                    game.back()
                    changes++
                    app.sounds.chime(Chimes.Sound.Drop, child)
                }
            }
            if (say) SmallButton("Hear it again") { ask() }
        }
    }
}

/** Levels 3 to 5: make words from the letters until [MakeWords.goal] are found. */
@Composable
private fun MakeWordsGame(
    app: AppState,
    child: Child,
    words: WordList,
    recent: RecentTopics,
    random: Random,
    startPicks: List<Int>,
    startFound: Int,
    /** The round, and how many times the child asked for new letters before it. */
    onDone: (MakeWords, skips: Int) -> Unit,
) {
    val setup = child.games.scramble
    var skips by remember { mutableIntStateOf(0) }
    var round by remember { mutableStateOf(MakeWords.new(words, setup.letters, setup.goal, random, recent)) }
    var changes by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    var letterOrder by remember(round) { mutableStateOf(round.letters.indices.toList()) }
    var message by remember(round) { mutableStateOf("Make a word with 3 letters or more") }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    var moved by remember { mutableStateOf(false) }

    LaunchedEffect(round) {
        round.possible.takeLast(startFound).forEach { word ->
            word.forEach { c -> round.letters.indices.firstOrNull { round.letters[it] == c && !round.used(it) }?.let(round::pick) }
            round.check()
        }
        startPicks.forEach { round.pick(it) }
        changes++
        first.requestFocusWhenReady()
        delay(250)
        app.say("Make words from these letters! Can you find ${round.goal}?")
    }

    fun check() {
        if (over) return
        val result = round.check()
        changes++
        when (result) {
            MakeWords.Check.TooShort -> {
                app.sounds.chime(Chimes.Sound.Bump, child)
                message = "Words need 3 letters or more"
                app.say("Words need three letters or more.")
            }
            is MakeWords.Check.Again -> {
                app.sounds.chime(Chimes.Sound.Bump, child)
                message = "You've found ${result.word} already"
                app.say("You've found ${result.word} already!")
            }
            is MakeWords.Check.NotAWord -> {
                app.sounds.play(Sounds.Effect.Wrong, child)
                message = "${result.word} isn't one of my words"
                app.say("Hmm, I don't know that word. Try another!")
            }
            is MakeWords.Check.Found -> {
                val word = result.word
                EventLog.log("scramble.found", child.id, subject = word, value = word.length.toDouble()) {
                    put("letters", round.letters.joinToString(""))
                }
                val all = word.length == round.letters.size
                message = if (all) "$word uses every letter! Amazing!" else "You found $word!"
                if (round.done) {
                    over = true
                    app.sounds.play(Sounds.Effect.Right, child)
                    val longest = round.longest
                    app.say(
                        "$word! That's ${round.found.size} words! " +
                            if (round.gotLongest) "And you found the longest word!" else "The longest word was $longest.",
                    )
                    if (!round.gotLongest) message = "The longest word was $longest"
                    scope.launch {
                        delay(4_500)
                        onDone(round, skips)
                    }
                } else {
                    app.sounds.play(if (all) Sounds.Effect.Right else Sounds.Effect.Piece, child)
                    app.say(if (all) "$word! You used every letter! Bonus star!" else "$word!")
                }
            }
        }
    }

    changes.let { }
    val built = round.built()
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PlayHeader(child, "🔠", "Word jumble", "") }
            Progress(round.found.size, round.goal)
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            ) {
                Gaps(built, round.letters.size, Color.White)
                Text(message, color = Color.White.copy(alpha = 0.85f), fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                // Seven letters still fit beside the words found.
                val tile = when (round.letters.size) {
                    in 0..5 -> 84
                    6 -> 72
                    else -> 62
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    letterOrder.forEachIndexed { at, i ->
                        LetterTile(
                            round.letters[i],
                            size = tile,
                            used = round.used(i),
                            glow = false,
                            modifier = (if (at == 0) Modifier.focusRequester(first) else Modifier).onFocusChanged {
                                if (it.isFocused) {
                                    if (moved) app.sounds.play(Sounds.Effect.Move, child)
                                    moved = true
                                }
                            },
                            onClick = {
                                if (!over) {
                                    if (round.pick(i)) app.sounds.chime(Chimes.Sound.Pick, child) else app.sounds.chime(Chimes.Sound.Drop, child)
                                    changes++
                                    // Every letter used: check it straight away.
                                    if (round.placed.size == round.letters.size) check()
                                }
                            },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SmallButton("Is it a word?", strong = true) { check() }
                    SmallButton("Take one back") {
                        if (!over) {
                            round.back()
                            changes++
                            app.sounds.chime(Chimes.Sound.Drop, child)
                        }
                    }
                    SmallButton("Mix up") {
                        if (!over) {
                            letterOrder = letterOrder.shuffled(random)
                            app.sounds.chime(Chimes.Sound.Pick, child)
                        }
                    }
                }
            }
            Column(Modifier.width(290.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Copies: the round changes in place, and a copy is what tells this to redraw.
                FoundWords(round.found.toList(), round.letters.size, round.longest.length, round.gotLongest, Modifier.fillMaxWidth().weight(1f))
                SmallButton("New letters") {
                    if (!over) {
                        EventLog.log("scramble.skip", child.id, subject = round.letters.joinToString(""), value = round.found.size.toDouble())
                        skips++
                        round = MakeWords.new(words, setup.letters, setup.goal, random, recent)
                        changes++
                    }
                }
            }
        }
    }
}

/** The words found so far, and how long the longest one could be. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoundWords(found: List<String>, letters: Int, longest: Int, gotLongest: Boolean, modifier: Modifier) {
    Column(
        modifier.background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Words found", color = Tangerine, fontSize = 24.sp, fontWeight = FontWeight.Black)
        if (found.isEmpty()) {
            Text("None yet!", color = Color.White.copy(alpha = 0.6f), fontSize = 20.sp)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            found.forEach { word ->
                val all = word.length == letters
                Text(
                    word,
                    color = Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(if (all) Tangerine else Leaf, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        Box(Modifier.weight(1f))
        Text(
            if (gotLongest) "You found the longest word!" else "Bonus star for a $longest-letter word",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** The gaps for a [length]-letter word, [shown] filled in from the left. */
@Composable
private fun Gaps(shown: String, length: Int, color: Color) {
    val wide = length > 6
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(length) { i ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    shown.getOrNull(i)?.toString() ?: " ",
                    color = color,
                    fontSize = if (wide) 52.sp else 64.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(if (wide) 50.dp else 62.dp),
                )
                Box(Modifier.width(if (wide) 50.dp else 62.dp).height(6.dp).background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(3.dp)))
            }
        }
    }
}

/**
 * A letter to pick. Never disabled, so it keeps the remote's focus: a [used] one is faded,
 * and picking it again takes it back out of the word. A [glow]ing one is the hint.
 */
@Composable
private fun LetterTile(letter: Char, size: Int, used: Boolean, glow: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val pulse = if (glow) {
        rememberInfiniteTransition(label = "hint").animateFloat(1f, 1.12f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "hint").value
    } else {
        1f
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(size.dp)
            .scale(pulse)
            .alpha(if (used) 0.3f else 1f)
            .then(if (glow) Modifier.border(4.dp, Lime, shape) else Modifier),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (glow) Lime else Color(0xFFFFF4E0),
            contentColor = Ink,
            focusedContainerColor = Tangerine,
            focusedContentColor = Ink,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(4.dp, Color.White), shape = shape)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("$letter", fontSize = (size * 0.55f).sp, fontWeight = FontWeight.Black)
        }
    }
}

/** A word button under the letters: "Take one back", "Is it a word?". */
@Composable
private fun SmallButton(text: String, strong: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (strong) Leaf else Reef,
            contentColor = if (strong) Ink else Color.White,
            focusedContainerColor = Tangerine,
            focusedContentColor = Ink,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
    }
}
