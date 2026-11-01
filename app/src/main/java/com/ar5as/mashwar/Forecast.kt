package com.ar5as.mashwar

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp

data class ForecastResult(val low:Double,val median:Double,val high:Double,val count:Int,val mae:Double,val coverage:Double,val residuals:List<Double>,val validationDays:Int)
data class DepartureOption(val minutes:Int,val forecast:ForecastResult,val expectedFare:Double,val generalizedCost:Double,val chanceCheaper:Double?)
data class PeakHour(val hour:Int,val median:Double,val samples:Int,val days:Int)
object ForecastEngine {
    private val zone=ZoneId.of("Africa/Cairo")
    private fun bucket(time:Long):Int=Instant.ofEpochMilli(time).atZone(zone).hour
    private fun estimates(data:List<Observation>,target:Long,tau:Int):Triple<Double,Double,Double>? {
        val hour=bucket(target)
        val relevant=data.filter { it.captured<target && abs(bucket(it.captured)-hour).let { d -> minOf(d,24-d)<=1 } }
        if(relevant.size<10) return null
        val values=relevant.map { it.price to exp(-(target-it.captured)/86_400_000.0/tau) }.sortedBy { it.first }
        val sum=values.sumOf { it.second }
        fun q(p:Double):Double { var total=0.0; for((v,w) in values) { total+=w; if(total>=sum*p) return v }; return values.last().first }
        return Triple(q(.1),q(.5),q(.9))
    }
    fun forecast(items:List<Observation>,example:Observation,target:Long):ForecastResult? {
        val data=items.filter { Logic.comparable(it,example) && Logic.same(it.provider,example.provider) && it.captured<target }.sortedBy { it.captured }.takeLast(500)
        if(data.size<40) return null
        val train=(data.size*.6).toInt(); val tuning=(data.size*.8).toInt(); if(train<20||data.size-tuning<8) return null
        fun validation(tau:Int,start:Int,end:Int):Triple<Double,Double,List<Double>> {
            val residuals=mutableListOf<Double>(); var covered=0
            for(i in start until end) { val e=estimates(data.subList(0,i),data[i].captured,tau)?:continue; residuals.add(data[i].price-e.second); if(data[i].price in e.first..e.third) covered++ }
            return Triple(if(residuals.isNotEmpty()) residuals.sumOf{abs(it)}/residuals.size else Double.POSITIVE_INFINITY,if(residuals.isNotEmpty()) covered.toDouble()/residuals.size else 0.0,residuals)
        }
        val tau=listOf(14,30,60,120).minByOrNull { validation(it,train,tuning).first }?:return null
        val tune=validation(tau,train,tuning); val holdout=validation(tau,tuning,data.size)
        if(tune.third.size<5||holdout.third.size<8||holdout.second<0.7) return null
        var baselineError=0.0
        for(i in tuning until data.size) { if(estimates(data.subList(0,i),data[i].captured,tau)==null) continue; val sorted=data.subList(0,i).map{it.price}.sorted(); baselineError+=abs(sorted[sorted.size/2]-data[i].price) }
        if(holdout.first>baselineError/holdout.third.size) return null
        val e=estimates(data,target,tau)?:return null
        val days=(tuning until data.size).filter { estimates(data.subList(0,it),data[it].captured,tau)!=null }.map { Instant.ofEpochMilli(data[it].captured).atZone(zone).toLocalDate() }.distinct().size
        return ForecastResult(e.first,e.second,e.third,data.size,holdout.first,holdout.second,holdout.third,days)
    }
    fun departureOptions(items:List<Observation>,baseline:Observation,now:Long,valueOfTime:Double,maxMinutes:Int):List<DepartureOption> {
        require(valueOfTime.isFinite() && valueOfTime>=0 && maxMinutes>=0)
        val history=items.filter { it.captured < now-Logic.FRESH }
        return listOf(5,10,15,30).filter { it<=maxMinutes }.mapNotNull { minutes ->
            val f=forecast(history,baseline,now+minutes*60_000L)?:return@mapNotNull null
            val prices=f.residuals.map { (f.median+it).coerceAtLeast(0.0) }
            val chance=if(Logic.fresh(baseline,now)&&prices.size>=20&&f.validationDays>=5) prices.count { it<baseline.price }.toDouble()/prices.size else null
            val expected=prices.average()
            DepartureOption(minutes,f,expected,expected+valueOfTime*minutes,chance)
        }
    }
    private fun hourlyHistory(items:List<Observation>,reference:Observation,now:Long):List<PeakHour> {
        val relevant=items.filter { Logic.comparable(it,reference)&&Logic.same(it.provider,reference.provider)&&it.captured<=now }
        val hours=relevant.groupBy { bucket(it.captured) }.mapNotNull { (hour,entries) ->
            val days=entries.map { Instant.ofEpochMilli(it.captured).atZone(zone).toLocalDate() }.distinct().size
            if(entries.size<5||days<3) null else { val prices=entries.map{it.price}.sorted(); PeakHour(hour,prices[prices.size/2],entries.size,days) }
        }
        return if(hours.size>=2 && hours.minOf { it.median }<hours.maxOf { it.median }) hours else emptyList()
    }
    fun peakHours(items:List<Observation>,reference:Observation,now:Long):List<PeakHour> {
        val hours=hourlyHistory(items,reference,now); val lowest=hours.minOfOrNull { it.median }?:return emptyList()
        return hours.filter { it.median>lowest }.sortedByDescending{it.median}.take(3)
    }
    fun cheaperHours(items:List<Observation>,reference:Observation,now:Long):List<PeakHour> {
        val hours=hourlyHistory(items,reference,now); val highest=hours.maxOfOrNull { it.median }?:return emptyList()
        return hours.filter { it.median<highest }.sortedBy{it.median}.take(3)
    }
}
