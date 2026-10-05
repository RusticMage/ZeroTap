package com.zerotap.data.repository

import com.zerotap.data.db.dao.TrustedPlaceDao
import com.zerotap.data.db.entity.toDomain
import com.zerotap.data.db.entity.toEntity
import com.zerotap.domain.model.TrustedPlace
import com.zerotap.domain.repository.TrustedPlaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TrustedPlaceRepositoryImpl(
    private val dao: TrustedPlaceDao
) : TrustedPlaceRepository {

    override fun observeTrustedPlaces(): Flow<List<TrustedPlace>> {
        return dao.getAllTrustedPlaces().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getTrustedPlaceById(id: String): TrustedPlace? {
        return dao.getPlaceById(id)?.toDomain()
    }

    override suspend fun addTrustedPlace(place: TrustedPlace) {
        dao.insert(place.toEntity())
    }

    override suspend fun updateTrustedPlace(place: TrustedPlace) {
        dao.update(place.toEntity())
    }

    override suspend fun deleteTrustedPlace(id: String) {
        dao.deleteById(id)
    }
}
