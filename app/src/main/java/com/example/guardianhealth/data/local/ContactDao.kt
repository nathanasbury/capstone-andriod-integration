package com.example.guardianhealth.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT * FROM emergency_contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<Contact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact)

    @Delete
    suspend fun deleteContact(contact: Contact)

    /** Non-Flow version for one-shot reads (e.g., sending SMS) */
    @Query("SELECT * FROM emergency_contacts")
    suspend fun getAllContactsList(): List<Contact>
}
