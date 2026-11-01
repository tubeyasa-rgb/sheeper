package com.ar5as.mashwar

import kotlin.math.abs

data class PickupComparison(val point:Pickup,val quote:Observation,val savings:Double,val walkCost:Double,val netSavings:Double)
object PickupAnalysis {
    fun compare(baseline:Observation,items:List<Observation>,points:List<Pickup>,now:Long,maxMeters:Double,maxMinutes:Double,valuePerMinute:Double):List<PickupComparison> {
        require(maxMeters.isFinite() && maxMeters>0 && maxMinutes.isFinite() && maxMinutes>0 && valuePerMinute.isFinite() && valuePerMinute>=0)
        if(!Logic.fresh(baseline,now)) return emptyList()
        return points.mapNotNull { point ->
            if(point.status=="UNSAFE" || point.direct!=true || point.walkMeters==null || point.walkMinutes==null || point.walkMeters<=0 || point.walkMinutes<=0 || point.walkMeters>maxMeters || point.walkMinutes>maxMinutes || Logic.same(point.name,baseline.origin)) return@mapNotNull null
            val quote=items.filter { q -> Logic.same(q.origin,point.name) && Logic.same(q.destination,baseline.destination) && Logic.same(q.category,baseline.category) && Logic.same(q.provider,baseline.provider) && Logic.fresh(q,now) && abs(q.captured-baseline.captured)<=Logic.FRESH }.maxByOrNull { it.captured } ?: return@mapNotNull null
            val savings=baseline.price-quote.price; val walkingCost=point.walkMinutes*valuePerMinute
            PickupComparison(point,quote,savings,walkingCost,savings-walkingCost)
        }.sortedByDescending { it.netSavings }
    }
}
