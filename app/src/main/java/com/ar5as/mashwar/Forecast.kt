package com.ar5as.mashwar

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp

data class ForecastResult(val low: Double,val median: Double,val high: Double,val count: Int,val mae: Double,val coverage: Double)
object ForecastEngine {
    private val zone=ZoneId.of("Africa/Cairo")
    private fun bucket(time: Long): Int = Instant.ofEpochMilli(time).atZone(zone).hour
    private fun estimates(data: List<Observation>, target: Long, tau: Int): Triple<Double,Double,Double>? {
        val hour=bucket(target)
        val relevant=data.filter { it.captured < target && abs(bucket(it.captured)-hour).let { d -> minOf(d,24-d)<=1 } }
        if(relevant.size<10) return null
        val values=relevant.map { it.price to exp(-(target-it.captured)/86_400_000.0/tau) }.sortedBy { it.first }
        val sum=values.sumOf { it.second }
        fun q(p: Double): Double { var total=0.0; for((v,w) in values) { total+=w; if(total>=sum*p) return v }; return values.last().first }
        return Triple(q(.1),q(.5),q(.9))
    }
    fun forecast(items: List<Observation>, example: Observation, target: Long): ForecastResult? {
        val data=items.filter { Logic.comparable(it,example) && Logic.same(it.provider,example.provider) && it.captured < target }.sortedBy { it.captured }
        if(data.size<40) return null
        val train=(data.size*.6).toInt(); val tuning=(data.size*.8).toInt(); if(train<20 || data.size-tuning<8) return null
        fun validation(tau:Int, start:Int,end:Int): Triple<Double,Double,Int> {
            var err=0.0; var covered=0; var count=0
            for(i in start until end) { val e=estimates(data.subList(0,i),data[i].captured,tau) ?: continue; err+=abs(e.second-data[i].price); if(data[i].price in e.first..e.third) covered++; count++ }
            return Triple(if(count>0) err/count else Double.POSITIVE_INFINITY,if(count>0) covered.toDouble()/count else 0.0,count)
        }
        val tau=listOf(14,30,60,120).minByOrNull { validation(it,train,tuning).first } ?: return null
        val tune=validation(tau,train,tuning); val holdout=validation(tau,tuning,data.size)
        if(tune.third<5 || holdout.third<8 || holdout.second<0.7) return null
        var baselineError=0.0
        for(i in tuning until data.size) {
            if(estimates(data.subList(0,i),data[i].captured,tau) == null) continue
            val sorted=data.subList(0,i).map { it.price }.sorted(); baselineError+=abs(sorted[sorted.size/2]-data[i].price)
        }
        if(holdout.first > baselineError/holdout.third) return null
        val estimate=estimates(data,target,tau) ?: return null
        return ForecastResult(estimate.first,estimate.second,estimate.third,data.size,holdout.first,holdout.second)
    }
}
