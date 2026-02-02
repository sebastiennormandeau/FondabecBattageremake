package com.fondabec.battage.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Criteria
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class DetectedAddress(
    val street: String,
    val city: String,
    val province: String,
    val postalCode: String,
    val country: String,
    val latitude: Double,
    val longitude: Double
)

object ProjectLocationService {

    suspend fun detect(context: Context): DetectedAddress? = withContext(Dispatchers.IO) {
        val loc = getBestLocation(context) ?: return@withContext null
        val addr = reverseGeocode(context, loc.latitude, loc.longitude)

        val street = addr?.streetLine().orEmpty()
        val city = addr?.locality.orEmpty()
        val province = addr?.adminArea.orEmpty()
        val postal = addr?.postalCode.orEmpty()
        val country = addr?.countryName.orEmpty()

        DetectedAddress(
            street = street,
            city = city,
            province = province,
            postalCode = postal,
            country = country,
            latitude = loc.latitude,
            longitude = loc.longitude
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun getBestLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }

        // 1) Last known (rapide)
        val last = providers.mapNotNull { p ->
            runCatching { lm.getLastKnownLocation(p) }.getOrNull()
        }.maxByOrNull { it.time }

        if (last != null) return last

        // 2) Single update (si rien)
        val criteria = Criteria().apply {
            accuracy = Criteria.ACCURACY_FINE
        }

        return suspendCancellableCoroutine { cont ->
            @Suppress("DEPRECATION")
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (cont.isActive) cont.resume(location)
                    runCatching { lm.removeUpdates(this) }
                }
                override fun onProviderDisabled(provider: String) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            }

            cont.invokeOnCancellation {
                runCatching { lm.removeUpdates(listener) }
            }

            runCatching {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(criteria, listener, Looper.getMainLooper())
            }.onFailure {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    private suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): Address? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.CANADA_FRENCH)

        return if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(lat, lng, 1) { list ->
                    cont.resume(list.firstOrNull())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
        }
    }

    private fun Address.streetLine(): String {
        val num = subThoroughfare?.trim().orEmpty()
        val street = thoroughfare?.trim().orEmpty()
        return (num + " " + street).trim()
    }
}
