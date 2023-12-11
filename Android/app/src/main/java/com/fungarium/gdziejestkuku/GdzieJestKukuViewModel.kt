package com.fungarium.gdziejestkuku

import android.content.SharedPreferences
import android.graphics.Color
import androidmads.library.qrgenearator.QRGContents
import androidmads.library.qrgenearator.QRGEncoder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.asDeferred
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class Followee {
    val nickname : String;
    var token : String;

    constructor(nickname: String, token: String) {
        this.nickname = nickname
        this.token = token
    }
}

class GdzieJestKukuViewModel {

    private val _followees = mutableStateListOf<Followee>()
    val followees : List<Followee>
        get() = _followees

    private var _shareLocationDialog by mutableStateOf<ShareLocationDialogViewModel?>(null)
    val shareLocationDialog : ShareLocationDialogViewModel?
        get() = _shareLocationDialog;

    private var _addFolloweeDialog by mutableStateOf<AddFolloweeDialogViewModel?>(null);
    val addFolloweeDialog : AddFolloweeDialogViewModel?
        get() = _addFolloweeDialog

    private var _currentFolloweeLocationRequest by mutableStateOf<FolloweeLocationRequest?>(null);
    val currentFolloweeLocationRequest : FolloweeLocationRequest?
        get() = _currentFolloweeLocationRequest

    private var _followeeMarkers = mutableStateListOf<FolloweeMarker>()
    val followeeMarkers : List<FolloweeMarker>
        get() = _followeeMarkers

    private var _preferences: SharedPreferences

    private val _functions = FirebaseFunctions.getInstance();

    constructor(preferences : SharedPreferences) {
        _preferences = preferences;

        var f = preferences.getStringSet("followees", HashSet())
        _followees.addAll(f!!.map { s -> Followee(s.substringBefore(':'), s.substringAfter(':')) })
    }

    suspend fun addFollowee(scanner: GmsBarcodeScanner) {
        val barcode = scanner.startScan().asDeferred().await()
        val token = barcode.rawValue!!;

        _addFolloweeDialog = AddFolloweeDialogViewModel(
            onSubmit = {

                val f = HashSet(_preferences.getStringSet("followees", HashSet()));
                val prefsEditor = _preferences.edit();
                f.add(it.nickname + ":" + token)
                prefsEditor.putStringSet("followees", f);
                prefsEditor.apply();

                _followees.add(Followee(it.nickname, token))

                _addFolloweeDialog = null
            },
            onDismiss = {
                _addFolloweeDialog = null
            });
    }

    fun removeFollowee(followee: Followee){
        val f = HashSet(_preferences.getStringSet("followees", HashSet()));
        val prefsEditor = _preferences.edit();
        f.remove(followee.nickname + ":" + followee.token)
        prefsEditor.putStringSet("followees", f);
        prefsEditor.apply();

        _followees.remove(followee);
    }

    suspend fun requestFolloweeLocation(followee: Followee) {
        val request = FolloweeLocationRequest(followee).also { _currentFolloweeLocationRequest = it }

        request.onAcquiringFCMToken()
        var myToken = FirebaseMessaging.getInstance().token.asDeferred().await();
        request.onAcquiredFCMToken()

        request.onSendingLocationRequest()
        var r1 = _functions
            .getHttpsCallable("requestLocation")
            .call(hashMapOf("recipient" to followee.token, "sender" to myToken)).asDeferred().await()

        var locationRequestId = (r1.data as HashMap<String, Object>)["requestId"];

        for (i in 1..5) {
            if (request.cancelled)
                break;

            delay(i.seconds)

            var r2 = _functions
                .getHttpsCallable("getLocationRequestStatus")
                .call(hashMapOf("requestId" to locationRequestId)).asDeferred().await()

            var f = r2.data as HashMap<String, Object>
            when(f["status"].toString()) {
                "new" -> {}
                "sent" -> {
                    request.onSentLocationRequest()
                }
                "received" -> {
                    request.onLocationRequestAcknowledged()
                }
                "fulfilled" -> {
                    val position = LatLng(
                        f["locationLat"].toString().toDouble(),
                        f["locationLon"].toString().toDouble()
                    )
                    updateMarker(followee.token, position, Instant.now())
                    _currentFolloweeLocationRequest = null;
                    break;
                }
            }
        }

        _currentFolloweeLocationRequest = null
    }

    suspend fun shareLocation() {
        var myToken = FirebaseMessaging.getInstance().token.asDeferred().await();

        val qrgEncoder = QRGEncoder(myToken, null, QRGContents.Type.TEXT, 1000)
        qrgEncoder.setColorBlack(Color.WHITE)
        qrgEncoder.setColorWhite(Color.BLACK)

        this._shareLocationDialog = ShareLocationDialogViewModel(qrgEncoder.bitmap.asImageBitmap(), { this._shareLocationDialog = null })
    }

    fun updateMarker(followeeToken: String, position: LatLng, timestamp: Instant) {
        val followee = followees.find { it.token === followeeToken}
        if (followee !== null)
            updateMarker(followee, position, timestamp)
    }

    private fun updateMarker(followee: Followee, position: LatLng, timestamp: Instant) {
        var existingMarker = _followeeMarkers.find { it.followee.token === followee.token }
        if (existingMarker === null) {
            _followeeMarkers.add(FolloweeMarker(followee, position, Instant.now()))
        } else {
            existingMarker.updatePosition(position, Instant.now())
        }
    }

}

class FolloweeLocationRequest {

    private val _log = mutableStateListOf<String>()
    val log : List<String>
        get() = _log

    private var _followee: Followee

    var cancelled = false
        public get() = field

    constructor(followee: Followee) {
        _followee = followee;
    }

    fun onAcquiringFCMToken() {
        _log.add("Getting ready to request ${_followee.nickname}'s location...")
    }
    fun onAcquiredFCMToken() {
    }
    fun onSendingLocationRequest() {
        _log.add("Sending location request...")
    }
    fun onSentLocationRequest() {
        _log.add("Location request sent")
    }
    fun onLocationRequestAcknowledged() {
        _log.add("Acquiring device location...")
    }

    fun cancel() {
        this.cancelled = true
    }
}

