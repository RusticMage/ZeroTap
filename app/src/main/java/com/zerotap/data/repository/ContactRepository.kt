package com.zerotap.data.repository

import com.zerotap.data.db.dao.TrustedContactDao
import com.zerotap.data.db.entity.toDomain
import com.zerotap.data.db.entity.toEntity
import com.zerotap.domain.model.TrustedContact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ContactRepository(private val dao: TrustedContactDao) {

    fun getAllContacts(): Flow<List<TrustedContact>> {
        return dao.getAllContacts().map { list -> list.map { it.toDomain() } }
    }
    
    suspend fun getById(id: String): TrustedContact? {
        return dao.getContactById(id)?.toDomain()
    }
    
    suspend fun save(contact: TrustedContact) {
        dao.insert(contact.toEntity())
    }
    
    suspend fun update(contact: TrustedContact) {
        dao.update(contact.toEntity())
    }
    
    suspend fun delete(id: String) {
        dao.deleteById(id)
    }
}
