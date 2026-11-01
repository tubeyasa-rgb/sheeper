package com.ar5as.mashwar
import org.junit.Assert.*
import org.junit.Test

class JourneyDecisionTest {
    private val now=1_750_000_000_000L
    private val quote=Observation(origin="A",destination="B",provider="P",category="C",price=100.0,captured=now-10_000)
    private val fare=ForecastResult(80.0,90.0,100.0,100,2.0,0.8,List(20){0.0},5)
    private val options=listOf(DepartureOption(5,fare,90.0,95.0,0.7),DepartureOption(10,fare,95.0,105.0,0.7))
    private val eta=TrafficSample(origin="A",destination="B",provider="P",category="C",kind="USER_ESTIMATE",minutes=20.0,recordedAt=now-20_000)
    private val predictions=mapOf(5 to TripTimeForecast(40.0,60,12,2.0),10 to TripTimeForecast(10.0,60,12,2.0))
    @Test fun travelDurationCanChangeBestDeparture() {
        val decision=JourneyDecisionEngine.choose(quote,now,options,1.0,eta,predictions)!!
        assertEquals(10,decision.waitMinutes); assertEquals(5.0,decision.expectedNetSaving,0.001); assertTrue(decision.usesTripDuration)
    }
    @Test fun doesNotSilentlyIgnoreMissingOrStaleTravelData() {
        assertNull(JourneyDecisionEngine.choose(quote,now,options,1.0,null,predictions))
        assertNull(JourneyDecisionEngine.choose(quote,now,options,1.0,eta,predictions - 10))
        assertNull(JourneyDecisionEngine.choose(quote,now,options,1.0,eta.copy(recordedAt=now-180_001),predictions))
        assertNull(JourneyDecisionEngine.choose(quote,now,options,1.0,eta.copy(destination="C"),predictions))
    }
    @Test fun zeroTimeValueUsesFareOnlyAndOldQuoteCannotRecommend() {
        val priceOnly=options.map { it.copy(generalizedCost=it.expectedFare) }
        assertEquals(5,JourneyDecisionEngine.choose(quote,now,priceOnly,0.0,null,emptyMap())!!.waitMinutes)
        assertNull(JourneyDecisionEngine.choose(quote.copy(captured=now-180_001),now,priceOnly,0.0,null,emptyMap()))
    }
}
