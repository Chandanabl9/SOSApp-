package com.example.sosapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object EmergencyHelper {

    // --------------------------------------------------
    // MAIN FUNCTION — CALL + SMS + LOCATION
    // --------------------------------------------------
     fun triggerEmergency(
        context: Context,
        contacts: List<EmergencyContact>,
        lat: Double,
        lon: Double
    ) {

        if (contacts.isEmpty()) {
            Toast.makeText(context, "No contacts available", Toast.LENGTH_SHORT).show()
            return
        }

        val locationUrl = "https://www.google.com/maps/search/?api=1&query=$lat,$lon"
        val message = "⚠️ HELP! I am in danger.\nMy Location: $locationUrl"

        val smsManager = SmsManager.getDefault()

        // ------------------------------
        // 1️⃣ FIRST CONTACT → CALL + SMS
        // ------------------------------
        val first = contacts[0]

        try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${first.phoneNumber}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(callIntent)
        } catch (e: SecurityException) {
            Toast.makeText(context, "CALL permission denied!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // SMS to first contact
        try {
            smsManager.sendTextMessage(first.phoneNumber, null, message, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // ------------------------------
        // 2️⃣ Remaining contacts → SMS only
        // ------------------------------
        for (i in 1 until contacts.size) {
            val contact = contacts[i]
            try {
                smsManager.sendTextMessage(contact.phoneNumber, null, message, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    // --------------------------------------------------
    // FETCH CONTACTS + LOCATION → TRIGGER SOS
    // --------------------------------------------------
    private fun fetchAndTrigger(context: Context) {

        CoroutineScope(Dispatchers.Main).launch {

            // Get saved contacts list
            val contacts = ContactDataStore.getContacts(context).first()

            if (contacts.isEmpty()) {
                Toast.makeText(context, "No contacts found!", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Get location
            LocationHelper.getCurrentLocation(context) { lat, lon ->
                triggerEmergency(context, contacts, lat, lon)
            }
        }
    }


    // --------------------------------------------------
    // GESTURE SOS
    // --------------------------------------------------
    fun triggerEmergencyAfterGesture(context: Context) {
        Toast.makeText(context, "Gesture SOS Triggered!", Toast.LENGTH_SHORT).show()
        fetchAndTrigger(context)
    }


    // --------------------------------------------------
    // VOICE KEYWORD SOS (Saying “HELP”)
    // Called from your VoiceListenerService
    // --------------------------------------------------
    fun triggerKeywordSOS(context: Context) {
        Toast.makeText(context, "Voice SOS Triggered!", Toast.LENGTH_LONG).show()
        fetchAndTrigger(context)
    }
}
