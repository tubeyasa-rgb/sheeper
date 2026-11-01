package com.ar5as.mashwar
import org.junit.Assert.*
import org.junit.Test
class TripEvaluationTest {
    private val now=1_750_000_000_000L
    private val day=86_400_000L
    private val quote=Observation(id=20,origin="A",destination="B",provider="P",category="C",price=120.0,captured=now-30_000)
    private val route=TrafficSample(origin="A",destination="B",provider="P",category="C",kind="USER_ESTIMATE",minutes=25.0,recordedAt=now-20_000,distanceKm=10.0,inputSource="CONFIRMED_IMAGE")
    @Test fun computesFarePerKilometreAndComparableHistoricalDifference() {
        val history=(1..10).map { quote.copy(id=it.toLong(),price=100.0,captured=now-it*day) }
        val result=TripEvaluation.evaluate(history,quote,route,now)!!
        assertEquals(12.0,result.pricePerKm!!,0.001); assertEquals(100.0,result.historicalMedian!!,0.001); assertEquals(20.0,result.percentDifference!!,0.001)
    }
    @Test fun doesNotUseStaleOrDifferentRouteDistance() {
        assertNull(TripEvaluation.evaluate(emptyList(),quote,route.copy(recordedAt=now-180_001),now)!!.pricePerKm)
        assertNull(TripEvaluation.evaluate(emptyList(),quote,route.copy(destination="C"),now)!!.pricePerKm)
        assertNull(TripEvaluation.evaluate(emptyList(),quote.copy(captured=now-180_001),route,now))
        assertNull(TripEvaluation.evaluate(emptyList(),quote,route.copy(distanceKm=0.0),now)!!.pricePerKm)
    }
    @Test fun invalidOrUnrepresentableNumbersDoNotAppearAsEstimates() {
        assertNull(TripEvaluation.evaluate(emptyList(),quote.copy(price=Double.NaN),route,now))
        assertNull(TripEvaluation.evaluate(emptyList(),quote,route.copy(distanceKm=Double.MIN_VALUE),now)!!.pricePerKm)
    }
    @Test fun insufficientDaysOrDifferentProviderDoNotSetHistoricalMedian() {
        val sameDay=(1..10).map { quote.copy(id=it.toLong(),captured=now-Logic.FRESH-it*60_000) }
        assertNull(TripEvaluation.evaluate(sameDay,quote,route,now)!!.historicalMedian)
        val otherProvider=(1..10).map { quote.copy(id=it.toLong(),provider="other",captured=now-it*day) }
        assertNull(TripEvaluation.evaluate(otherProvider,quote,route,now)!!.historicalMedian)
    }
}
