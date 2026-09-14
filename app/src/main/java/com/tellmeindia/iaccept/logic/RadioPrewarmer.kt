package com.tellmeindia.iaccept.logic

import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object RadioPrewarmer {
    private const val TAG = "RadioPrewarmer"
    private var job: Job? = null

    fun startPrewarming(scope: CoroutineScope) {
        if (job?.isActive == true) return
        
        job = scope.launch(Dispatchers.IO) {
            val buf = byteArrayOf(0x00)
            try {
                val socket = DatagramSocket()
                val address = InetAddress.getByName("1.1.1.1") // Cloudflare DNS
                
                while (isActive) {
                    try {
                        val packet = DatagramPacket(buf, buf.size, address, 53)
                        socket.send(packet)
                    } catch (e: Exception) {
                        // Silent pre-warming ping
                    }
                    delay(3500) // Keep modem chip awake in active 5G state
                }
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Prewarmer init error: ${e.message}")
            }
        }
    }

    fun stopPrewarming() {
        job?.cancel()
        job = null
    }
}
