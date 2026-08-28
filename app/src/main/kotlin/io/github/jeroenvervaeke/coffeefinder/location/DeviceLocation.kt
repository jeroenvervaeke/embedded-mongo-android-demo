package io.github.jeroenvervaeke.coffeefinder.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import io.github.jeroenvervaeke.coffeefinder.data.model.Coordinates
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Why a query is being measured from where it is, which is worth saying on screen. */
enum class LocationSource {
    /** No answer yet — the permission has not been decided, or the fix has not arrived. */
    ASKING,

    /** The device said where it is. */
    DEVICE,

    /** It would not, so the queries measure from Dublin. */
    FALLBACK,

    /** The user tapped the map, which overrides wherever the device thinks it is. */
    PICKED,
}

/**
 * Where the device is, or `null` when it will not say.
 *
 * `null` covers every way that happens — permission refused, location switched off, no fix yet,
 * no Play services — because they all lead to the same place: measure from Dublin and say so.
 * There is nothing here worth telling the four of them apart for.
 *
 * The `MissingPermission` suppression is on the one call that needs the permission, which
 * [isPermitted] has just checked and lint cannot see through. Revocation between the two is a
 * real race, and it is the `SecurityException` the `catch` is there for.
 */
class DeviceLocation(private val context: Context) {
    fun isPermitted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(): Coordinates? {
        if (!isPermitted()) return null
        val cancellation = CancellationTokenSource()
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { cancellation.cancel() }
            // Both calls can throw before any listener is attached -- no Play services on the
            // device, or the permission revoked in the moment between the check above and here.
            // Failing that way would take the process down through the ViewModel's launch, and
            // the answer is the same one every other failure gets: measure from Dublin.
            try {
                // A fresh fix rather than the last known one, at the accuracy a coarse permission
                // gives anyway. Play services answers null where there is no position at all.
                LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                    .addOnSuccessListener { location ->
                        continuation.resume(
                            location?.let { Coordinates(longitude = it.longitude, latitude = it.latitude) },
                        )
                    }
                    .addOnFailureListener { continuation.resume(null) }
            } catch (unavailable: Exception) {
                continuation.resume(null)
            }
        }
    }
}
