package com.controleinfantil.kids.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.controleinfantil.kids.remote.SupabaseClient
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Pega uma posição atual e envia ao backend.
 *
 * Tenta primeiro o Google Play Services (mais preciso). Onde ele não existe ou não
 * devolve posição — emuladores como o BlueStacks, aparelhos sem os serviços Google —
 * cai para o [LocationManager] do próprio Android.
 */
class LocationReporter(private val context: Context) {

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun reportOnce(client: SupabaseClient): Boolean {
        if (!hasPermission()) {
            Log.w(TAG, "Sem permissão de localização")
            return false
        }
        val loc = fusedLocation() ?: managerLocation()
        if (loc == null) {
            Log.w(TAG, "Nenhuma posição disponível (GPS desligado ou sem sinal?)")
            return false
        }
        client.postLocation(loc.latitude, loc.longitude, loc.accuracy)
        return true
    }

    private fun playServicesReady(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) ==
            ConnectionResult.SUCCESS

    @Suppress("MissingPermission")
    private suspend fun fusedLocation(): Location? {
        if (!playServicesReady()) return null
        val fused = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { cont ->
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener {
                    Log.e(TAG, "getCurrentLocation falhou", it)
                    cont.resume(null)
                }
        }
    }

    /** Reserva sem Google: última posição conhecida ou uma leitura única. */
    @Suppress("MissingPermission")
    private suspend fun managerLocation(): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return null

        // Melhor última posição conhecida, se houver.
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { return it }

        // Nenhuma guardada: pede uma leitura única com tempo limite.
        val provider = providers.first()
        return suspendCancellableCoroutine { cont ->
            val listener = android.location.LocationListener { location ->
                if (cont.isActive) cont.resume(location)
            }
            runCatching {
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }.onFailure {
                Log.e(TAG, "requestSingleUpdate falhou", it)
                if (cont.isActive) cont.resume(null)
            }
            cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
        }
    }

    companion object {
        private const val TAG = "LocationReporter"
    }
}
