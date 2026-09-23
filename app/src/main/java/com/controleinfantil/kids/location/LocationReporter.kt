package com.controleinfantil.kids.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.controleinfantil.kids.remote.SupabaseClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Pega uma posição atual e envia ao backend. */
class LocationReporter(private val context: Context) {

    private val fused = LocationServices.getFusedLocationProviderClient(context)

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun reportOnce(client: SupabaseClient): Boolean {
        if (!hasPermission()) {
            Log.w(TAG, "Sem permissão de localização")
            return false
        }
        val loc = currentLocation() ?: return false
        client.postLocation(loc.latitude, loc.longitude, loc.accuracy)
        return true
    }

    @Suppress("MissingPermission")
    private suspend fun currentLocation() =
        suspendCancellableCoroutine { cont ->
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener {
                    Log.e(TAG, "getCurrentLocation falhou", it)
                    cont.resume(null)
                }
        }

    companion object {
        private const val TAG = "LocationReporter"
    }
}
