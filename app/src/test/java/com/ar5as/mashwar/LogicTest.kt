package com.ar5as.mashwar
import org.junit.Assert.*
import org.junit.Test
class LogicTest {
    private val base = Observation(origin="A",destination="B",provider="one",category="economy",price=100.0,captured=1_000_000)
    @Test fun arabicNumbers() { assertEquals(1234.5,Logic.parsePrice("١٬٢٣٤٫٥٠")!!,0.001); assertNull(Logic.parsePrice("NaN")); assertNull(Logic.parsePrice("-1")) }
    @Test fun freshnessAndFuture() { assertTrue(Logic.fresh(base,1_179_999)); assertFalse(Logic.fresh(base,1_180_001)); assertFalse(Logic.fresh(base,999_999)) }
    @Test fun routeAndClassMustMatch() { assertFalse(Logic.comparable(base,base.copy(destination="C"))); assertFalse(Logic.comparable(base,base.copy(category="premium"))) }
    @Test fun duplicateOnlyWithinNinetySeconds() { assertTrue(Logic.duplicate(base,base.copy(captured=1_090_000))); assertFalse(Logic.duplicate(base,base.copy(captured=1_090_001))) }
    @Test fun lowestDoesNotIncludeStaleOrDifferentRoute() { val other=base.copy(provider="two",price=90.0); assertEquals(other,Logic.lowest(listOf(base,other,base.copy(price=1.0,destination="C")),base,1_000_000)); assertNull(Logic.lowest(listOf(base),base,1_200_000)) }
    @Test fun forecastNeedsCalibratedHistory() {
        assertNull(ForecastEngine.forecast(listOf(base),base,base.captured+1000))
        val history=(0 until 60).map { base.copy(captured=1_700_000_000_000L + it*86_400_000L) }
        val result=ForecastEngine.forecast(history,base,1_700_000_000_000L + 61*86_400_000L)
        assertNotNull(result); assertEquals(100.0,result!!.median,0.001); assertTrue(result.low<=result.median && result.median<=result.high)
    }
    @Test fun forecastsEachDepartureHorizonWithoutInventingHourlyChanges() {
        val zone=java.time.ZoneId.of("Africa/Cairo")
        val target=java.time.ZonedDateTime.of(2026,10,30,12,5,0,0,zone).toInstant().toEpochMilli()
        val history=(1..60).map { base.copy(captured=target-it*86_400_000L) }
        val results=listOf(0,5,10,15,30).map { ForecastEngine.forecast(history,base,target+it*60_000L) }
        assertTrue(results.all { it!=null }); assertTrue(results.all { it!!.median==100.0 })
        assertNull(ForecastEngine.forecast(history,base.copy(provider="another"),target))
    }
    @Test fun noDataAbstains() { assertTrue(Logic.forecastMessage(emptyList()).contains("لا يوجد")) }
}
