package com.fungarium.gdziejestkuku

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.maps.model.LatLng
import java.time.Instant

class FolloweeMarker(val followee: Followee, position: LatLng, timestamp: Instant) {

    private val _position : MutableState<LatLng>
    val position : LatLng
        get() = _position.value

    private val _timestamp : MutableState<Instant>
    val timestamp : Instant
        get() = _timestamp.value

    init {
        _position = mutableStateOf(position)
        _timestamp = mutableStateOf(timestamp)
    }

    fun updatePosition(position: LatLng, timestamp: Instant) {
        _position.value = position
        _timestamp.value = timestamp
    }

}