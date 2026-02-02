package com.fondabec.battage.location

import android.content.Context
import android.location.Geocoder
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object GeocodingService {

    @Suppress("DEPRECATION")
    suspend fun reverseGeocodeLine(context: Context, lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) return@withContext null
                val geocoder = Geocoder(context, Locale.getDefault())
                val res = geocoder.getFromLocation(lat, lng, 1)
                val a = res?.firstOrNull() ?: return@withContext null
                a.getAddressLine(0)?.trim().takeIf { !it.isNullOrBlank() }
            } catch (_: Exception) {
                null
            }
        }

    @Suppress("DEPRECATION")
    suspend fun forwardGeocodeFirst(context: Context, query: String): LatLng? =
        withContext(Dispatchers.IO) {
            try {
                val q = query.trim()
                if (q.isBlank()) return@withContext null
                if (!Geocoder.isPresent()) return@withContext null
                val geocoder = Geocoder(context, Locale.getDefault())
                val res = geocoder.getFromLocationName(q, 1)
                val a = res?.firstOrNull() ?: return@withContext null
                LatLng(a.latitude, a.longitude)
            } catch (_: Exception) {
                null
            }
        }
}
