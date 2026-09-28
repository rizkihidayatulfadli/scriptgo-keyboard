package com.magickeyboard.manager.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import com.magickeyboard.profile.Province
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** City (kabupaten/kota) and province for the business profile. Coordinates are never stored. */
data class DetectedPlace(val city: String, val province: String)

/**
 * Where the phone is, as city and province, from the approximate (network) location and the
 * platform geocoder. Null when location is off, unavailable, or the geocoder has no answer; the
 * caller then lets the user pick. Needs ACCESS_COARSE_LOCATION to be granted first.
 */
suspend fun detectPlace(context: Context): DetectedPlace? {
    val location = withTimeoutOrNull(10_000) { currentLocation(context) } ?: return null
    if (!Geocoder.isPresent()) return null
    val address = withTimeoutOrNull(10_000) { reverseGeocode(context, location) } ?: return null
    val province = Province.match(address.adminArea) ?: return null
    val city = cityName(address)
    return DetectedPlace(city, province)
}

/** "Kota Bandung", "Kabupaten Sleman", or the locality when the geocoder has no regency. */
private fun cityName(address: Address): String {
    val raw = address.subAdminArea ?: address.locality ?: ""
    return raw
        .replace(Regex("^(City of )(.+)$"), "Kota $2")
        .replace(Regex("^(.+) City$"), "Kota $1")
        .replace(Regex("^(.+) Regency$"), "Kabupaten $1")
        .trim()
}

@SuppressLint("MissingPermission") // checked by the caller before asking for a location
private suspend fun currentLocation(context: Context): Location? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
        .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    // A recent fix from any app is accurate enough for a city.
    providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .filter { System.currentTimeMillis() - it.time < 6 * 60 * 60 * 1000L }
        .maxByOrNull { it.time }
        ?.let { return it }
    val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER } ?: return null
    return suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT >= 30) {
            val cancel = CancellationSignal()
            cont.invokeOnCancellation { cancel.cancel() }
            manager.getCurrentLocation(provider, cancel, context.mainExecutor) { cont.resume(it) }
        } else {
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (cont.isActive) cont.resume(location)
                }
                @Deprecated("Deprecated in API 29") override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) {
                    if (cont.isActive) cont.resume(null)
                }
            }
            cont.invokeOnCancellation { manager.removeUpdates(listener) }
            @Suppress("DEPRECATION")
            manager.requestSingleUpdate(provider, listener, android.os.Looper.getMainLooper())
        }
    }
}

private suspend fun reverseGeocode(context: Context, location: Location): Address? {
    val geocoder = Geocoder(context, Locale("id", "ID"))
    return if (Build.VERSION.SDK_INT >= 33) {
        suspendCancellableCoroutine { cont ->
            geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (cont.isActive) cont.resume(addresses.firstOrNull())
                }
                override fun onError(errorMessage: String?) {
                    if (cont.isActive) cont.resume(null)
                }
            })
        }
    } else {
        withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            runCatching { geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull() }.getOrNull()
        }
    }
}
