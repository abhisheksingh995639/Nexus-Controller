package com.example.controller

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class NetworkController {

    enum class State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    var onStateChanged: ((State) -> Unit)? = null
    var onLatencyUpdate: ((Long) -> Unit)? = null
    var onRumble: ((Int, Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    
    // Server socket for USB mode
    private var serverSocket: java.net.ServerSocket? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = AtomicBoolean(false)
    
    // Optimization: Use a Conflated Channel for input packets.
    // This ensures that if the network lags, we drop old frames and only send the latest one.
    // This prevents "spiral of death" lag and ensures lowest possible latency.
    private val inputChannel =  kotlinx.coroutines.channels.Channel<ByteArray>(kotlinx.coroutines.channels.Channel.CONFLATED)
    private val otherChannel = kotlinx.coroutines.channels.Channel<ByteArray>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    private var lastPingTime = 0L

    fun connect(ip: String? = null) {
        if (isRunning.get()) return
        isRunning.set(true)
        
        scope.launch {
            try {
                notifyState(State.CONNECTING)

                if (ip == null) {
                    Log.d("Nexus", "Starting USB Server wait...")
                    serverSocket = java.net.ServerSocket(6000)
                    socket = serverSocket?.accept()
                    Log.d("Nexus", "USB Connected!")
                } else {
                    Log.d("Nexus", "Connecting to $ip:6000...")
                    socket = Socket()
                    socket?.connect(InetSocketAddress(ip, 6000), 5000)
                }

                socket?.tcpNoDelay = true
                outputStream = socket?.getOutputStream()
                inputStream = socket?.getInputStream()

                // Handshake
                outputStream?.write(0x10)
                outputStream?.flush()

                // Start Sender Loops
                startSendLoops()
                
                // Start Ping Loop
                startPingLoop()

                notifyState(State.CONNECTED)

                // Read Loop
                val buffer = ByteArray(1024)
                while (isRunning.get()) {
                    val header = inputStream?.read() ?: -1
                    if (header == -1) throw Exception("EOF")

                    if (header == 0x11) {
                         Log.d("Nexus", "Handshake OK")
                    } 
                    else if (header == 0xF1) { // PONG
                        val lat = System.currentTimeMillis() - lastPingTime
                        onLatencyUpdate?.invoke(lat)
                    }
                    else if (header == 0x03) { // RUMBLE
                        val large = inputStream?.read() ?: 0
                        val small = inputStream?.read() ?: 0
                        onRumble?.invoke(large, small)
                    }
                }

            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e("Nexus", "Connection Error: $e")
                    onError?.invoke(e.message ?: "Connection Lost")
                }
            } finally {
                cleanup()
            }
        }
    }

    private fun startSendLoops() {
        // High Priority Input Loop (Conflated)
        scope.launch {
            while (isRunning.get()) {
                val packet = inputChannel.receive()
                try {
                    streamMutex.withLock {
                        outputStream?.write(packet)
                        // We rely on TCP_NODELAY for immediate send without explicit flush for every packet
                    }
                } catch (e: Exception) { 
                    // Network errors handled by reader loop
                }
            }
        }
        
        // Low Priority / Reliable Loop (Text, Mouse, etc)
        // OPTIMIZED: Batching to prevent Flush-Storms on mouse drag
        scope.launch {
            while (isRunning.get()) {
                val packet = otherChannel.receive()
                try {
                    streamMutex.withLock {
                        outputStream?.write(packet)
                        
                        // Smart Batching: If more packets are waiting (e.g. rapid mouse moves), 
                        // write them all (up to limit) before flushing.
                        var batchCount = 0
                        while (batchCount < 20) {
                            val next = otherChannel.tryReceive().getOrNull() ?: break
                            outputStream?.write(next)
                            batchCount++
                        }
                        
                        outputStream?.flush()
                    }
                } catch (e: Exception) { }
            }
        }
    }

    private fun startPingLoop() {
        scope.launch {
            while (isRunning.get()) {
                delay(1000)
                try {
                    lastPingTime = System.currentTimeMillis()
                    // Send directly or via otherChannel to ensure it goes out
                    // We use otherChannel to avoid threading collisions on outputStream if we didn't lock
                    // But here we have two loops writing to same stream.
                    // We MUST synchronize the stream writing if we have multiple threads!
                    // Actually, let's use a single unified Send Loop to be safe or use a mutex inside the loops?
                    // Better: Send Ping via otherChannel
                    otherChannel.trySend(byteArrayOf(0xF0.toByte()))
                } catch (e: Exception) { }
            }
        }
    }
    
    // NOTE: Having multiple coroutines write to 'outputStream' concurrently is UNSAFE without a lock.
    // However, I separated them into channels. I should merge them or lock them.
    // The previous implementation used a Mutex. 
    // Let's protect the write with a Mutex or use a single Select loop.
    // Since we want priorities (Input > Text), a single loop with 'select' is complex in Kotlin compared to just locking.
    // But since 'outputStream.write' is blocking, we don't want the Conflated loop to block on the Text loop.
    // Sol: Use a unified loop with priority? 
    // Actually, simply wrapping 'write' in `synchronized(outputStream)` or a mutex is enough.
    // But I will refine 'startSendLoops' to use a single consumer for thread safety.
    
    // RE-WRITING startSendLoops to be SAFE:
    /*
    private fun startSendLoops() {
        scope.launch {
            // merge channels?
            // Simple approach: Use `select` or just launch one loop that checks both? 
            // No, select is experimental.
            // Let's just use a Mutex like before, but now we only contend from 2 loops instead of 1000s.
        }
    }
    */
    
    // Let's refine the replacement to include the Mutex again for safety.
    private val streamMutex = Mutex()

    fun sendInput(
        lx: Int, ly: Int, rx: Int, ry: Int,
        btnsLow: Int, btnsHigh: Int,
        lt: Int, rt: Int,
        roll: Int, pitch: Int
    ) {
        if (!isRunning.get()) return
        
        // Create packet
        val packet = ByteArray(17)
        packet[0] = 0x01.toByte()
        // Map 0..255 to -128..127 for sticks
        packet[1] = (lx - 128).toByte()
        packet[2] = (ly - 128).toByte()
        packet[3] = (rx - 128).toByte()
        packet[4] = (ry - 128).toByte()
        packet[5] = btnsLow.toByte()
        packet[6] = btnsHigh.toByte()
        packet[7] = lt.toByte()
        packet[8] = rt.toByte()
        packet[9] = (roll shr 8).toByte()
        packet[10] = (roll).toByte()
        packet[11] = (pitch shr 8).toByte()
        packet[12] = (pitch).toByte()
        
        inputChannel.trySend(packet)
    }
    
    fun sendText(text: String) {
        val b = text.toByteArray()
        val len = b.size.coerceAtMost(255)
        val packet = ByteArray(2 + len)
        packet[0] = 0x02.toByte()
        packet[1] = len.toByte()
        System.arraycopy(b, 0, packet, 2, len)
        otherChannel.trySend(packet)
    }
    
    private var mouseAccX = 0f
    private var mouseAccY = 0f
    private var lastMouseBtns = -1

    fun sendMouse(dx: Float, dy: Float, left: Boolean, right: Boolean, sensitivity: Float = 1.0f) {
        mouseAccX += dx * sensitivity
        mouseAccY += dy * sensitivity
        
        val ix = mouseAccX.toInt()
        val iy = mouseAccY.toInt()
        val btns = (if(left) 1 else 0) or (if(right) 2 else 0)
        
        if (ix != 0 || iy != 0 || btns != lastMouseBtns) {
            // Clamp to byte range
            val finalX = ix.coerceIn(-128, 127).toByte()
            val finalY = iy.coerceIn(-128, 127).toByte()
            val p = byteArrayOf(0x04.toByte(), finalX, finalY, btns.toByte())
            otherChannel.trySend(p)
            mouseAccX -= ix
            mouseAccY -= iy
            lastMouseBtns = btns
        }
    }

    private var scrollAccX = 0f
    private var scrollAccY = 0f
    fun sendScroll(dx: Float, dy: Float, sensitivity: Float = 1.0f) {
        scrollAccX += dx * sensitivity
        scrollAccY += dy * sensitivity
        
        val ix = scrollAccX.toInt()
        val iy = scrollAccY.toInt()
        
        if (ix != 0 || iy != 0) {
            val finalX = ix.coerceIn(-128, 127).toByte()
            val finalY = iy.coerceIn(-128, 127).toByte()
            val packet = byteArrayOf(0x05.toByte(), finalX, finalY)
            otherChannel.trySend(packet)
            scrollAccX -= ix
            scrollAccY -= iy
        }
    }
    
    fun disconnect() {
        isRunning.set(false)
        cleanup()
    }

    private fun cleanup() {
        try {
            socket?.close()
            serverSocket?.close()
        } catch (e: Exception) {}
        socket = null
        serverSocket = null
        outputStream = null
        inputStream = null
        notifyState(State.DISCONNECTED)
    }

    private fun notifyState(s: State) {
        GlobalScope.launch(Dispatchers.Main) {
            onStateChanged?.invoke(s)
        }
    }

    // --- Discovery ---
    private var isDiscovering = false
    
    fun startDiscovery(context: android.content.Context, onFound: (String, String) -> Unit) {
        if (isDiscovering) return
        isDiscovering = true
        scope.launch {
            var ds: DatagramSocket? = null
            try {
                ds = DatagramSocket()
                ds.broadcast = true
                ds.soTimeout = 2000
                
                val req = "DISCOVER_CONTROLLER".toByteArray()
                val pkt = DatagramPacket(req, req.size, java.net.InetAddress.getByName("255.255.255.255"), 6001)
                
                while(isDiscovering) {
                    try {
                        ds.send(pkt)
                        val buf = ByteArray(1024)
                        val rcv = DatagramPacket(buf, buf.size)
                        ds.receive(rcv)
                        val s = String(rcv.data, 0, rcv.length)
                        if (s.startsWith("PC_SERVER:")) {
                            val name = s.substringAfter(":")
                            withContext(Dispatchers.Main) { onFound(rcv.address.hostAddress, name) }
                        }
                    } catch (e: Exception) {}
                    delay(1000)
                }
            } catch (e: Exception) {
            } finally {
                ds?.close()
            }
        }
    }
    
    fun stopDiscovery() {
        isDiscovering = false
    }
}
