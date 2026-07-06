package com.code2hack.scopex

class CaptureProofFrameStore<T> {
    var latest: T? = null
        private set

    fun replace(frame: T) { latest = frame }
    fun clear() { latest = null }
}
