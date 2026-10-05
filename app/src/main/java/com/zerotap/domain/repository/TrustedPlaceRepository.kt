package com.zerotap.domain.repository

import com.zerotap.domain.model.TrustedPlace
import kotlinx.coroutines.flow.Flow

interface TrustedPlaceRepository {
    fun observeTrustedPlaces(): Flow<List<TrustedPlace>>
    suspend fun getTrustedPlaceById(id: String): TrustedPlace?
    suspend fun addTrustedPlace(place: TrustedPlace)
    suspend fun updateTrustedPlace(place: TrustedPlace)
    suspend fun deleteTrustedPlace(id: String)
}
