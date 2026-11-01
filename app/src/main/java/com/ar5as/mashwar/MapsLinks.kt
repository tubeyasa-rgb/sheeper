package com.ar5as.mashwar

import java.net.URLEncoder

object MapsLinks {
    fun forWalkingTrip(origin: String, destination: String): String? {
        if (origin.isBlank() || destination.isBlank()) return null
        fun encode(value: String) = URLEncoder.encode(value.trim(), "UTF-8")
        return "https://www.google.com/maps/dir/?api=1&origin=${encode(origin)}&destination=${encode(destination)}&travelmode=walking"
    }
    fun forTrip(origin: String, destination: String): String? {
        val target = destination.trim()
        if (target.isEmpty()) return null
        fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
        return if (origin.isBlank()) {
            "https://www.google.com/maps/search/?api=1&query=${encode(target)}"
        } else {
            "https://www.google.com/maps/dir/?api=1&origin=${encode(origin.trim())}&destination=${encode(target)}&travelmode=driving"
        }
    }
}
