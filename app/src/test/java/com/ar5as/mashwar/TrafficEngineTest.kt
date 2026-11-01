package com.ar5as.mashwar

import org.junit.Assert.*
import org.junit.Test

class TrafficEngineTest {
    private val now=1_750_000_000_000L
    private val day=86_400_000L
    private val reference=Observation(id=100,origin="A",destination="B",provider="P",category="C",price=100.0,captured=now-30_000)
    private fun sample(index:Int,minutes:Double=30.0):TrafficSample {
        val start=now-index*day-3_600_000
        return TrafficSample(id=index.toLong(),origin="A",destination="B",provider="P",category="C",kind="MEASURED",minutes=minutes,startedAt=start,recordedAt=start+(minutes*60_000).toLong())
    }
    private fun estimate(minutes:Double=45.0,at:Long=now-60_000)=TrafficSample(id=999,origin="A",destination="B",provider="P",category="C",kind="USER_ESTIMATE",minutes=minutes,recordedAt=at)
    @Test fun elapsedMeasurementRejectsInvalidOrExcessiveClocks() {
        assertEquals(10.0,TrafficEngine.elapsedMinutes(now-600_000,now)!!,0.0001)
        assertNull(TrafficEngine.elapsedMinutes(now,now)); assertNull(TrafficEngine.elapsedMinutes(now,now-1)); assertNull(TrafficEngine.elapsedMinutes(now-day-1,now))
    }
    @Test fun comparesUserEnteredEstimateAgainstComparablePastTrips() {
        val comparison=TrafficEngine.compareCurrent((1..12).map{sample(it)}+estimate(),reference,now)!!
        assertEquals(30.0,comparison.fastBaselineMinutes,0.001); assertEquals(15.0,comparison.differenceMinutes,0.001); assertEquals(50.0,comparison.percentChange,0.001)
        assertEquals(12,comparison.sampleCount); assertTrue(comparison.days>=5)
    }
    @Test fun noLiveTrafficClaimFromStaleEstimateOrWrongRoute() {
        val history=(1..12).map{sample(it)}
        assertNull(TrafficEngine.compareCurrent(history+estimate(at=now-180_001),reference,now))
        assertNull(TrafficEngine.compareCurrent(history+estimate().copy(destination="C"),reference,now))
        assertNull(TrafficEngine.compareCurrent(history.take(9)+estimate(),reference,now))
        assertNull(TrafficEngine.currentEstimate(listOf(estimate(at=now+1)),reference,now))
    }
    @Test fun validatedHistoricalTripDurationCanBeForecast() {
        val history=(1..60).map{sample(it)}
        val prediction=TrafficEngine.forecastDuration(history,reference,now,now+10*60_000L)!!
        assertEquals(30.0,prediction.minutes,0.001); assertTrue(prediction.validationCount>=8); assertEquals(0.0,prediction.validationMae,0.001)
        assertNull(TrafficEngine.forecastDuration(history.take(39),reference,now,now))
        assertNull(TrafficEngine.forecastDuration(history.map{it.copy(provider="other")},reference,now,now))
        assertNull(TrafficEngine.forecastDuration(history,reference,now,now-1))
    }
    @Test fun fastestArrivalIncludesWaitingWithoutInventingMoneyValue() {
        val predictions=mapOf(5 to TripTimeForecast(30.0,50,10,3.0),10 to TripTimeForecast(20.0,50,10,3.0))
        val choice=TrafficEngine.fastestDeparture(listOf(estimate(40.0)),reference,now,predictions)!!
        assertEquals(10,choice.waitMinutes); assertEquals(10.0,choice.savedMinutes,0.001)
        assertNull(TrafficEngine.fastestDeparture(listOf(estimate(40.0)),reference,now,mapOf(5 to null)))
        assertNull(TrafficEngine.fastestDeparture(listOf(estimate(40.0).copy(origin="other")),reference,now,predictions))
        assertEquals(0,TrafficEngine.fastestDeparture(listOf(estimate(10.0)),reference,now,predictions)!!.waitMinutes)
    }
    @Test fun excludesIncompleteOrFutureTripsFromHistory() {
        val fake=(1..12).map{sample(it).copy(kind="ACTIVE",minutes=null)} + estimate()
        assertNull(TrafficEngine.compareCurrent(fake,reference,now))
        val future=(1..12).map{sample(it).copy(recordedAt=now+1)} + estimate()
        assertNull(TrafficEngine.compareCurrent(future,reference,now))
    }
}
