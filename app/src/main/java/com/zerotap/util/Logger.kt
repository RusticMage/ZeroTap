package com.zerotap.util

import android.util.Log

object Logger {
    private const val PREFIX_SENSOR = "[Sensor]"
    private const val PREFIX_AI = "[AI]"
    private const val PREFIX_RISK = "[Risk]"
    private const val PREFIX_INCIDENT = "[Incident]"
    private const val PREFIX_EVIDENCE = "[Evidence]"
    private const val PREFIX_ALERT = "[Alert]"
    private const val PREFIX_DEBUG = "[Debug]"

    fun sensor(tag: String, message: String) = log(PREFIX_SENSOR, tag, message)
    fun ai(tag: String, message: String) = log(PREFIX_AI, tag, message)
    fun risk(tag: String, message: String) = log(PREFIX_RISK, tag, message)
    fun incident(tag: String, message: String) = log(PREFIX_INCIDENT, tag, message)
    fun evidence(tag: String, message: String) = log(PREFIX_EVIDENCE, tag, message)
    fun alert(tag: String, message: String) = log(PREFIX_ALERT, tag, message)
    fun debug(tag: String, message: String) = log(PREFIX_DEBUG, tag, message)

    private fun log(prefix: String, tag: String, message: String) {
        Log.d("ZeroTap-$tag", "$prefix $message")
    }
}
