package com.ar5as.mashwar

data class ParsedMeasure(val value:Double,val text:String)
data class ParsedRouteText(val durations:List<ParsedMeasure>,val distances:List<ParsedMeasure>)
object TrafficTextParser {
    private val hours=Regex("(?<![\\d.])([0-9]{1,3}(?:\\.[0-9]+)?)\\s*(?:hours?|hrs?|hr|h|ساعات|ساعة|ساعه|سا|س)(?![\\p{L}])(?:\\s*(?:و|and)?\\s*([0-9]{1,2}(?:\\.[0-9]+)?)\\s*(?:minutes?|mins?|min|m|دقائق|دقيقة|دقيقه|دق|د)(?![\\p{L}]))?",RegexOption.IGNORE_CASE)
    private val minutes=Regex("(?<![\\d.])([0-9]{1,4}(?:\\.[0-9]+)?)\\s*(?:minutes?|mins?|min|m|دقائق|دقيقة|دقيقه|دق|د)(?![\\p{L}])",RegexOption.IGNORE_CASE)
    private val km=Regex("(?<![\\d.])([0-9]{1,4}(?:\\.[0-9]+)?)\\s*(?:kilomet(?:er|re)s?|kms?|km|كيلومترات|كيلومتر|كلم|كم)(?![\\p{L}])",RegexOption.IGNORE_CASE)
    private fun normalize(text:String)=buildString { text.forEach { c -> val digit=Character.digit(c,10); when { digit>=0 -> append(digit); c=='٫' -> append('.'); c=='٬' -> {}; c in listOf('\u200e','\u200f','\u061c','\u202a','\u202b','\u202c','\u2066','\u2067','\u2069') -> {}; else -> append(c) } } }
    fun parse(text:String):ParsedRouteText {
        val input=normalize(text.take(20000)); val durations=mutableListOf<Pair<IntRange,ParsedMeasure>>()
        hours.findAll(input).forEach { match ->
            val h=match.groupValues[1].toDoubleOrNull()?:return@forEach
            val m=match.groupValues[2].takeIf { it.isNotEmpty() }?.toDoubleOrNull()?:0.0
            val total=h*60+m
            if(total>0 && total<=1440 && m<60) durations.add(match.range to ParsedMeasure(total,match.value.trim()))
        }
        minutes.findAll(input).forEach { match ->
            if(durations.any { it.first.first<=match.range.last && match.range.first<=it.first.last }) return@forEach
            val value=match.groupValues[1].toDoubleOrNull()?:return@forEach
            if(value>0 && value<=1440) durations.add(match.range to ParsedMeasure(value,match.value.trim()))
        }
        val distances=km.findAll(input).mapNotNull { match -> val value=match.groupValues[1].toDoubleOrNull(); if(value!=null && value>0 && value<=2000) ParsedMeasure(value,match.value.trim()) else null }.toList()
        return ParsedRouteText(durations.sortedBy { it.first.first }.map { it.second }.distinctBy { it.value },distances.distinctBy { it.value })
    }
}
