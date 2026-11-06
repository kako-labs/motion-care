package dev.motioncare.data.environment.air

import dev.motioncare.core.model.AirGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AirGradesTest {
    @Test fun `pm25 follows the four step table`() {
        assertEquals(AirGrade.GOOD, airGradeOf(pm10 = null, pm25 = 15.0))
        assertEquals(AirGrade.MODERATE, airGradeOf(pm10 = null, pm25 = 16.0))
        assertEquals(AirGrade.MODERATE, airGradeOf(pm10 = null, pm25 = 35.0))
        assertEquals(AirGrade.BAD, airGradeOf(pm10 = null, pm25 = 36.0))
        assertEquals(AirGrade.BAD, airGradeOf(pm10 = null, pm25 = 75.0))
        assertEquals(AirGrade.VERY_BAD, airGradeOf(pm10 = null, pm25 = 76.0))
    }

    @Test fun `pm10 follows the four step table`() {
        assertEquals(AirGrade.GOOD, airGradeOf(pm10 = 30.0, pm25 = null))
        assertEquals(AirGrade.MODERATE, airGradeOf(pm10 = 31.0, pm25 = null))
        assertEquals(AirGrade.MODERATE, airGradeOf(pm10 = 80.0, pm25 = null))
        assertEquals(AirGrade.BAD, airGradeOf(pm10 = 81.0, pm25 = null))
        assertEquals(AirGrade.BAD, airGradeOf(pm10 = 150.0, pm25 = null))
        assertEquals(AirGrade.VERY_BAD, airGradeOf(pm10 = 151.0, pm25 = null))
    }

    @Test fun `decimal values are rounded to whole micrograms first`() {
        assertEquals(AirGrade.GOOD, airGradeOf(pm10 = null, pm25 = 15.4))
        assertEquals(AirGrade.MODERATE, airGradeOf(pm10 = null, pm25 = 15.5))
    }

    @Test fun `the worse pollutant decides`() {
        assertEquals(AirGrade.BAD, airGradeOf(pm10 = 20.0, pm25 = 40.0))
        assertEquals(AirGrade.MODERATE, airGradeOf(pm10 = 50.0, pm25 = 10.0))
    }

    @Test fun `no value gives no grade`() {
        assertNull(airGradeOf(pm10 = null, pm25 = null))
    }
}
