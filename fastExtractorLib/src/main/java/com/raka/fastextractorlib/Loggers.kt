package com.raka.fastextractorlib

import android.util.Log

object Loggers {
    private const val DEBUG_MODE = false
    fun e(TAG: String?, msg: String) {
        if (DEBUG_MODE) {
            Log.e(TAG, msg + "")
        }
    }

    fun e(TAG: String?, msg: Int) {
        if (DEBUG_MODE) {
            Log.e(TAG, msg.toString() + "")
        }
    }
}