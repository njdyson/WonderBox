package com.njdyson.wonderbox.games

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** The Stories tile's [GameEntry.key]: StoryLibraryScreen, which makes new stories (StoryScreen) and keeps old ones. */
const val STORY = "story"

/**
 * A child's story maker, set in the phone app: how old they are (each part is longer and a
 * little harder for an older child) and how many pictures they pick in a story.
 */
@Serializable
data class StorySetup(
    val on: Boolean = true,
    val age: Int = 5,
    val turns: Int = 5,
) {
    companion object {
        val AGES = 3..9
        val TURNS = 3..10
    }
}

/** A picture the child can pick: the word the story is told about, and its bundled picture. */
data class StoryPick(val word: String, val picture: String)

/** A part of the story: the picture picked for it and what it says. [ai] when Gemini wrote it. */
data class StoryPart(val pick: StoryPick, val text: String, val ai: Boolean)

/**
 * What the child picks from, part by part: who the story is about, where they go, what they
 * find, and how it ends. All of them have a picture in assets/avatars.
 */
object StoryPictures {
    val HEROES = listOf(
        "cat" to "🐱", "dog" to "🐶", "rabbit" to "🐇", "bear" to "🐻", "lion" to "🦁", "fox" to "🦊",
        "owl" to "🦉", "penguin" to "🐧", "dragon" to "🐉", "unicorn" to "🦄", "dinosaur" to "🦖", "monkey" to "🐒",
        "elephant" to "🐘", "hedgehog" to "🦔", "frog" to "🐸", "turtle" to "🐢", "butterfly" to "🦋", "ladybird" to "🐞",
        "panda" to "🐼", "tiger" to "🐯", "duck" to "🦆", "pig" to "🐷", "mouse" to "🐭", "octopus" to "🐙",
        "dolphin" to "🐬", "koala" to "🐨", "snail" to "🐌", "giraffe" to "🦒", "sloth" to "🦥", "squirrel" to "🐿️",
        "hamster" to "🐹", "horse" to "🐴", "zebra" to "🦓", "kangaroo" to "🦘", "raccoon" to "🦝", "otter" to "🦦",
        "beaver" to "🦫", "flamingo" to "🦩", "peacock" to "🦚", "swan" to "🦢", "hen" to "🐔", "seal" to "🦭",
        "llama" to "🦙", "deer" to "🦌", "gorilla" to "🦍", "wolf" to "🐺", "sheep" to "🐑", "goat" to "🐐",
        "cow" to "🐄", "bat" to "🦇", "shark" to "🦈", "bee" to "🐝", "caterpillar" to "🐛", "moose" to "🫎",
        "donkey" to "🫏", "goose" to "🪿", "badger" to "🦡", "lizard" to "🦎", "mammoth" to "🦣", "whale" to "🐳",
    )

    val PLACES = listOf(
        "castle" to "🏰", "mountain" to "⛰️", "volcano" to "🌋", "desert" to "🏜️", "house" to "🏠", "school" to "🏫",
        "woods" to "🌳", "space" to "🚀", "moon" to "🌙", "rainbow" to "🌈", "ship" to "🚢", "campsite" to "⛺",
        "park" to "⛲", "farm" to "🚜", "snow" to "☃️", "sky" to "☁️", "train" to "🚂", "garden" to "🌷",
        "cave" to "🪨", "river" to "🛶", "pond" to "🪷", "coral reef" to "🪸", "meadow" to "🌼", "snowy hill" to "🛷",
        "stadium" to "🏟️", "little hut" to "🛖", "bakery" to "🍞", "seaside" to "⚓", "jungle" to "🦜", "cactus land" to "🌵",
        "toy shop" to "🧩",
    )

    val THINGS = listOf(
        "key" to "🔑", "balloon" to "🎈", "cake" to "🎂", "kite" to "🪁", "crown" to "👑", "gold coin" to "🪙",
        "map" to "🗺", "umbrella" to "☂️", "magnet" to "🧲", "drum" to "🥁", "guitar" to "🎸", "trumpet" to "🎺",
        "bicycle" to "🚲", "boat" to "⛵", "book" to "📖", "camera" to "📷", "cookie" to "🍪", "pizza" to "🍕",
        "banana" to "🍌", "apple" to "🍎", "cupcake" to "🧁", "popcorn" to "🍿", "lollipop" to "🍭", "teapot" to "🫖",
        "shell" to "🐚", "feather" to "🪶", "star" to "⭐", "snowman" to "☃️", "bubbles" to "🫧", "parcel" to "📦",
        "letter" to "✉️", "ladder" to "🪜", "compass" to "🧭", "mushroom" to "🍄", "owl" to "🦉", "butterfly" to "🦋",
        "ladybird" to "🐞", "puppy" to "🐶", "crab" to "🦀", "whale" to "🐳", "bee" to "🐝", "parrot" to "🦜",
        "ribbon" to "🎀", "backpack" to "🎒", "magic hat" to "🎩", "violin" to "🎻", "tennis ball" to "🎾", "football" to "⚽",
        "basket" to "🧺", "broom" to "🧹", "magnifying glass" to "🔍", "bell" to "🔔", "radio" to "📻", "mirror" to "🪞",
        "shield" to "🛡️", "flute" to "🪈", "maracas" to "🪇", "banjo" to "🪕", "doughnut" to "🍩", "honey" to "🍯",
        "carrot" to "🥕", "cherries" to "🍒", "grapes" to "🍇", "lemon" to "🍋", "coconut" to "🥥", "waffle" to "🧇",
        "acorn" to "🌰", "rose" to "🌹", "log" to "🪵", "bucket" to "🪣", "suitcase" to "🧳", "glasses" to "👓",
        "scarf" to "🧣", "gloves" to "🧤", "socks" to "🧦", "comet" to "☄️", "egg" to "🥚", "ticket" to "🎫",
        "old scroll" to "📜", "crayon" to "🖍️", "pencil" to "✏️", "magic door" to "🚪", "jigsaw" to "🧩", "wool" to "🧶",
    )

    val ENDINGS = listOf(
        "party" to "🎂", "home" to "🏠", "bedtime" to "🛏", "rainbow" to "🌈", "prize" to "🏆", "picnic" to "🧺",
        "music" to "🎵", "balloon ride" to "🎈", "crown" to "👑", "stars" to "⭐", "cookies" to "🍪", "boat trip" to "⛵",
        "tea party" to "🫖", "pancakes" to "🥞", "bubble bath" to "🛁", "parade" to "🥁", "rocket ride" to "🚀", "train ride" to "🚂",
        "hot chocolate" to "☕", "story time" to "📖", "campfire" to "🔥", "pizza party" to "🍕", "concert" to "🎸", "presents" to "🎀",
    )

    /** What [turn] (1 to [turns]) asks for: who, where, what, then how it ends. */
    fun forTurn(turn: Int, turns: Int) = when {
        turn <= 1 -> HEROES
        turn == 2 -> PLACES
        turn >= turns -> ENDINGS
        else -> THINGS
    }

    /** The question for [turn], said aloud and shown over the pictures. */
    fun question(turn: Int, turns: Int) = when {
        turn <= 1 -> "Who is the story about?"
        turn == 2 -> "Where do they go?"
        turn >= turns -> "How does it end?"
        else -> "What happens next?"
    }

    /** Three pictures for [turn], none of them picked already in this story. */
    fun options(turn: Int, turns: Int, picked: List<StoryPick>, random: Random = Random): List<StoryPick> {
        val used = picked.map { it.word }.toSet()
        val pool = forTurn(turn, turns).filter { it.first !in used }.ifEmpty { forTurn(turn, turns) }
        return pool.shuffled(random).take(3).map { (w, p) -> StoryPick(w, p) }
    }
}

/**
 * The TV's own simple story, for when the server can't write one (no Gemini key, no network,
 * or a part that didn't pass the checks). Short and always happy.
 */
object SimpleStory {
    private fun a(word: String) = if (word.first() in "aeiou") "an $word" else "a $word"

    fun part(turn: Int, turns: Int, pick: StoryPick, story: List<StoryPart>, name: String, random: Random = Random): String {
        val hero = story.firstOrNull()?.pick?.word ?: pick.word
        val w = pick.word
        return when {
            turn <= 1 -> listOf(
                "Once upon a time, there was a friendly little $w. Every morning, the $w woke up with a big smile.",
                "Once upon a time, there was ${a(w)} who loved adventures. One sunny day, the $w set off to explore.",
            ).random(random)
            turn == 2 -> when (w) {
                "space", "snow", "home" -> "The $hero went all the way to the $w. Wow, what a place!"
                else -> listOf(
                    "The $hero walked and walked until they came to the $w. It was the most wonderful place!",
                    "Off went the $hero to the $w, humming a happy tune.",
                ).random(random)
            }
            turn >= turns -> when (w) {
                "party" -> "Everyone had a big party with cake and games. The $hero danced and laughed with all their friends."
                "home", "bedtime" -> "At last, the $hero went home, snuggled into bed and dreamed happy dreams. What a day!"
                else -> "The $hero and their friends finished the day with ${a(w)}. It was the best day ever, and they all smiled."
            }
            else -> listOf(
                "Then the $hero found ${a(w)}! \"What a surprise!\" said the $hero.",
                "Suddenly, the $hero spotted ${a(w)}. $name came to help, and they had lots of fun with it.",
                "Along came ${a(w)}, and the $hero laughed and laughed.",
            ).random(random)
        }
    }
}
