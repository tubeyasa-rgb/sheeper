package com.ar5as.mashwar

data class JourneyDecision(val waitMinutes:Int,val expectedNetSaving:Double,val usesTripDuration:Boolean)
object JourneyDecisionEngine {
    fun choose(reference:Observation,now:Long,options:List<DepartureOption>,valuePerMinute:Double,currentDuration:TrafficSample?,futureDurations:Map<Int,TripTimeForecast?>):JourneyDecision? {
        if(!Logic.fresh(reference,now) || !valuePerMinute.isFinite() || valuePerMinute<0) return null
        val eligible=options.filter { it.chanceCheaper!=null }
        if(eligible.isEmpty()) return null
        val useTripDuration=valuePerMinute>0
        if(useTripDuration && (currentDuration==null || currentDuration.kind!="USER_ESTIMATE" || !TrafficEngine.sameRoute(currentDuration,reference) || currentDuration.minutes==null || !currentDuration.minutes.isFinite() || currentDuration.minutes<=0 || currentDuration.minutes>1440 || currentDuration.recordedAt>now || now-currentDuration.recordedAt>Logic.FRESH || eligible.any { futureDurations[it.minutes]==null })) return null
        val currentCost=reference.price + if(useTripDuration) currentDuration!!.minutes!!*valuePerMinute else 0.0
        fun cost(option:DepartureOption)=option.generalizedCost + if(useTripDuration) futureDurations[option.minutes]!!.minutes*valuePerMinute else 0.0
        val best=eligible.minByOrNull { cost(it) }?:return null
        val net=currentCost-cost(best)
        return if(net>0) JourneyDecision(best.minutes,net,useTripDuration) else JourneyDecision(0,0.0,useTripDuration)
    }
}
