package com.ar5as.mashwar

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume

data class PlaceSuggestion(val label: String, val latitude: Double, val longitude: Double)
sealed interface PlaceSearchResult {
    data class Found(val places: List<PlaceSuggestion>) : PlaceSearchResult
    data object Unavailable : PlaceSearchResult
}

private val geocoderExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "place-search").apply { isDaemon = true } }
private fun suggestions(addresses: List<Address>): PlaceSearchResult = PlaceSearchResult.Found(
    addresses.filter { it.hasLatitude() && it.hasLongitude() }.mapNotNull { address ->
        val label = address.getAddressLine(0)?.trim().orEmpty()
        if (label.isBlank()) null else PlaceSuggestion(label, address.latitude, address.longitude)
    }.distinctBy { it.label }
)

@Suppress("DEPRECATION")
suspend fun searchPlaces(context: Context, query: String): PlaceSearchResult {
    if (!Geocoder.isPresent()) return PlaceSearchResult.Unavailable
    return withTimeoutOrNull(12_000) {
        suspendCancellableCoroutine { continuation ->
            fun finish(result: PlaceSearchResult) { if (continuation.isActive) continuation.resume(result) }
            try {
                val geocoder = Geocoder(context.applicationContext, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= 33) {
                    geocoder.getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) { finish(suggestions(addresses)) }
                        override fun onError(errorMessage: String?) { finish(PlaceSearchResult.Unavailable) }
                    })
                } else {
                    val task = geocoderExecutor.submit {
                        try { finish(suggestions(geocoder.getFromLocationName(query, 5).orEmpty())) }
                        catch (_: Exception) { finish(PlaceSearchResult.Unavailable) }
                    }
                    continuation.invokeOnCancellation { task.cancel(true) }
                }
            } catch (_: Exception) { finish(PlaceSearchResult.Unavailable) }
        }
    } ?: PlaceSearchResult.Unavailable
}
