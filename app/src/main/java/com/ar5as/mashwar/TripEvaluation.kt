package com.ar5as.mashwar

import java.time.Instant
import java.time.ZoneId

data class TripEvaluationResult(val pricePerKm:Double?,val historicalMedian:Double?,val percentDifference:Double?,val sampleCount:Int,val sampleDays:Int)
object TripEvaluation {
    fun evaluate(items:List<Observation>,quote:Observation,route:TrafficSample?,now:Long):TripEvaluationResult? {
        if(!Logic.fresh(quote,now) || !quote.price.isFinite() || quote.price<=0) return null
        val validRoute=route?.takeIf { it.kind=="USER_ESTIMATE" && TrafficEngine.sameRoute(it,quote) && it.recordedAt<=now && now-it.recordedAt<=Logic.FRESH }
        val distance=validRoute?.distanceKm?.takeIf { it.isFinite() && it>0 && it<=2000 }
        val perKm=distance?.let { quote.price/it }?.takeIf { it.isFinite() }
        val history=items.filter { Logic.comparable(it,quote) && Logic.same(it.provider,quote.provider) && it.captured>0 && it.captured<now-Logic.FRESH && it.price.isFinite() && it.price>0 }.sortedBy { it.captured }.takeLast(500)
        val days=history.map { Instant.ofEpochMilli(it.captured).atZone(ZoneId.of("Africa/Cairo")).toLocalDate() }.distinct().size
        if(history.size<10 || days<5) return TripEvaluationResult(perKm,null,null,history.size,days)
        val prices=history.map { it.price }.sorted(); val i=prices.size/2
        val median=if(prices.size%2==0) (prices[i-1]+prices[i])/2 else prices[i]
        val difference=((quote.price-median)/median*100).takeIf { it.isFinite() }
        return TripEvaluationResult(perKm,median,difference,history.size,days)
    }
}
