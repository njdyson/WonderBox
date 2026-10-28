package com.njdyson.wonderbox.games

import kotlin.random.Random

/** A country and its flag, a picture on the server at [picture]. */
data class Country(val code: String, val name: String, val region: String) {
    val picture get() = "/clipart/flags/$code.png"
}

/**
 * The flag game: see a flag and pick the country, or hear a country and pick its flag. The
 * flags are the clipart picker's (server/pb_public/clipart/flags, from tools/make_flags.js).
 */
internal object FlagQuestions {
    /** The UK's four nations and Ireland: where to start. */
    val UK = listOf("gb", "gb-eng", "gb-sct", "gb-wls", "gb-nir", "ie")

    /** Flags children are likely to know, or that are easy to tell apart. */
    val FAMOUS = listOf(
        "us", "fr", "de", "it", "es", "ca", "jp", "cn", "au", "br", "in", "mx",
        "gr", "nl", "se", "ch", "kr", "za", "jm", "nz", "pt", "dk",
    )

    /** "uk" and "famous" are lists above; the rest are regions of the world. */
    val REGIONS = listOf("uk", "famous", "europe", "asia", "africa", "americas", "oceania")

    /** The countries with a flag, minus territories that share another country's or aren't countries. */
    val COUNTRIES = listOf(
        Country("af", "Afghanistan", "asia"),
        Country("al", "Albania", "europe"),
        Country("dz", "Algeria", "africa"),
        Country("ad", "Andorra", "europe"),
        Country("ao", "Angola", "africa"),
        Country("ag", "Antigua and Barbuda", "americas"),
        Country("ar", "Argentina", "americas"),
        Country("am", "Armenia", "asia"),
        Country("au", "Australia", "oceania"),
        Country("at", "Austria", "europe"),
        Country("az", "Azerbaijan", "asia"),
        Country("bs", "Bahamas", "americas"),
        Country("bh", "Bahrain", "asia"),
        Country("bd", "Bangladesh", "asia"),
        Country("bb", "Barbados", "americas"),
        Country("by", "Belarus", "europe"),
        Country("be", "Belgium", "europe"),
        Country("bz", "Belize", "americas"),
        Country("bj", "Benin", "africa"),
        Country("bt", "Bhutan", "asia"),
        Country("bo", "Bolivia", "americas"),
        Country("ba", "Bosnia and Herzegovina", "europe"),
        Country("bw", "Botswana", "africa"),
        Country("br", "Brazil", "americas"),
        Country("bn", "Brunei", "asia"),
        Country("bg", "Bulgaria", "europe"),
        Country("bf", "Burkina Faso", "africa"),
        Country("bi", "Burundi", "africa"),
        Country("cv", "Cape Verde", "africa"),
        Country("kh", "Cambodia", "asia"),
        Country("cm", "Cameroon", "africa"),
        Country("ca", "Canada", "americas"),
        Country("cf", "Central African Republic", "africa"),
        Country("td", "Chad", "africa"),
        Country("cl", "Chile", "americas"),
        Country("cn", "China", "asia"),
        Country("co", "Colombia", "americas"),
        Country("km", "Comoros", "africa"),
        Country("cr", "Costa Rica", "americas"),
        Country("ci", "Ivory Coast", "africa"),
        Country("hr", "Croatia", "europe"),
        Country("cu", "Cuba", "americas"),
        Country("cy", "Cyprus", "europe"),
        Country("cz", "Czech Republic", "europe"),
        Country("cd", "DR Congo", "africa"),
        Country("dk", "Denmark", "europe"),
        Country("dj", "Djibouti", "africa"),
        Country("dm", "Dominica", "americas"),
        Country("do", "Dominican Republic", "americas"),
        Country("ec", "Ecuador", "americas"),
        Country("eg", "Egypt", "africa"),
        Country("sv", "El Salvador", "americas"),
        Country("gb-eng", "England", "europe"),
        Country("gq", "Equatorial Guinea", "africa"),
        Country("er", "Eritrea", "africa"),
        Country("ee", "Estonia", "europe"),
        Country("sz", "Eswatini", "africa"),
        Country("et", "Ethiopia", "africa"),
        Country("fm", "Micronesia", "oceania"),
        Country("fj", "Fiji", "oceania"),
        Country("fi", "Finland", "europe"),
        Country("fr", "France", "europe"),
        Country("ga", "Gabon", "africa"),
        Country("gm", "Gambia", "africa"),
        Country("ge", "Georgia", "asia"),
        Country("de", "Germany", "europe"),
        Country("gh", "Ghana", "africa"),
        Country("gr", "Greece", "europe"),
        Country("gd", "Grenada", "americas"),
        Country("gt", "Guatemala", "americas"),
        Country("gn", "Guinea", "africa"),
        Country("gw", "Guinea-Bissau", "africa"),
        Country("gy", "Guyana", "americas"),
        Country("ht", "Haiti", "americas"),
        Country("va", "Vatican City", "europe"),
        Country("hn", "Honduras", "americas"),
        Country("hu", "Hungary", "europe"),
        Country("is", "Iceland", "europe"),
        Country("in", "India", "asia"),
        Country("id", "Indonesia", "asia"),
        Country("ir", "Iran", "asia"),
        Country("iq", "Iraq", "asia"),
        Country("ie", "Ireland", "europe"),
        Country("il", "Israel", "asia"),
        Country("it", "Italy", "europe"),
        Country("jm", "Jamaica", "americas"),
        Country("jp", "Japan", "asia"),
        Country("jo", "Jordan", "asia"),
        Country("kz", "Kazakhstan", "asia"),
        Country("ke", "Kenya", "africa"),
        Country("ki", "Kiribati", "oceania"),
        Country("xk", "Kosovo", "europe"),
        Country("kw", "Kuwait", "asia"),
        Country("kg", "Kyrgyzstan", "asia"),
        Country("la", "Laos", "asia"),
        Country("lv", "Latvia", "europe"),
        Country("lb", "Lebanon", "asia"),
        Country("ls", "Lesotho", "africa"),
        Country("lr", "Liberia", "africa"),
        Country("ly", "Libya", "africa"),
        Country("li", "Liechtenstein", "europe"),
        Country("lt", "Lithuania", "europe"),
        Country("lu", "Luxembourg", "europe"),
        Country("mg", "Madagascar", "africa"),
        Country("mw", "Malawi", "africa"),
        Country("my", "Malaysia", "asia"),
        Country("mv", "Maldives", "asia"),
        Country("ml", "Mali", "africa"),
        Country("mt", "Malta", "europe"),
        Country("mh", "Marshall Islands", "oceania"),
        Country("mr", "Mauritania", "africa"),
        Country("mu", "Mauritius", "africa"),
        Country("mx", "Mexico", "americas"),
        Country("md", "Moldova", "europe"),
        Country("mc", "Monaco", "europe"),
        Country("mn", "Mongolia", "asia"),
        Country("me", "Montenegro", "europe"),
        Country("ma", "Morocco", "africa"),
        Country("mz", "Mozambique", "africa"),
        Country("mm", "Myanmar", "asia"),
        Country("na", "Namibia", "africa"),
        Country("nr", "Nauru", "oceania"),
        Country("np", "Nepal", "asia"),
        Country("nl", "Netherlands", "europe"),
        Country("nz", "New Zealand", "oceania"),
        Country("ni", "Nicaragua", "americas"),
        Country("ne", "Niger", "africa"),
        Country("ng", "Nigeria", "africa"),
        Country("kp", "North Korea", "asia"),
        Country("mk", "North Macedonia", "europe"),
        Country("gb-nir", "Northern Ireland", "europe"),
        Country("no", "Norway", "europe"),
        Country("om", "Oman", "asia"),
        Country("pk", "Pakistan", "asia"),
        Country("pw", "Palau", "oceania"),
        Country("pa", "Panama", "americas"),
        Country("pg", "Papua New Guinea", "oceania"),
        Country("py", "Paraguay", "americas"),
        Country("pe", "Peru", "americas"),
        Country("ph", "Philippines", "asia"),
        Country("pl", "Poland", "europe"),
        Country("pt", "Portugal", "europe"),
        Country("qa", "Qatar", "asia"),
        Country("cg", "Congo", "africa"),
        Country("ro", "Romania", "europe"),
        Country("ru", "Russia", "europe"),
        Country("rw", "Rwanda", "africa"),
        Country("kn", "St Kitts and Nevis", "americas"),
        Country("lc", "St Lucia", "americas"),
        Country("vc", "St Vincent", "americas"),
        Country("ws", "Samoa", "oceania"),
        Country("sm", "San Marino", "europe"),
        Country("st", "São Tomé and Príncipe", "africa"),
        Country("sa", "Saudi Arabia", "asia"),
        Country("gb-sct", "Scotland", "europe"),
        Country("sn", "Senegal", "africa"),
        Country("rs", "Serbia", "europe"),
        Country("sc", "Seychelles", "africa"),
        Country("sl", "Sierra Leone", "africa"),
        Country("sg", "Singapore", "asia"),
        Country("sk", "Slovakia", "europe"),
        Country("si", "Slovenia", "europe"),
        Country("sb", "Solomon Islands", "oceania"),
        Country("so", "Somalia", "africa"),
        Country("za", "South Africa", "africa"),
        Country("kr", "South Korea", "asia"),
        Country("ss", "South Sudan", "africa"),
        Country("es", "Spain", "europe"),
        Country("lk", "Sri Lanka", "asia"),
        Country("ps", "Palestine", "asia"),
        Country("sd", "Sudan", "africa"),
        Country("sr", "Suriname", "americas"),
        Country("se", "Sweden", "europe"),
        Country("ch", "Switzerland", "europe"),
        Country("sy", "Syria", "asia"),
        Country("tw", "Taiwan", "asia"),
        Country("tj", "Tajikistan", "asia"),
        Country("tz", "Tanzania", "africa"),
        Country("th", "Thailand", "asia"),
        Country("tl", "East Timor", "asia"),
        Country("tg", "Togo", "africa"),
        Country("to", "Tonga", "oceania"),
        Country("tt", "Trinidad and Tobago", "americas"),
        Country("tn", "Tunisia", "africa"),
        Country("tr", "Türkiye", "asia"),
        Country("tm", "Turkmenistan", "asia"),
        Country("tv", "Tuvalu", "oceania"),
        Country("ug", "Uganda", "africa"),
        Country("ua", "Ukraine", "europe"),
        Country("ae", "United Arab Emirates", "asia"),
        Country("gb", "United Kingdom", "europe"),
        Country("us", "United States", "americas"),
        Country("uy", "Uruguay", "americas"),
        Country("uz", "Uzbekistan", "asia"),
        Country("vu", "Vanuatu", "oceania"),
        Country("ve", "Venezuela", "americas"),
        Country("vn", "Vietnam", "asia"),
        Country("gb-wls", "Wales", "europe"),
        Country("ye", "Yemen", "asia"),
        Country("zm", "Zambia", "africa"),
        Country("zw", "Zimbabwe", "africa"),
    )

    private val byCode = COUNTRIES.associateBy { it.code }

    /** The countries in [regions]. */
    fun pool(regions: List<String>): List<Country> = regions.flatMap { region ->
        when (region) {
            "uk" -> UK.mapNotNull { byCode[it] }
            "famous" -> FAMOUS.mapNotNull { byCode[it] }
            else -> COUNTRIES.filter { it.region == region }
        }
    }.distinct()

    fun question(setup: FlagSetup, random: Random): Question {
        val pool = pool(setup.regions.filter { it in REGIONS }).ifEmpty { pool(listOf("uk")) }
        val count = setup.choices.coerceIn(2, 4)
        val country = pool.random(random)
        // Wrong answers from the same set where there are enough, so they're as hard as the level.
        val nearby = (pool - country).shuffled(random)
        val others = (nearby + (COUNTRIES - country - nearby.toSet()).shuffled(random)).take(count - 1)
        val choices = (others + country).shuffled(random)
        val type = setup.types.filter { it in TYPES }.ifEmpty { listOf("name") }.random(random)
        return if (type == "flag") {
            Question(
                prompt = country.name,
                spoken = "Which one is the flag of ${country.name}?",
                choices = choices.map { "" },
                answer = country.name,
                choicePictures = choices.map { it.picture },
                choiceNames = choices.map { it.name },
                answerIndex = choices.indexOf(country),
                kind = "flag",
                topic = country.code,
            )
        } else {
            Question(
                prompt = "",
                spoken = "Which country's flag is this?",
                choices = choices.map { it.name },
                answer = country.name,
                picture = country.picture,
                kind = "flagname",
                topic = country.code,
            )
        }
    }

    private val TYPES = setOf("name", "flag")
}
