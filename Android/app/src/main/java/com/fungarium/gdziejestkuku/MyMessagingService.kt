package com.fungarium.gdziejestkuku

import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

object Locations {
    private val _updates = MutableStateFlow<Triple<String, LatLng, Instant>?>(null)
    val updates : StateFlow<Triple<String, LatLng, Instant>?>
        get() = _updates;

    fun updateLocationOf(token : String, location: LatLng) {
        this._updates.value = Triple(token, location, Instant.now());
    }
}
class MyMessagingService : FirebaseMessagingService() {

    private lateinit var locationClient: FusedLocationProviderClient
    private lateinit var functions: FirebaseFunctions

    override fun onCreate() {
        super.onCreate()
        this.locationClient = LocationServices.getFusedLocationProviderClient(this)
        this.functions = Firebase.functions;
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val messageType = data["messageType"]
        Log.d("Foo", "Message received: $messageType")

        when (messageType) {
            "location-request" -> {
                    LocationSharingService.startService(this, data["requestId"].toString())
                }

            "location" -> {
                val followeeToken = data["requestRecipient"]
                var lat = data["locationLat"]
                var lon = data["locationLon"]

                Locations.updateLocationOf(followeeToken!!, LatLng(lat!!.toDouble(), lon!!.toDouble()));
            }
        }
    }
}