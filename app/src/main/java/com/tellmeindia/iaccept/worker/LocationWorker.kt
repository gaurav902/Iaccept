package com.tellmeindia.iaccept.worker

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import com.tellmeindia.iaccept.data.SupabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val supabaseManager = SupabaseManager(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // 1. Check Permission (Safety First)
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return@withContext Result.failure()
            }

            // 2. Fetch High-Accuracy Location
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            val locationTask = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            val location = Tasks.await(locationTask)

            // 3. Send to Supabase (Zero Local Storage / Zero Logs)
            if (location != null) {
                supabaseManager.updateLocation(location.latitude, location.longitude)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
