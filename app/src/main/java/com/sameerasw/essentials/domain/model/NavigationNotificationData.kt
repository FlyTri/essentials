package com.sameerasw.essentials.domain.model

import android.app.PendingIntent
import android.graphics.Bitmap

data class NavigationNotificationData(
    val key: String,
    val packageName: String,
    val instruction: String?,
    val distance: String?,
    val eta: String?,
    // Turn arrow, usually the notification's large icon.
    val maneuver: Bitmap?,
    val contentIntent: PendingIntent?,
    val exitIntent: PendingIntent?,
    val postTime: Long,
)

object NavigationParser {
    data class Parsed(val instruction: String?, val distance: String?, val eta: String?)

    private val distanceRegex = Regex("""\d[\d.,]*\s?(km|m|mi|ft|yd)\b""", RegexOption.IGNORE_CASE)
    private val exitRegex = Regex(
        """\b(exit|stop|end|close|quit|salir|terminar|detener|finalizar|beenden|quitter|arrêter|sair|encerrar)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun isDistance(s: String?) = s != null && s.length <= 24 && distanceRegex.containsMatchIn(s)

    // Apps put the distance in either the title or the text, so take whichever looks like one.
    // `chip` is the Live Update status-bar text, which navigation apps fill with the distance.
    fun parse(title: String?, text: String?, subText: String?, chip: String?): Parsed {
        val titleIsDistance = isDistance(title)
        val textIsDistance = !titleIsDistance && isDistance(text)
        val instruction = when {
            titleIsDistance -> text
            textIsDistance -> title
            else -> title ?: text
        }
        val leftover = if (!titleIsDistance && !textIsDistance && title != null) text else null
        val distance = chip?.takeIf { it.isNotBlank() } ?: when {
            titleIsDistance -> title
            textIsDistance -> text
            else -> null
        }
        return Parsed(
            instruction = instruction?.takeIf { it.isNotBlank() },
            distance = distance,
            eta = (subText ?: leftover)?.takeIf { it.isNotBlank() },
        )
    }

    private val meterRegex = Regex("""(\d{1,3}(?:,\d{3})+|\d+(?:[.,]\d+)?)\s?(km|m|mi|ft|yd)\b""", RegexOption.IGNORE_CASE)
    private val unitMeters = mapOf("km" to 1000.0, "m" to 1.0, "mi" to 1609.344, "ft" to 0.3048, "yd" to 0.9144)

    // "1,200 ft" uses a thousands separator; "1,2 km" uses a decimal comma.
    fun meters(distance: String?): Double? {
        val match = meterRegex.find(distance ?: return null) ?: return null
        val (number, unit) = match.destructured
        val value = if (Regex("""\d{1,3}(,\d{3})+""").matches(number)) number.replace(",", "") else number.replace(',', '.')
        return value.toDoubleOrNull()?.times(unitMeters.getValue(unit.lowercase()))
    }

    // ponytail: matches the button label; an app with several unlabeled-looking actions gets no Exit button
    fun exitActionIndex(titles: List<String?>): Int {
        val match = titles.indexOfFirst { it != null && exitRegex.containsMatchIn(it) }
        return when {
            match >= 0 -> match
            titles.size == 1 -> 0
            else -> -1
        }
    }
}
