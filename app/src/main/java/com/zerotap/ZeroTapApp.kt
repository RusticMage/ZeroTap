package com.zerotap

import android.app.Application
import android.content.Context

object ServiceLocator {
    // Stubs for real dependencies
    lateinit var appContext: Context
    
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }
}

class ZeroTapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(this)
    }
}
