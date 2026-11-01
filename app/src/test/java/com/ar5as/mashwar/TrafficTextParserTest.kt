package com.ar5as.mashwar
import org.junit.Assert.*
import org.junit.Test
class TrafficTextParserTest {
    @Test fun readsArabicDigitsTimeAndDistance() {
        val parsed=TrafficTextParser.parse("٤٥ دقيقة · ١٢٫٥ كم")
        assertEquals(45.0,parsed.durations.single().value,0.001); assertEquals(12.5,parsed.distances.single().value,0.001)
    }
    @Test fun readsCombinedHoursAndMinutesWithoutDoubleCounting() {
        val parsed=TrafficTextParser.parse("1 hr 20 min (22.3 km)")
        assertEquals(80.0,parsed.durations.single().value,0.001); assertEquals(22.3,parsed.distances.single().value,0.001)
        assertEquals(95.0,TrafficTextParser.parse("١ ساعة و ٣٥ دقيقة").durations.single().value,0.001)
    }
    @Test fun keepsDifferentRouteAlternativesForConfirmation() {
        val parsed=TrafficTextParser.parse("35 min 12 km\n50 min 15 km")
        assertEquals(listOf(35.0,50.0),parsed.durations.map{it.value}); assertEquals(listOf(12.0,15.0),parsed.distances.map{it.value})
    }
    @Test fun rejectsMissingUnitsAndOutOfRangeValues() {
        val parsed=TrafficTextParser.parse("السعر 250 جنيه، 9999 min، 9999 km")
        assertTrue(parsed.durations.isEmpty()); assertTrue(parsed.distances.isEmpty())
    }
}
