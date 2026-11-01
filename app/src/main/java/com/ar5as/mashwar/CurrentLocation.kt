package com.ar5as.mashwar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed interface LocationResult {
    data class Found(val location: Location) : LocationResult
    data object PermissionRequired : LocationResult
    data object Disabled : LocationResult
    data object Unavailable : LocationResult
}

@SuppressLint("MissingPermission")
suspend fun currentLocation(context: Context): LocationResult {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) return LocationResult.PermissionRequired
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .filter { provider -> (provider != LocationManager.GPS_PROVIDER || fine) && runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false) }
    if (providers.isEmpty()) return LocationResult.Disabled
    return try {
        val recent = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { location -> val age = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos; age in 0..120_000_000_000L }
            .maxByOrNull { it.elapsedRealtimeNanos }
        if (recent != null) LocationResult.Found(recent)
        else {
            val location = withTimeoutOrNull(20_000) {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            manager.removeUpdates(this)
                            if (continuation.isActive) continuation.resume(location)
                        }
                    }
                    continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                    var registered = false
                    for (provider in providers) {
                        try { manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper()); registered = true }
                        catch (_: IllegalArgumentException) { }
                    }
                    if (!registered && continuation.isActive) continuation.resume(null)
                }
            }
            if (location != null) LocationResult.Found(location) else LocationResult.Unavailable
        }
    } catch (_: SecurityException) { LocationResult.PermissionRequired }
}
