package com.njdyson.wonderbox.games

import com.njdyson.wonderbox.data.Child
import com.njdyson.wonderbox.data.Library
import kotlin.random.Random

/** The photo jigsaw's [GameEntry.key]; it has its own screen (JigsawScreen), not rounds of questions. */
const val JIGSAW = "jigsaw"

/**
 * A game on a child's screen: maths, spelling, the clock (unless the parent turned them off),
 * a photo jigsaw, hangman, the word game, memory pairs or one of the parent's quizzes for them.
 * [key] is also the activity log's subject: "maths", "spelling", "clock" or "quiz/<id>" ([JIGSAW],
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
    /** Rounds of questions (maths, spelling, a parent's quiz) go under Quizzes; the rest, like the jigsaw, under Games. */
    val isQuiz get() = game != null || quiz != null

    /** A new round to play: [Round.goal] right answers wins it. */
    fun newRound(child: Child, random: Random = Random): Round {
        quiz?.let { return quizRound(it, random) }
        val roundSize = child.games.roundSize.coerceIn(1, 50)
        val recent = RecentTopics.forGame("${child.id}/$key")
        return when (game) {
            Game.Spelling -> {
                val setup = child.games.spelling()
                Round(roundSize, setup.level()) { recent.fresh { SpellingQuestions.question(setup, random) } }
            }
            Game.Clock -> {
                val setup = child.games.clock()
                Round(roundSize, setup.level()) { recent.fresh { ClockQuestions.question(setup, random) } }
            }
            Game.Flags -> {
                val setup = child.games.flags()
                Round(roundSize, setup.level()) { recent.fresh { FlagQuestions.question(setup, random) } }
            }
            else -> {
                val setup = child.games.maths()
                Round(roundSize, setup.level()) { recent.fresh { MathsQuestions.question(setup, random) } }
            }
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
 * The games on [child]'s screen, in order: maths, spelling, the clock, hangman, the word game, a jigsaw (when
 * there are [photos] and the child has jigsaws on), memory pairs, the story maker, their finished
 * stories (when they have [stories]), then the parent's quizzes.
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
    if (child.games.story.on) add(GameEntry(STORY, "Story maker", "📖", "Pick pictures, make a story"))
    if (stories) add(GameEntry(STORY_LIBRARY, "My stories", "📚", "Hear your stories again"))
    quizzes.filter { child.id in it.children && it.questions.any { q -> q.answers.size >= 2 } }.forEach { quiz ->
        val count = quiz.questions.size
        add(GameEntry("quiz/${quiz.id}", quiz.title.ifBlank { "Quiz" }, quiz.icon.ifBlank { "❓" }, "$count question${if (count == 1) "" else "s"}", quiz = quiz))
    }
}

private fun subtitle(level: Int) = if (level > 0) "Level $level" else "Your own mix"
