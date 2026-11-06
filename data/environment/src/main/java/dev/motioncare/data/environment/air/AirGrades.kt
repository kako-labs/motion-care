package dev.motioncare.data.environment.air

import dev.motioncare.core.model.AirGrade
import kotlin.math.roundToInt

// Upper bounds of 좋음, 보통 and 나쁨 in µg/m³. Anything above the last is 매우나쁨.
// The public table is for daily averages; applying it to one hour is a product judgement (docs/environment.md).
private val PM10_UPPER_BOUNDS = intArrayOf(30, 80, 150)
private val PM25_UPPER_BOUNDS = intArrayOf(15, 35, 75)

/** The worse of the two grades. Values are rounded to whole µg/m³ first, as the table lists integers. */
internal fun airGradeOf(pm10: Double?, pm25: Double?): AirGrade? = listOfNotNull(
    pm10?.let { gradeOf(it, PM10_UPPER_BOUNDS) },
    pm25?.let { gradeOf(it, PM25_UPPER_BOUNDS) },
).maxOrNull()

private fun gradeOf(value: Double, upperBounds: IntArray): AirGrade {
    val rounded = value.roundToInt()
    val step = upperBounds.indexOfFirst { rounded <= it }
    return AirGrade.entries[if (step < 0) upperBounds.size else step]
}
