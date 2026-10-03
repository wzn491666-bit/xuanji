package com.xuanji.android

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidMcpConnectionsTest {
    @Test
    fun capacityRejectsConnectionsAndStopClosesActiveSockets() {
        val connections = AndroidMcpConnections(1)
        val entered = CountDownLatch(1)
        ServerSocket(0, 2, InetAddress.getLoopbackAddress()).use { listener ->
            Socket(listener.inetAddress, listener.localPort).use {
                val accepted = listener.accept()
                connections.execute(accepted) {
                    entered.countDown()
                    it.getInputStream().read()
                }
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                Socket(listener.inetAddress, listener.localPort).use {
                    val rejected = listener.accept()
                    connections.execute(rejected) { error("capacity exceeded") }
                    assertTrue(rejected.isClosed)
                }
                connections.close()
                assertTrue(accepted.isClosed)
                Socket(listener.inetAddress, listener.localPort).use {
                    val afterStop = listener.accept()
                    connections.execute(afterStop) { error("server stopped") }
                    assertTrue(afterStop.isClosed)
                }
            }
        }
    }
}
