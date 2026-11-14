package com.exosynk.planr.smart

import com.exosynk.planr.reminder.ContactAction
import com.exosynk.planr.reminder.Repeat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

enum class SmartKind { REMINDER, ALARM, NOTE }

/** "call to parth" → action CALL, name "parth". */
data class ContactIntent(val action: ContactAction, val nameQuery: String)

data class SmartResult(
    val kind: SmartKind,
    /** Cleaned title, e.g. "Call Parth" (reminder title / alarm label / note title). */
    val title: String,
    /** Note body (notes only). */
    val body: String = "",
    /** When it's due / rings. Null = no time understood. */
    val at: LocalDateTime? = null,
    /** A date but no time: an all-day reminder. */
    val allDay: Boolean = false,
    val repeat: Repeat = Repeat.NONE,
    /** Alarm repeat days (bit 0 = Monday); 0 = once. */
    val alarmDays: Int = 0,
    val contact: ContactIntent? = null,
)

/**
 * Turns everyday phrases into reminders, alarms and notes, offline:
 *  "call to parth at 9 am"           → reminder "Call Parth", today/tomorrow 9:00, contact "parth"
 *  "remind me to pay rent on 1st"    → reminder "Pay rent", 1st of next month, all day
 *  "wake me up at 6:30 weekdays"     → alarm 6:30, Mon–Fri
 *  "meeting tomorrow 4pm every week" → weekly reminder
 *  "in 20 min check the oven"        → reminder 20 minutes from now
 *  "note: gift ideas for mom"        → note
 */
object SmartParser {

    private val opt = setOf(RegexOption.IGNORE_CASE)

    private val WEEKDAYS = mapOf(
        "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY,
    )
    private const val WEEKDAY = "(mon|tue|tues|wed|weds|thu|thur|thurs|fri|sat|sun)(?:day|nesday|sday|urday|rsday)?"
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private const val MONTH = "(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*"
    private const val MERIDIEM = "(am|pm|a\\.m\\.?|p\\.m\\.?)"

    private val NAME_STOP = setOf(
        "about", "for", "regarding", "re", "and", "to", "at", "on", "in", "back", "that", "the", "with",
        "today", "tomorrow", "tonight", "asap", "now", "later", "again", "please",
    )

    fun parse(input: String, now: LocalDateTime = LocalDateTime.now()): SmartResult {
        val text = Hinglish.normalize(input).replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return SmartResult(SmartKind.REMINDER, "")

        // ---- notes: "note: …" / "jot …" ----
        Regex("^(note|notes|jot|jot down|write down|idea)\\s*[:\\-]?\\s+(.+)$", opt).find(text)?.let { m ->
            val content = m.groupValues[2].trim()
            val lines = content.split(Regex("(?<=[.!?])\\s+|\\n"), limit = 2)
            val title = if (content.length <= 60) content else lines.first().take(60)
            val body = if (content.length <= 60) "" else content
            return SmartResult(SmartKind.NOTE, title.capitalizeFirst(), body = body)
        }

        var w = " $text "
        fun cut(r: Regex): MatchResult? {
            val m = r.find(w) ?: return null
            w = w.removeRange(m.range).let { it.substring(0, m.range.first) + " " + it.substring(m.range.first) }
            return m
        }

        // ---- kind ----
        var kind = SmartKind.REMINDER
        if (cut(Regex("\\b(wake me( up)?|set (an |my )?alarm( for)?|alarm( for| at)?)\\b", opt)) != null) kind = SmartKind.ALARM
        cut(Regex("\\b(remind me( to| about| that)?|reminder( to| for)?|don'?t forget( to)?|remember to)\\b", opt))

        // ---- repeat ----
        var repeat = Repeat.NONE
        var alarmDays = 0
        var repeatWeekday: DayOfWeek? = null
        when {
            cut(Regex("\\b(every ?day|daily|each day)\\b", opt)) != null -> { repeat = Repeat.DAILY; alarmDays = 0b1111111 }
            cut(Regex("\\b(every |on )?week ?days?\\b", opt)) != null -> { repeat = Repeat.WEEKDAYS; alarmDays = 0b0011111 }
            cut(Regex("\\b(every |on )?weekends?\\b", opt)) != null -> { repeat = Repeat.WEEKLY; alarmDays = 0b1100000 }
            else -> cut(Regex("\\bevery $WEEKDAY\\b", opt))?.let { m ->
                repeatWeekday = WEEKDAYS[m.groupValues[1].take(3).lowercase()]
                repeat = Repeat.WEEKLY
                alarmDays = 1 shl (repeatWeekday!!.value - 1)
            } ?: run {
                when {
                    cut(Regex("\\b(every week|weekly)\\b", opt)) != null -> repeat = Repeat.WEEKLY
                    cut(Regex("\\b(every month|monthly)\\b", opt)) != null -> repeat = Repeat.MONTHLY
                    cut(Regex("\\b(every year|yearly|annually)\\b", opt)) != null -> repeat = Repeat.YEARLY
                }
            }
        }

        // ---- relative time: "in 20 min", "in an hour", "in half an hour" ----
        var at: LocalDateTime? = null
        cut(Regex("\\bin (\\d{1,3}|a|an|one|half an?|a couple of|couple of) ?(min|mins|minute|minutes|hr|hrs|hour|hours)\\b", opt))?.let { m ->
            val n = when (val q = m.groupValues[1].lowercase()) {
                "a", "an", "one" -> 1.0
                "half a", "half an" -> 0.5
                "a couple of", "couple of" -> 2.0
                else -> q.toDouble()
            }
            val minutes = if (m.groupValues[2].lowercase().startsWith("h")) n * 60 else n
            at = now.plusMinutes(minutes.toLong()).withSecond(0).withNano(0)
        }

        // ---- date ----
        var date: LocalDate? = null
        val today = now.toLocalDate()
        var tonight = false
        when {
            cut(Regex("\\b(the )?day after tomorrow\\b", opt)) != null -> date = today.plusDays(2)
            cut(Regex("\\b(tomorrow|tmrw|tmr|tomo)\\b", opt)) != null -> date = today.plusDays(1)
            cut(Regex("\\btonight\\b", opt)) != null -> { date = today; tonight = true }
            cut(Regex("\\btoday\\b", opt)) != null -> date = today
        }
        if (date == null) {
            cut(Regex("\\b(on |this |next |coming )?$WEEKDAY\\b", opt))?.let { m ->
                val dow = WEEKDAYS[m.groupValues[2].take(3).lowercase()]!!
                val isThis = m.groupValues[1].trim().equals("this", true)
                date = if (isThis && today.dayOfWeek == dow) today else today.with(TemporalAdjusters.next(dow))
            }
        }
        if (date == null) {
            // "25 dec", "25th of december", "dec 25", "december 25th"
            (cut(Regex("\\b(on )?(\\d{1,2})(st|nd|rd|th)?( of)? $MONTH\\b", opt))?.let { m ->
                m.groupValues[2].toInt() to monthIndex(m.groupValues[5])
            } ?: cut(Regex("\\b(on )?$MONTH (\\d{1,2})(st|nd|rd|th)?\\b", opt))?.let { m ->
                m.groupValues[3].toInt() to monthIndex(m.groupValues[2])
            })?.let { (d, mo) -> date = futureDate(today, mo, d) }
        }
        if (date == null) {
            // "on 25/12" (day/month)
            cut(Regex("\\b(on )?(\\d{1,2})/(\\d{1,2})\\b", opt))?.let { m ->
                date = futureDate(today, m.groupValues[3].toInt(), m.groupValues[2].toInt())
            }
        }
        if (date == null) {
            // "on the 1st", "on 15th"
            cut(Regex("\\bon (the )?(\\d{1,2})(st|nd|rd|th)\\b", opt))?.let { m ->
                val d = m.groupValues[2].toInt()
                val thisMonth = runCatching { today.withDayOfMonth(d) }.getOrNull()
                date = if (thisMonth != null && thisMonth.isAfter(today)) thisMonth
                else runCatching { today.plusMonths(1).withDayOfMonth(d) }.getOrNull()
            }
        }
        if (date == null && repeatWeekday != null) date = today.with(TemporalAdjusters.nextOrSame(repeatWeekday!!))

        // ---- time ----
        var time: LocalTime? = null
        var hourOnly: Int? = null // "at 9" with no am/pm: resolved below
        cut(Regex("(\\bat |@ ?)?\\b(\\d{1,2})[:.](\\d{2}) ?$MERIDIEM?(?![\\w/])", opt))?.let { m ->
            time = toTime(m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[4])
            if (time != null && m.groupValues[4].isEmpty() && m.groupValues[2].toInt() in 1..12) hourOnly = m.groupValues[2].toInt()
        }
        if (time == null) {
            cut(Regex("(\\bat |@ ?)?\\b(\\d{1,2}) ?$MERIDIEM(?![\\w])", opt))?.let { m ->
                time = toTime(m.groupValues[2].toInt(), 0, m.groupValues[3])
            }
        }
        if (time == null) {
            cut(Regex("(\\bat |@ ?)(\\d{1,2})( ?o'?clock)?\\b", opt))?.let { m ->
                val h = m.groupValues[2].toInt()
                if (h in 0..23) {
                    time = LocalTime.of(h, 0)
                    if (h in 1..12) hourOnly = h
                }
            }
        }
        if (time == null && kind == SmartKind.ALARM) {
            // "alarm 7", "wake me 6": for alarms a lone number is the hour.
            cut(Regex("\\b(\\d{1,2})\\b", opt))?.let { m ->
                val h = m.groupValues[1].toInt()
                if (h in 0..23) {
                    time = LocalTime.of(h, 0)
                    if (h in 1..12) hourOnly = h
                }
            }
        }
        if (time == null) {
            when {
                cut(Regex("\\b(at )?(noon|midday)\\b", opt)) != null -> time = LocalTime.NOON
                cut(Regex("\\b(at )?midnight\\b", opt)) != null -> time = LocalTime.of(23, 59)
                cut(Regex("\\b(in the |this |tomorrow )?morning\\b", opt)) != null -> time = LocalTime.of(9, 0)
                cut(Regex("\\b(in the |this )?afternoon\\b", opt)) != null -> time = LocalTime.of(14, 0)
                cut(Regex("\\b(in the |this )?evening\\b", opt)) != null -> time = LocalTime.of(18, 0)
                cut(Regex("\\bat night\\b", opt)) != null -> time = LocalTime.of(21, 0)
                tonight -> time = LocalTime.of(20, 0)
            }
        }

        // Hour without am/pm: alarms mean the morning; otherwise the next time it comes round.
        val h = hourOnly
        val t0 = time
        if (h != null && t0 != null) {
            val am = LocalTime.of(h % 12, t0.minute)
            val pm = LocalTime.of(h % 12 + 12, t0.minute)
            time = when {
                kind == SmartKind.ALARM -> am
                tonight -> pm
                date != null && date != today -> if (h in 1..6) pm else am // "tomorrow at 5" → 5 PM
                now.toLocalTime().isBefore(am) -> am
                now.toLocalTime().isBefore(pm) -> pm
                else -> { date = today.plusDays(1); am }
            }
        }

        // ---- combine ----
        var allDay = false
        if (at == null) {
            val tm = time
            val d = date
            when {
                tm != null -> {
                    var candidate = (d ?: today).atTime(tm)
                    if (d == null && !candidate.isAfter(now)) candidate = candidate.plusDays(1)
                    at = candidate
                }
                d != null && kind != SmartKind.ALARM -> {
                    at = d.atStartOfDay()
                    allDay = true
                }
            }
        }

        // ---- contact: "call (to) parth", "message mom", "whatsapp rahul sharma" ----
        var contact: ContactIntent? = null
        var title = w.replace(Regex("\\s+"), " ").trim()
        Regex("\\b(call|phone|ring|dial|message|msg|text|sms|whatsapp|wa|ping)\\s+(to\\s+)?([\\p{L}][\\p{L}'.-]*)(\\s+[\\p{L}][\\p{L}'.-]*)?", opt)
            .find(title)?.let { m ->
                val first = m.groupValues[3]
                if (first.lowercase() in NAME_STOP) return@let
                val second = m.groupValues[4].trim().takeIf { it.isNotEmpty() && it.lowercase() !in NAME_STOP }
                val name = listOfNotNull(first, second).joinToString(" ")
                val action = when (m.groupValues[1].lowercase()) {
                    "message", "msg", "text", "sms", "ping" -> ContactAction.MESSAGE
                    "whatsapp", "wa" -> ContactAction.WHATSAPP
                    else -> ContactAction.CALL
                }
                contact = ContactIntent(action, name)
                // "call to parth about x" → "Call Parth about x"
                val nameEnd = if (second != null) m.range.last + 1 else m.groups[3]!!.range.last + 1
                title = title.substring(0, m.range.first) + action.verb + " " + name.titleCase() + title.substring(nameEnd)
            }
        title = title.replace(Regex("\\s+"), " ").trim(' ', ',', '.', '-', ':', ';').capitalizeFirst()

        return SmartResult(
            kind = kind,
            title = title,
            at = at,
            allDay = allDay,
            repeat = if (kind == SmartKind.ALARM) Repeat.NONE else repeat,
            alarmDays = if (kind == SmartKind.ALARM) alarmDays else 0,
            contact = contact,
        )
    }

    private fun toTime(hour: Int, minute: Int, meridiem: String): LocalTime? {
        if (minute !in 0..59) return null
        val m = meridiem.lowercase().replace(".", "")
        val h = when {
            m.startsWith("a") -> if (hour == 12) 0 else hour
            m.startsWith("p") -> if (hour == 12) 12 else hour + 12
            else -> hour
        }
        return if (h in 0..23 && (m.isEmpty() || hour in 1..12)) LocalTime.of(h, minute) else null
    }

    private fun monthIndex(s: String): Int = MONTHS.indexOf(s.take(3).lowercase()) + 1

    /** That day this year, or next year if it already passed. */
    private fun futureDate(today: LocalDate, month: Int, day: Int): LocalDate? {
        if (month !in 1..12) return null
        val d = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        return if (d.isBefore(today)) runCatching { d.plusYears(1) }.getOrNull() else d
    }

    private fun String.capitalizeFirst() = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    private fun String.titleCase() = split(' ').joinToString(" ") { it.capitalizeFirst() }
}
