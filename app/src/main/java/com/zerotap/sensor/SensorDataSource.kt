package com.zerotap.sensor

import kotlinx.coroutines.flow.Flow

interface SensorDataSource<T> {
    val dataFlow: Flow<T>
    fun start()
    fun stop()
    val isActive: Boolean
}
