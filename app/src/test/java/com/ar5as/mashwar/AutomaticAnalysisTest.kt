package com.ar5as.mashwar

import org.junit.Assert.*
import org.junit.Test

class AutomaticAnalysisTest {
    private val day=86_400_000L
    private val now=1_750_000_000_000L
    private val baseline=Observation(id=1000,origin="A",destination="B",provider="P",category="C",price=150.0,captured=now-20_000)
    private fun history(count:Int)= (1..count).map { baseline.copy(id=it.toLong(),price=100.0,captured=now-day*it) }
    private fun point(name:String="corner",status:String="UNKNOWN",direct:Boolean?=true,meters:Double?=100.0,minutes:Double?=4.0)=Pickup(name=name,status=status,note="",recorded=now,walkMeters=meters,walkMinutes=minutes,direct=direct)
    private fun quote(name:String="corner",price:Double=110.0)=baseline.copy(id=2000,origin=name,price=price,captured=now-30_000)

    @Test fun probabilityRequiresFreshBaselineAndTwentyHistoricalErrors() {
        val options=ForecastEngine.departureOptions(history(120),baseline,now,0.0,30)
        assertEquals(4,options.size); assertEquals(1.0,options.first().chanceCheaper!!,0.001)
        assertNull(ForecastEngine.departureOptions(history(60),baseline,now,0.0,30).first().chanceCheaper)
        assertNull(ForecastEngine.departureOptions(history(120),baseline.copy(captured=now-180_001),now,0.0,30).first().chanceCheaper)
    }
    @Test fun noProbabilityFromManyObservationsOnOneDay() {
        val recent=(1..120).map { baseline.copy(id=it.toLong(),price=100.0,captured=now-Logic.FRESH-it*60_000L) }
        val options=ForecastEngine.departureOptions(recent,baseline,now,0.0,30)
        assertTrue(options.isNotEmpty()); assertTrue(options.all { it.chanceCheaper==null })
    }
    @Test fun timeValueRaisesCostAndRespectsMaximumWait() {
        val options=ForecastEngine.departureOptions(history(120),baseline,now,2.0,10)
        assertEquals(listOf(5,10),options.map{it.minutes}); assertEquals(10.0,options.first().generalizedCost-options.first().expectedFare,0.001)
    }
    @Test fun noPeakClaimWhenHoursHaveIdenticalPrices() {
        val rows=(0..2).flatMap { d -> (0..1).flatMap { h -> (0..1).map { n -> baseline.copy(id=(d*10+h*2+n).toLong(),captured=now-d*day-h*3_600_000L-n*10_000L,price=100.0) } } }
        assertTrue(ForecastEngine.peakHours(rows,baseline,now).isEmpty())
    }
    @Test fun historyRanksHighAndLowHoursByObservedMedian() {
        val zone=java.time.ZoneId.of("Africa/Cairo")
        val end=java.time.ZonedDateTime.of(2025,6,30,22,0,0,0,zone)
        val records=(1..3).flatMap { day -> listOf(10,18).flatMap { hour -> (0..1).map { n ->
            baseline.copy(captured=end.minusDays(day.toLong()).withHour(hour).withMinute(n).toInstant().toEpochMilli(),price=if(hour==10) 80.0 else 150.0)
        } } }
        val target=end.toInstant().toEpochMilli()
        assertEquals(10,ForecastEngine.cheaperHours(records,baseline,target).single().hour)
        assertEquals(18,ForecastEngine.peakHours(records,baseline,target).single().hour)
        assertTrue(ForecastEngine.peakHours(records.filter { it.provider=="another" },baseline,target).isEmpty())
    }
    @Test fun candidateRequiresMatchingFreshQuoteDirectRouteAndKnownWalk() {
        assertEquals(1,PickupAnalysis.compare(baseline,listOf(quote()),listOf(point()),now,500.0,10.0,0.0).size)
        assertTrue(PickupAnalysis.compare(baseline,listOf(quote()),listOf(point(status="UNSAFE"),point(direct=false),point(meters=null)),now,500.0,10.0,0.0).isEmpty())
        assertTrue(PickupAnalysis.compare(baseline,listOf(quote().copy(destination="other"),quote().copy(provider="other"),quote().copy(captured=now-181_000)),listOf(point()),now,500.0,10.0,0.0).isEmpty())
    }
    @Test fun moreExpensiveAlternativeHasNegativeSavings() {
        val result=PickupAnalysis.compare(baseline,listOf(quote(price=160.0)),listOf(point()),now,500.0,10.0,1.0).single()
        assertTrue(result.netSavings<0)
    }
    @Test fun walkingTimeChangesRankingAndIncreasesCost() {
        val points=listOf(point("one",minutes=8.0),point("two",minutes=1.0))
        val quotes=listOf(quote("one",100.0),quote("two",110.0))
        assertEquals("one",PickupAnalysis.compare(baseline,quotes,points,now,500.0,10.0,0.0).first().point.name)
        val ranked=PickupAnalysis.compare(baseline,quotes,points,now,500.0,10.0,2.0)
        assertEquals("two",ranked.first().point.name); assertEquals(16.0,ranked.last().walkCost,0.001)
    }
}
