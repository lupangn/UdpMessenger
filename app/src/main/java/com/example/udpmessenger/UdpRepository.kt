package com.example.udpmessenger

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class SendResult(
    val success: Boolean,
    val bytesSent: Int = 0,
    val error: String = ""
)

object UdpRepository {
    suspend fun send(host: String, port: Int, message: String): SendResult =
        withContext(Dispatchers.IO) {
            try {
                val data    = message.toByteArray(Charsets.UTF_8)
                val address = InetAddress.getByName(host)
                val packet  = DatagramPacket(data, data.size, address, port)
                DatagramSocket().use { it.send(packet) }
                SendResult(true, data.size)
            } catch (e: Exception) {
                SendResult(false, error = e.message ?: "Unknown error")
            }
        }
}
