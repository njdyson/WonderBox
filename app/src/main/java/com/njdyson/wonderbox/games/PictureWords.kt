package com.njdyson.wonderbox.games

// Made by tools/make_picture_words.py: change that, not this.

/** The spelling games' built-in words, each with a picture bundled in assets/avatars. */
internal object PictureWords {
    /** Three-letter words, like cat. */
    val SHORT = listOf(
        "cat" to "🐱", "dog" to "🐶", "pig" to "🐷", "hen" to "🐔", "bus" to "🚌", "box" to "📦",
        "bat" to "🦇", "bed" to "🛏", "hat" to "🎩", "sun" to "🌞", "van" to "🚐", "web" to "🕸",
        "pen" to "🖊", "map" to "🗺", "fox" to "🦊", "bag" to "👜", "jam" to "🍯", "mug" to "☕",
        "ant" to "🐜", "bug" to "🐛", "cow" to "🐄", "fly" to "🪰", "owl" to "🦉", "ram" to "🐏",
        "rat" to "🐀", "egg" to "🥚", "ice" to "🧊", "jar" to "🫙", "pie" to "🥧", "fog" to "🌫️",
        "hut" to "🛖", "axe" to "🪓", "key" to "🔑",
    )

    /** Four- and five-letter words, like frog. */
    val LONG = listOf(
        "fish" to "🐟", "frog" to "🐸", "duck" to "🦆", "star" to "⭐", "moon" to "🌙", "cake" to "🎂",
        "tree" to "🌳", "ship" to "🚢", "bell" to "🔔", "ball" to "⚽", "bird" to "🐦", "boat" to "⛵",
        "book" to "📖", "lion" to "🦁", "bear" to "🐻", "crab" to "🦀", "snail" to "🐌", "snake" to "🐍",
        "horse" to "🐴", "sheep" to "🐑", "mouse" to "🐭", "apple" to "🍎", "house" to "🏠", "train" to "🚂",
        "clock" to "🕐", "bread" to "🍞", "shell" to "🐚", "whale" to "🐳", "tiger" to "🐯", "pizza" to "🍕",
        "bison" to "🦬", "boar" to "🐗", "camel" to "🐪", "coral" to "🪸", "deer" to "🦌", "dodo" to "🦤",
        "dove" to "🕊️", "eagle" to "🦅", "goat" to "🐐", "goose" to "🪿", "koala" to "🐨", "llama" to "🦙",
        "lotus" to "🪷", "moose" to "🫎", "otter" to "🦦", "panda" to "🐼", "rose" to "🌹", "seal" to "🦭",
        "shark" to "🦈", "skunk" to "🦨", "sloth" to "🦥", "swan" to "🦢", "tulip" to "🌷", "wing" to "🪽",
        "wolf" to "🐺", "worm" to "🪱", "zebra" to "🦓", "bacon" to "🥓", "bagel" to "🥯", "beans" to "🫘",
        "lemon" to "🍋", "lime" to "🍋‍🟩", "mango" to "🥭", "melon" to "🍈", "olive" to "🫒", "onion" to "🧅",
        "peach" to "🍑", "pear" to "🍐", "salt" to "🧂", "spoon" to "🥄", "squid" to "🦑", "sushi" to "🍣",
        "taco" to "🌮", "brick" to "🧱", "canoe" to "🛶", "cloud" to "☁️", "comet" to "☄️", "ferry" to "⛴️",
        "fire" to "🔥", "hotel" to "🏨", "rock" to "🪨", "taxi" to "🚕", "tent" to "⛺", "tram" to "🚊",
        "watch" to "⌚", "wheel" to "🛞", "wood" to "🪵", "kite" to "🪁", "skis" to "🎿", "sled" to "🛷",
        "yarn" to "🧶", "banjo" to "🪕", "broom" to "🧹", "chair" to "🪑", "coat" to "🧥", "coin" to "🪙",
        "crown" to "👑", "door" to "🚪", "dress" to "👗", "drum" to "🥁", "flute" to "🪈", "hook" to "🪝",
        "jeans" to "👖", "purse" to "👛", "radio" to "📻", "ring" to "💍", "scarf" to "🧣", "soap" to "🧼",
        "socks" to "🧦",
    )

    /** Six- to eight-letter words, like rabbit. */
    val BIG = listOf(
        "badger" to "🦡", "beaver" to "🦫", "beetle" to "🪲", "blossom" to "🌼", "bouquet" to "💐", "cactus" to "🌵",
        "chicken" to "🐔", "chipmunk" to "🐿️", "cricket" to "🦗", "dolphin" to "🐬", "donkey" to "🫏", "dragon" to "🐉",
        "elephant" to "🐘", "feather" to "🪶", "flamingo" to "🦩", "giraffe" to "🦒", "gorilla" to "🦍", "hamster" to "🐹",
        "hedgehog" to "🦔", "honeybee" to "🐝", "kangaroo" to "🦘", "leopard" to "🐆", "lizard" to "🦎", "mammoth" to "🦣",
        "monkey" to "🐒", "mosquito" to "🦟", "octopus" to "🐙", "parrot" to "🦜", "peacock" to "🦚", "penguin" to "🐧",
        "poodle" to "🐩", "rabbit" to "🐇", "raccoon" to "🦝", "rooster" to "🐓", "spider" to "🕷️", "turkey" to "🦃",
        "turtle" to "🐢", "unicorn" to "🦄", "avocado" to "🥑", "banana" to "🍌", "broccoli" to "🥦", "burrito" to "🌯",
        "butter" to "🧈", "carrot" to "🥕", "cherries" to "🍒", "chestnut" to "🌰", "coconut" to "🥥", "cookie" to "🍪",
        "cucumber" to "🥒", "cupcake" to "🧁", "custard" to "🍮", "doughnut" to "🍩", "dumpling" to "🥟", "falafel" to "🧆",
        "garlic" to "🧄", "grapes" to "🍇", "lobster" to "🦞", "lollipop" to "🍭", "mushroom" to "🍄", "oyster" to "🦪",
        "pancakes" to "🥞", "peanuts" to "🥜", "popcorn" to "🍿", "potato" to "🥔", "pretzel" to "🥨", "sandwich" to "🥪",
        "teapot" to "🫖", "tomato" to "🍅", "waffle" to "🧇", "anchor" to "⚓", "bicycle" to "🚲", "castle" to "🏰",
        "church" to "⛪", "compass" to "🧭", "cyclone" to "🌀", "desert" to "🏜️", "factory" to "🏭", "fountain" to "⛲",
        "hospital" to "🏥", "luggage" to "🧳", "mosque" to "🕌", "mountain" to "⛰️", "rainbow" to "🌈", "rocket" to "🚀",
        "sailboat" to "⛵", "school" to "🏫", "snowman" to "☃️", "stadium" to "🏟️", "station" to "🚉", "sunrise" to "🌅",
        "sunset" to "🌇", "tornado" to "🌪️", "tractor" to "🚜", "umbrella" to "☂️", "volcano" to "🌋", "wedding" to "💒",
        "balloon" to "🎈", "baseball" to "⚾", "bowling" to "🎳", "joystick" to "🕹️", "ribbon" to "🎀", "tennis" to "🎾",
        "thread" to "🧵", "ticket" to "🎫", "trophy" to "🏆", "abacus" to "🧮", "backpack" to "🎒", "basket" to "🧺",
        "bathtub" to "🛁", "battery" to "🔋", "bookmark" to "🔖", "bubbles" to "🫧", "bucket" to "🪣", "calendar" to "📅",
        "camera" to "📷", "candle" to "🕯️", "crayon" to "🖍️", "envelope" to "✉️", "glasses" to "👓", "gloves" to "🧤",
        "goggles" to "🥽", "guitar" to "🎸", "hammer" to "🔨", "handbag" to "👜", "keyboard" to "⌨️", "kimono" to "👘",
        "ladder" to "🪜", "laptop" to "💻", "lipstick" to "💄", "magnet" to "🧲", "maracas" to "🪇", "mirror" to "🪞",
        "necktie" to "👔", "notebook" to "📓", "package" to "📦", "pencil" to "✏️", "postbox" to "📮", "printer" to "🖨️",
        "scissors" to "✂️", "scroll" to "📜", "shield" to "🛡️", "shorts" to "🩳", "shower" to "🚿", "sponge" to "🧽",
        "toilet" to "🚽", "toolbox" to "🧰", "trumpet" to "🎺", "violin" to "🎻", "window" to "🪟",
    )
}
