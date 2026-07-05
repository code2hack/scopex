package com.code2hack.scopex

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper

enum class CaptureProofStopReason { Stopped, Error }

object CaptureProofFrameBus {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var listener: ((Bitmap) -> Unit)? = null
    private var stopListener: ((CaptureProofStopReason) -> Unit)? = null
    private var pendingFrame: Bitmap? = null
    private var drainPosted = false
    private var generation = 0

    fun setListener(nextListener: ((Bitmap) -> Unit)?) {
        val frame = synchronized(lock) {
            listener = nextListener
            if (nextListener == null) clearPendingFrameLocked() else null
        }
        frame?.recycle()
    }

    fun setStopListener(nextListener: ((CaptureProofStopReason) -> Unit)?) {
        synchronized(lock) {
            stopListener = nextListener
        }
    }

    fun notifyStopped(reason: CaptureProofStopReason = CaptureProofStopReason.Stopped) {
        mainHandler.post {
            val currentListener = synchronized(lock) { stopListener }
            currentListener?.invoke(reason)
        }
    }

    fun beginSession(): Int {
        var frame: Bitmap? = null
        val sessionId = synchronized(lock) {
            generation++
            drainPosted = false
            frame = clearPendingFrameLocked()
            generation
        }
        frame?.recycle()
        return sessionId
    }

    fun clear() {
        val frame = synchronized(lock) {
            generation++
            drainPosted = false
            clearPendingFrameLocked()
        }
        frame?.recycle()
    }

    fun publish(sessionId: Int, frame: Bitmap) {
        var frameToRecycle: Bitmap? = null
        var drainSessionId: Int? = null
        synchronized(lock) {
            if (sessionId != generation || listener == null) {
                frameToRecycle = frame
            } else {
                frameToRecycle = pendingFrame
                pendingFrame = frame
                if (!drainPosted) {
                    drainPosted = true
                    drainSessionId = generation
                }
            }
        }
        frameToRecycle?.recycle()
        drainSessionId?.let { mainHandler.post { drain(it) } }
    }

    private fun drain(sessionId: Int) {
        var frameToRecycle: Bitmap? = null
        synchronized(lock) {
            if (sessionId == generation) {
                drainPosted = false
                val frame = clearPendingFrameLocked()
                val currentListener = listener
                if (frame != null) {
                    if (currentListener == null) {
                        frameToRecycle = frame
                    } else {
                        currentListener.invoke(frame)
                    }
                }
            }
        }

        frameToRecycle?.recycle()
    }

    private fun clearPendingFrameLocked(): Bitmap? {
        val frame = pendingFrame
        pendingFrame = null
        return frame
    }
}
