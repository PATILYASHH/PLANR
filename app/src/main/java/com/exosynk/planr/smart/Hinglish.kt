package com.exosynk.planr.smart

/**
 * Rewrites everyday Hinglish (Hindi in Latin letters) into the English phrases
 * [SmartParser] already understands, before parsing:
 *  "kal 9 baje parth ko call karna" → "tomorrow at 9 call parth"
 *  "roz raat 10 baje dawai"         → "every day at 10 pm dawai"
 *  "subah 6 baje alarm laga do"     → "at 6 am set alarm"
 * Only whole words are touched, so English input passes through unchanged.
 */
object Hinglish {

    private val opt = setOf(RegexOption.IGNORE_CASE)

    private val WEEKDAYS = mapOf(
        "somvar" to "monday", "somwar" to "monday",
        "mangalvar" to "tuesday", "mangalwar" to "tuesday",
        "budhvar" to "wednesday", "budhwar" to "wednesday",
        "guruvar" to "thursday", "guruwar" to "thursday", "brihaspativar" to "thursday",
        "shukravar" to "friday", "shukrawar" to "friday",
        "shanivar" to "saturday", "shaniwar" to "saturday",
        "ravivar" to "sunday", "raviwar" to "sunday", "itvar" to "sunday", "itwar" to "sunday",
    )

    /** Words that are never part of a person's name in "<name> ko call". */
    private val NOT_NAME = setOf(
        "kal", "aaj", "parso", "parson", "subah", "sawere", "savere", "dopahar", "dopehar", "shaam", "sham", "raat",
        "baje", "ko", "mujhe", "muje", "abhi", "roz", "har", "din", "at", "pm", "am", "tomorrow", "today",
        "morning", "evening", "night", "afternoon", "every", "day",
    ) + WEEKDAYS.keys

    private val CALL = Regex("\\bko\\s+(call|phone|fone|ring|message|msg|sms|text|whatsapp|wa)\\b(\\s+(karna|karo|kar\\s*do|kardo|kar|krna|lagana|lagao|bhejna|bhejo))?", opt)

    private val NOTE = Regex("^\\s*(note|notes|jot|jot down|write down|idea)\\s*[:\\-]?\\s+", opt)

    fun normalize(input: String): String {
        // Notes keep their exact words.
        if (NOTE.containsMatchIn(input)) return input.trim()
        var s = " " + input.trim() + " "
        fun sub(pattern: String, to: String) { s = Regex(pattern, opt).replace(s, to) }

        // "<name> ko call karna" → "call <name>" (name = up to two words before "ko")
        while (true) {
            val m = CALL.find(s) ?: break
            val before = s.substring(0, m.range.first).trimEnd()
            val words = before.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val name = mutableListOf<String>()
            for (w in words.asReversed()) {
                if (name.size == 2 || w.lowercase() in NOT_NAME || w.any { it.isDigit() }) break
                name.add(0, w)
            }
            if (name.isEmpty()) break
            val head = words.dropLast(name.size).joinToString(" ")
            s = " $head " + m.groupValues[1].lowercase() + " " + name.joinToString(" ") + " " + s.substring(m.range.last + 1)
        }

        // Time of day + "baje": subah/savere = AM, dopahar/shaam/raat = PM.
        s = Regex("\\b(subah|savere|sawere)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*baje\\b", opt).replace(s) { m -> " at ${m.groupValues[2]}${m.groupValues[3].let { if (it.isEmpty()) "" else ":$it" }} am " }
        s = Regex("\\b(dopahar|dopehar|shaam|sham|raat)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*baje\\b", opt).replace(s) { m -> " at ${m.groupValues[2]}${m.groupValues[3].let { if (it.isEmpty()) "" else ":$it" }} pm " }
        s = Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*baje\\s+(subah|savere|sawere)\\b", opt).replace(s) { m -> " at ${m.groupValues[1]}${m.groupValues[2].let { if (it.isEmpty()) "" else ":$it" }} am " }
        s = Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*baje\\s+(dopahar|dopehar|shaam|sham|raat)\\b", opt).replace(s) { m -> " at ${m.groupValues[1]}${m.groupValues[2].let { if (it.isEmpty()) "" else ":$it" }} pm " }
        s = Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*baje(\\s+(pe|par|ko|tak))?\\b", opt).replace(s) { m -> " at ${m.groupValues[1]}${m.groupValues[2].let { if (it.isEmpty()) "" else ":$it" }} " }

        // Repeats
        sub("\\b(har\\s+din|roz|rozana|roj|daily)\\b", " every day ")
        sub("\\bhar\\s+(hafte|hafta|week)\\b", " every week ")
        sub("\\bhar\\s+(mahine|mahina|month)\\b", " every month ")
        sub("\\bhar\\s+saal\\b", " every year ")
        WEEKDAYS.forEach { (hi, en) ->
            sub("\\bhar\\s+$hi\\b", " every $en ")
            sub("\\b$hi(\\s+(ko|ke\\s+din))?\\b", " $en ")
        }

        // Days
        sub("\\b(parso|parson)\\b", " day after tomorrow ")
        sub("\\bkal\\b", " tomorrow ")
        sub("\\baaj\\s+raat(\\s+ko)?\\b", " tonight ")
        sub("\\baaj\\b", " today ")

        // Parts of the day (when no hour was given)
        sub("\\b(subah|savere|sawere)(\\s+ko)?\\b", " morning ")
        sub("\\b(dopahar|dopehar)(\\s+ko)?\\b", " afternoon ")
        sub("\\b(shaam|sham)(\\s+ko)?\\b", " evening ")
        sub("\\braat(\\s+ko)?\\b", " at night ")

        // Alarms and "remind me"
        // "mujhe … utha dena" / "… jaga dena", wherever the time sits in between
        val wake = Regex("\\b(utha\\s+dena|utha\\s+do|uthana|utha|jaga\\s+dena|jaga\\s+do|jagana|jaga)\\b", opt)
        if (wake.containsMatchIn(s)) s = " wake me " + wake.replace(s, " ")
        sub("\\balarm\\s+(laga\\s*do|lagao|lagana|laga\\s*dena|set\\s+karo|set\\s+kar\\s*do|set\\s+karna)\\b", " set alarm ")
        sub("\\b(yaad\\s+(dilana|dila\\s*do|dilao|dila\\s*dena|rakhna|karwana))\\b", " ")
        sub("\\b(mujhe|muje)\\b", " ")

        return s.replace(Regex("\\s+"), " ").trim()
    }
}
