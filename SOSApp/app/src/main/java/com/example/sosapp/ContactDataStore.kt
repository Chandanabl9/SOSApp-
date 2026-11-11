package com.example.sosapp

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Create DataStore
val Context.dataStore by preferencesDataStore(name = "contacts_store")

object ContactDataStore {

    private val CONTACTS_KEY = stringPreferencesKey("contacts_json")

    // Save contacts to datastore
    suspend fun saveContacts(context: Context, contacts: List<EmergencyContact>) {
        val formatted = contacts.joinToString("|") {
            "${it.name}#${it.phoneNumber}"
        }

        context.dataStore.edit { prefs ->
            prefs[CONTACTS_KEY] = formatted
        }
    }

    // Load contacts from datastore
    fun getContacts(context: Context): Flow<List<EmergencyContact>> {
        return context.dataStore.data.map { prefs ->

            val saved = prefs[CONTACTS_KEY] ?: ""

            if (saved.isBlank()) emptyList()
            else saved.split("|").map {
                val parts = it.split("#")
                EmergencyContact(parts[0], parts[1])
            }
        }
    }
}
