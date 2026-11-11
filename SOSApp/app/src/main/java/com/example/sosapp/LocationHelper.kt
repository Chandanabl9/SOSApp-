package com.example.sosapp

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

object LocationHelper {

    private var fusedLocationClient: FusedLocationProviderClient? = null

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(context: Context, callback: (Double, Double) -> Unit) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        fusedLocationClient?.lastLocation?.addOnSuccessListener { location ->
            if (location != null) {
                callback(location.latitude, location.longitude)
            } else {
                callback(0.0, 0.0)
            }
        }?.addOnFailureListener {
            callback(0.0, 0.0)
        }
    }
}
