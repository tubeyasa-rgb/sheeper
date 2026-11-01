package com.ar5as.mashwar

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

data class TrafficComparison(val currentMinutes:Double,val fastBaselineMinutes:Double,val differenceMinutes:Double,val percentChange:Double,val sampleCount:Int,val days:Int,val recordedAt:Long)
data class TripTimeForecast(val minutes:Double,val samples:Int,val validationCount:Int,val validationMae:Double)
data class ArrivalChoice(val waitMinutes:Int,val totalMinutes:Double,val savedMinutes:Double)
object TrafficEngine {
    private val zone=ZoneId.of("Africa/Cairo")
    private fun hour(time:Long)=Instant.ofEpochMilli(time).atZone(zone).hour
    private fun day(time:Long)=Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
    private fun same(sample:TrafficSample,reference:Observation)=Logic.same(sample.origin,reference.origin)&&Logic.same(sample.destination,reference.destination)&&Logic.same(sample.provider,reference.provider)&&Logic.same(sample.category,reference.category)
    private fun validDuration(minutes:Double?)=minutes!=null&&minutes.isFinite()&&minutes>0&&minutes<=1440
    fun sameRoute(sample:TrafficSample,reference:Observation)=same(sample,reference)
    fun currentEstimate(samples:List<TrafficSample>,reference:Observation,now:Long):TrafficSample? = samples.filter { it.kind=="USER_ESTIMATE" && validDuration(it.minutes) && same(it,reference) && it.recordedAt<=now && now-it.recordedAt<=Logic.FRESH }.maxByOrNull { it.recordedAt }
    private fun pastTrips(samples:List<TrafficSample>,reference:Observation,now:Long):List<TrafficSample> = samples.filter { it.kind=="MEASURED" && validDuration(it.minutes) && same(it,reference) && it.startedAt!=null && it.startedAt>0 && it.recordedAt>=it.startedAt && it.recordedAt<=now }.sortedBy { it.startedAt }.takeLast(500)
    private fun median(values:List<Double>):Double { val sorted=values.sorted(); val m=sorted.size/2; return if(sorted.size%2==0) (sorted[m-1]+sorted[m])/2 else sorted[m] }
    fun compareCurrent(samples:List<TrafficSample>,reference:Observation,now:Long):TrafficComparison? {
        val current=currentEstimate(samples,reference,now)?:return null
        val history=pastTrips(samples,reference,now); val days=history.map { day(it.startedAt!!) }.distinct().size
        if(history.size<10 || days<5) return null
        val sorted=history.map { it.minutes!! }.sorted(); val fast=sorted[(sorted.size-1)/4]
        val minutes=current.minutes!!; val diff=minutes-fast
        return TrafficComparison(minutes,fast,diff,diff/fast*100,history.size,days,current.recordedAt)
    }
    fun forecastDuration(samples:List<TrafficSample>,reference:Observation,now:Long,target:Long):TripTimeForecast? {
        if(target<now) return null
        val history=pastTrips(samples,reference,now)
        if(history.size<40) return null
        fun estimate(available:List<TrafficSample>,at:Long):Double? {
            val h=hour(at)
            val near=available.filter { it.recordedAt<at && abs(hour(it.startedAt!!)-h).let { d -> minOf(d,24-d)<=1 } }
            if(near.size<5 || near.map { day(it.startedAt!!) }.distinct().size<3) return null
            return median(near.map { it.minutes!! })
        }
        val split=(history.size*0.8).toInt(); var modelError=0.0; var baseError=0.0
        val validationDays=mutableSetOf<java.time.LocalDate>(); var count=0
        for(i in split until history.size) {
            val trip=history[i]; val at=trip.startedAt!!
            val prior=history.subList(0,i).filter { it.recordedAt<at }
            val predicted=estimate(prior,at)?:continue
            if(prior.isEmpty()) continue
            val observed=trip.minutes!!; modelError+=abs(observed-predicted); baseError+=abs(observed-median(prior.map { it.minutes!! })); count++; validationDays.add(day(at))
        }
        if(count<8 || validationDays.size<3 || modelError>baseError) return null
        val prediction=estimate(history,target)?:return null
        return TripTimeForecast(prediction,history.size,count,modelError/count)
    }
    fun fastestDeparture(samples:List<TrafficSample>,reference:Observation,now:Long,forecasts:Map<Int,TripTimeForecast?>):ArrivalChoice? {
        val current=currentEstimate(samples,reference,now)?.minutes?:return null
        if(forecasts.isEmpty() || forecasts.any { it.key<=0 || it.value==null || !validDuration(it.value?.minutes) }) return null
        val best=forecasts.minByOrNull { it.key+it.value!!.minutes }?:return null
        val total=best.key+best.value!!.minutes
        return if(total<current) ArrivalChoice(best.key,total,current-total) else ArrivalChoice(0,current,0.0)
    }
    fun elapsedMinutes(startedAt:Long,endedAt:Long):Double? {
        val elapsed=endedAt-startedAt
        return if(startedAt>0 && elapsed in 1..86_400_000L) elapsed/60_000.0 else null
    }
}
