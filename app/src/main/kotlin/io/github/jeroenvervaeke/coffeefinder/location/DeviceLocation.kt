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

/**
 * The device's own answer to where it is, straight from Play services and with no bound on how
 * long it takes. [fixWithin] is what puts a bound on it.
 *
 * The `MissingPermission` suppression is on the one call that needs the permission, which
 * [isPermitted] has just checked and lint cannot see through. Revocation between the two is a
 * real race, and it is the `SecurityException` the `catch` is there for.
 */
class DeviceLocation(private val context: Context) : Locator {
    fun isPermitted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun fix(): Coordinates? {
        if (!isPermitted()) return null
        val cancellation = CancellationTokenSource()
        return suspendCancellableCoroutine { continuation ->
            // What makes giving up cost nothing. The caller's timeout cancels this coroutine and
            // lands here, and Play services then drops a request that was still holding the
            // radios open for a fix nobody is waiting for any more. A late answer resumes a dead
            // continuation, which is discarded rather than delivered.
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
