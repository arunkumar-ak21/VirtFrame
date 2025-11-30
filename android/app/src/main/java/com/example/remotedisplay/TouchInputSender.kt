package com.example.remotedisplay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.net.Socket

class TouchInputSender(private val host: String, private val port: Int) {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private val queue = java.util.concurrent.LinkedBlockingQueue<String>()
    private var running = false
    private var senderThread: Thread? = null

    suspend fun connect() {
        withContext(Dispatchers.IO) {
            try {
                socket = Socket(host, port)
                writer = PrintWriter(socket!!.getOutputStream(), true)
                startSenderThread()
                android.util.Log.d("TouchSender", "Connected to $host:$port")
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("TouchSender", "Connection failed: ${e.message}")
            }
        }
    }

    private fun startSenderThread() {
        running = true
        senderThread = Thread {
            try {
                while (running) {
                    val message = queue.take() // Blocks until message available
                    writer?.println(message)
                }
            } catch (e: InterruptedException) {
                // Thread interrupted, exit
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        senderThread?.start()
    }

    fun sendMove(x: Float, y: Float) {
        offer("MOVE,$x,$y")
    }

    fun sendDown(x: Float, y: Float) {
        offer("DOWN,$x,$y")
    }

    fun sendUp(x: Float, y: Float) {
        offer("UP,$x,$y")
    }

    fun sendClick(x: Float, y: Float) {
        offer("CLICK,$x,$y")
    }

    fun sendDoubleClick(x: Float, y: Float) {
        offer("DBL_CLICK,$x,$y")
    }

    fun sendScroll(dx: Float, dy: Float) {
        offer("SCROLL,$dx,$dy")
    }

    private fun offer(message: String) {
        if (!queue.offer(message)) {
            // Queue full, maybe drop or log? 
            // LinkedBlockingQueue is unbounded by default so this won't happen unless OOM
        }
    }

    fun close() {
        running = false
        senderThread?.interrupt()
        try {
            writer?.close()
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
