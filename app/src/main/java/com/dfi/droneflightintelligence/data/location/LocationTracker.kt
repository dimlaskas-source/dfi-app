package com.dfi.droneflightintelligence.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.dfi.droneflightintelligence.data.model.GeoPointData
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationTracker(context: Context) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun locationUpdates(intervalMs: Long = 2000L): Flow<GeoPointData> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                trySend(
                    GeoPointData(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
                        altitudeMeters = if (loc.hasAltitude()) loc.altitude else null,
                        bearingDegrees = if (loc.hasBearing()) loc.bearing else null,
                        timestampMs = loc.time
                    )
                )
            }
        }

        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                trySend(
                    GeoPointData(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
                        altitudeMeters = if (loc.hasAltitude()) loc.altitude else null,
                        bearingDegrees = if (loc.hasBearing()) loc.bearing else null,
                        timestampMs = loc.time
                    )
                )
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }
}
