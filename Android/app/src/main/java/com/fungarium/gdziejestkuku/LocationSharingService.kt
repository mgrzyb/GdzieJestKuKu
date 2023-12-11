package com.fungarium.gdziejestkuku

import android.Manifest
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase

class LocationSharingService : Service() {

    private val CHANNEL_ID = "ForegroundService Kotlin"

    companion object {
        fun startService(context: Context, requestId: String) {
            val startIntent = Intent(context, LocationSharingService::class.java)
            startIntent.putExtra("requestId", requestId)
            ContextCompat.startForegroundService(context, startIntent)
        }
        fun stopService(context: Context) {
            val stopIntent = Intent(context, LocationSharingService::class.java)
            context.stopService(stopIntent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //do heavy work on a background thread

        val requestId = intent?.getStringExtra("requestId")

        createNotificationChannel()

        val notificationIntent = Intent(this, GdzieJestKukuActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Foreground Service Kotlin Example")
            .setContentText("Acquiring current location")
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Log.w("Foo", "Cannot access location")
            stopForeground(STOP_FOREGROUND_REMOVE)
            return START_NOT_STICKY;
        }

        Log.d("Foo", "Trying to retrieve location")
        val locationClient = LocationServices.getFusedLocationProviderClient(this)
        val functions = Firebase.functions;

        functions
            .getHttpsCallable("ackLocationRequest")
            .call(
                hashMapOf(
                    "requestId" to requestId
                )
            ).addOnCompleteListener {
                locationClient
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnFailureListener { exception -> Log.e("Foo", "Failed to retrieve location", exception) }
                    .addOnCanceledListener { Log.e("Foo", "Location request was cancelled") }
                    .addOnSuccessListener { location ->
                        val lat = location.latitude
                        val lon = location.longitude
                        Log.i("Foo", String.format("Received location: %s, %s", lat, lon))

                        Log.i("Foo", "Invoking sendLocation")
                        functions
                            .getHttpsCallable("sendLocation")
                            .call(
                                hashMapOf(
                                    "requestId" to requestId,
                                    "locationLat" to lat.toString(),
                                    "locationLon" to lon.toString()
                                )
                            ).addOnCompleteListener { stopForeground(STOP_FOREGROUND_REMOVE) }
                    }
            }

        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent): IBinder? {
        return null
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }
    }
}