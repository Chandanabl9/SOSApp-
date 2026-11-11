package com.example.sosapp

import android.content.Context

object Prefs {
    private const val PREF_NAME = "sos_prefs"
    private const val KEY_VOICE = "voice_enabled"

    fun setVoiceEnabled(context: Context, enabled: Boolean) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_VOICE, enabled).apply()
    }

    fun isVoiceEnabled(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_VOICE, false)
    }
}
