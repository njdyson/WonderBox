package com.njdyson.wonderbox.games

import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.Library
import kotlin.random.Random

/** The photo jigsaw's [GameEntry.key]; it has its own screen (JigsawScreen), not rounds of questions. */
const val JIGSAW = "jigsaw"

/** The mystery quiz's [GameEntry.key]: rounds that mix maths, spelling, clock and flag questions ([MysterySetup]). */
const val MYSTERY = "mystery"

/**
 * A game on a child's screen: maths, spelling, the clock (unless the parent turned them off),
 * a photo jigsaw, hangman, the word game, memory pairs or one of the parent's quizzes for them.
 * [key] is also the activity log's subject: "maths", "spelling", "clock", [MYSTERY] or "quiz/<id>" ([JIGSAW],
 * [HANGMAN], [SCRAMBLE] and [MEMORY] log their own events).
 */
data class GameEntry(
    val key: String,
    val title: String,
    /** An emoji or a quiz image's path. */
    val icon: String,
    /** "Level 3", "Your own mix", "6 questions": shown under the title. */
    val subtitle: String,
    val game: Game? = null,
    val quiz: Quiz? = null,
) {
    /** Rounds of questions (maths, spelling, the mystery quiz, a parent's quiz) go under Quizzes; the rest, like the jigsaw, under Games. */
    val isQuiz get() = game != null || quiz != null || key == MYSTERY

    /** A new round to play: [Round.goal] right answers wins it. */
    fun newRound(child: Child, random: Random = Random): Round {
        quiz?.let { return quizRound(it, random) }
        val roundSize = child.games.roundSize.coerceIn(1, 50)
        if (key == MYSTERY) return mysteryRound(child, roundSize, random)
        val game = game ?: Game.Maths
        return Round(roundSize, game.levelFor(child)) { game.questionFor(child, random) }
    }

    /**
     * A mystery round: each question from one of the games the parent ticked, at that game's
     * own level. The games come round in a shuffled bag, so they're well mixed and the same
     * one is never asked many times running.
     */
    private fun mysteryRound(child: Child, roundSize: Int, random: Random): Round {
        val games = child.games.mystery.mixed().ifEmpty { Game.entries }
        val bag = mutableListOf<Game>()
        var last: Game? = null
        return Round(roundSize, 0) {
            if (bag.isEmpty()) {
                bag += games.shuffled(random)
                // Not the same game twice in a row across bags.
                if (bag.size > 1 && bag.first() == last) bag.add(bag.removeAt(0))
            }
            val game = bag.removeAt(0)
            last = game
            game.questionFor(child, random).copy(game = game.key)
        }
    }

    private fun quizRound(quiz: Quiz, random: Random): Round {
        val questions = quiz.questions.filter { it.answers.size >= 2 }
        val order = if (quiz.shuffle) questions.shuffled(random) else questions
        var next = 0
        return Round(order.size.coerceAtLeast(1), 0) {
            order[next++ % order.size].toQuestion(random)
        }
    }
}

/**
 * The last few things a child was asked in a game, kept while the app runs, so a word or sum
 * doesn't come up again in the same round or the next one.
 */
class RecentTopics(private val window: Int = WINDOW) {
    // A plain list: ArrayDeque.lastIndexOf gives wrong answers once its buffer wraps around.
    private val topics = mutableListOf<String>()

    /**
     * A question from [make] whose topic hasn't come up recently. When everything has (counting
     * to 5, three of the parent's own words), the one asked longest ago.
     */
    fun fresh(make: () -> Question): Question = fresh(make) { it.topic }

    /** The same for anything with a topic, such as hangman's words. */
    fun <T> fresh(make: () -> T, topicOf: (T) -> String): T {
        var best = make()
        var bestAge = age(topicOf(best))
        var tries = 0
        while (bestAge < Int.MAX_VALUE && tries++ < 80) {
            val q = make()
            val a = age(topicOf(q))
            if (a > bestAge) {
                best = q
                bestAge = a
            }
        }
        topics.add(topicOf(best))
        while (topics.size > window) topics.removeAt(0)
        return best
    }

    /** How many questions ago [topic] was asked: 1 for the last one, MAX_VALUE if not lately. */
    private fun age(topic: String): Int {
        val i = topics.lastIndexOf(topic)
        return if (i < 0) Int.MAX_VALUE else topics.size - i
    }

    companion object {
        const val WINDOW = 20
        private val games = mutableMapOf<String, RecentTopics>()

        /** The one for a child's game ("<child>/<game>"), shared by its rounds. */
        fun forGame(key: String) = synchronized(games) { games.getOrPut(key) { RecentTopics() } }
    }
}

/** [child]'s level at this game, 0 for their own mix. */
fun Game.levelFor(child: Child): Int = when (this) {
    Game.Maths -> child.games.maths().level()
    Game.Spelling -> child.games.spelling().level()
    Game.Clock -> child.games.clock().level()
    Game.Flags -> child.games.flags().level()
}

/**
 * A question from this game as [child]'s setup says, not one they've had lately. The mystery
 * quiz shares each game's recent questions, so it doesn't repeat what they just answered there.
 */
fun Game.questionFor(child: Child, random: Random = Random): Question {
    val recent = RecentTopics.forGame("${child.id}/$key")
    return when (this) {
        Game.Maths -> child.games.maths().let { setup -> recent.fresh { MathsQuestions.question(setup, random) } }
        Game.Spelling -> child.games.spelling().let { setup -> recent.fresh { SpellingQuestions.question(setup, random) } }
        Game.Clock -> child.games.clock().let { setup -> recent.fresh { ClockQuestions.question(setup, random) } }
        Game.Flags -> child.games.flags().let { setup -> recent.fresh { FlagQuestions.question(setup, random) } }
    }
}

/** Questions one after another until [goal] are answered right. [level] is 0 for a parent's own setup or quiz. */
class Round(val goal: Int, val level: Int, private val nextQuestion: () -> Question) {
    fun next() = nextQuestion()
}

/** A quiz question for [GameScreen]'s buttons, its answers in a random order. */
fun QuizQuestion.toQuestion(random: Random = Random): Question {
    val order = answers.indices.shuffled(random)
    return Question(
        prompt = text,
        spoken = text,
        choices = order.map { answers[it].text },
        answer = answers.getOrNull(correct)?.text.orEmpty(),
        picture = picture,
        choicePictures = order.map { answers[it].picture },
        answerIndex = order.indexOf(correct.coerceIn(answers.indices)),
        kind = "quiz",
    )
}

/**
 * The games on [child]'s screen, in order: maths, spelling, the clock, flags, the mystery quiz, hangman, the word game, a jigsaw (when
 * there are [photos] and the child has jigsaws on), memory pairs, the photo quiz (with [photos], when
 * the server can write its questions), stories (the story maker, or just their finished [stories]
 * when it's off), then the parent's quizzes.
 */
fun Library.gamesFor(child: Child, photos: Boolean = false, stories: Boolean = false): List<GameEntry> = buildList {
    val maths = child.games.maths()
    if (maths.on) add(GameEntry("maths", Game.Maths.title, Game.Maths.icon, subtitle(maths.level()), Game.Maths))
    val spelling = child.games.spelling()
    if (spelling.on) add(GameEntry("spelling", Game.Spelling.title, Game.Spelling.icon, subtitle(spelling.level()), Game.Spelling))
    val clock = child.games.clock()
    if (clock.on) add(GameEntry("clock", Game.Clock.title, Game.Clock.icon, subtitle(clock.level()), Game.Clock))
    val flags = child.games.flags()
    if (flags.on) add(GameEntry("flags", Game.Flags.title, Game.Flags.icon, subtitle(flags.level()), Game.Flags))
    val mystery = child.games.mystery.mixed()
    if (child.games.mystery.on && mystery.isNotEmpty()) {
        // Short enough for the tile: the game, the two games, or a bit of everything.
        val names = mystery.map { it.title.lowercase() }
        val subtitle = when (names.size) {
            1 -> "Mystery ${names[0]}"
            2 -> "${mystery[0].title} and ${names[1]}"
            else -> "A bit of everything"
        }
        add(GameEntry(MYSTERY, "Mystery quiz", "🔮", subtitle))
    }
    val hangman = child.games.hangman
    if (hangman.on) add(GameEntry(HANGMAN, "Hangman", "🎈", "Find the word"))
    val scramble = child.games.scramble
    if (scramble.on) {
        add(GameEntry(SCRAMBLE, "Word jumble", "🔠", if (scramble.makeWords) "Make words" else "Unjumble words"))
    }
    if (photos && child.games.jigsaw > 0) {
        val size = child.games.jigsaw.coerceIn(JIGSAW_SIZES.first, JIGSAW_SIZES.last)
        add(GameEntry(JIGSAW, "Jigsaw", "🧩", "$size × $size pieces"))
    }
    val memory = child.games.memory
    if (memory.on) add(GameEntry(MEMORY, "Pairs", "🃏", "${memory.pairs.coerceIn(MemorySetup.PAIRS.first(), MemorySetup.PAIRS.last())} pairs to find"))
    if (photos && photoQuiz && child.games.photoQuiz.on) add(GameEntry(PHOTO_QUIZ, "Photo quiz", "🔍", "Look, then remember"))
    if (child.games.story.on || stories) {
        add(GameEntry(STORY, "Story maker", "✏️", if (child.games.story.on) "Make a story with pictures" else "Hear your stories again"))
    }
    quizzes.filter { child.id in it.children && it.questions.any { q -> q.answers.size >= 2 } }.forEach { quiz ->
        val count = quiz.questions.size
        add(GameEntry("quiz/${quiz.id}", quiz.title.ifBlank { "Quiz" }, quiz.icon.ifBlank { "❓" }, "$count question${if (count == 1) "" else "s"}", quiz = quiz))
    }
}

private fun subtitle(level: Int) = if (level > 0) "Level $level" else "Your own mix"
