package com.tellmeindia.iaccept.logic

import android.app.ActivityManager
import android.content.Context

enum class HardwareTier {
    HIGH_END,
    LOW_END
}

object HardwareDetector {

    fun getHardwareTier(context: Context): HardwareTier {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)

            val totalRamGb = memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
            val numCores = Runtime.getRuntime().availableProcessors()

            if (totalRamGb >= 5.0 && numCores >= 8) {
                HardwareTier.HIGH_END
            } else {
                HardwareTier.LOW_END
            }
        } catch (e: Throwable) {
            HardwareTier.LOW_END
        }
    }
}
