@file:OptIn(ExperimentalMaterial3Api::class)

package com.fungarium.gdziejestkuku

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import com.fungarium.gdziejestkuku.ui.theme.GdzieJestKuKuTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import java.time.Instant


class GdzieJestKukuActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModel = GdzieJestKukuViewModel(this.getPreferences(MODE_PRIVATE))

        val extras = intent.extras

        if (extras != null) {
            val followeeToken = extras.getString("requestRecipient");
            if (followeeToken != null) {
                val lat = extras.getString("locationLat")!!.toDouble();
                val lon = extras.getString("locationLon")!!.toDouble();

                viewModel.updateMarker(followeeToken, LatLng(lat, lon), Instant.now())
            }
        }

        setContent {
            LaunchedEffect(Unit) {
                Locations.updates.collect {
                    if (it !== null)
                        viewModel.updateMarker(it.first, it.second, it.third);
                }
            }
            GdzieJestKuKuTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GdzieJestKukuScreen(viewModel)
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GdzieJestKukuScreen(viewModel : GdzieJestKukuViewModel) {
    val activity = LocalContext.current as Activity;
    val scope = rememberCoroutineScope()

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(10.0, 10.0), 5f)
    }

    val settingsMenuExpanded = remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.followeeMarkers.size) {
        if (viewModel.followeeMarkers.size === 0)
            return@LaunchedEffect;

        var bounds = LatLngBounds(viewModel.followeeMarkers[0].position, viewModel.followeeMarkers[0].position)
        for (f in viewModel.followeeMarkers.drop(1))
            bounds = bounds.including(f.position)

        cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds, 500))
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false)
    ) {
        for(f in viewModel.followeeMarkers) {
            Marker(MarkerState(f.position), title = f.followee.nickname)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dp(10f))
            .wrapContentSize(Alignment.TopEnd)
    ) {
        DropdownMenu(
            expanded = settingsMenuExpanded.value,
            onDismissRequest = { settingsMenuExpanded.value = false }) {
            DropdownMenuItem(
                text = { Text("Share location") },
                onClick = {
                    settingsMenuExpanded.value = false
                    scope.launch { viewModel.shareLocation(); }
                })
            DropdownMenuItem(text = { Text("Follow") }, onClick = {
                settingsMenuExpanded.value = false
                scope.launch { viewModel.addFollowee(GmsBarcodeScanning.getClient(activity)) }
            })
        }
        IconButton(
            onClick = { settingsMenuExpanded.value = true }, modifier = Modifier.background(
                MaterialTheme.colorScheme.surfaceColorAtElevation(Dp(3f)), CircleShape
            )
        ) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "Menu")
        }
    }

    if (viewModel.addFolloweeDialog != null) {
        AddFolloweeDialog(viewModel.addFolloweeDialog!!)
    }

    if (viewModel.shareLocationDialog != null) {
        val dialog = viewModel.shareLocationDialog!!;
        Dialog(onDismissRequest = { dialog.dismiss() }) {
            Column {
                Text("Share location")
                Image(bitmap = dialog.qrCode, "QRCode")
            }
        }
    }

    var draggedFolloweePosition by remember { mutableStateOf(Offset.Zero) }
    var draggedFollowee by remember { mutableStateOf<Followee?>(null) }
    var dropTargeBounds by remember { mutableStateOf(Rect.Zero) }

    val deleting by remember { derivedStateOf { draggedFollowee != null && dropTargeBounds.contains(draggedFolloweePosition) } };

    if (draggedFollowee != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier
                .padding(Dp(50f))
                .size(Dp(80f))
                .background(
                    if (deleting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer,
                    CircleShape
                )
                .align(Alignment.Center)
                .onGloballyPositioned {
                    dropTargeBounds = it.boundsInWindow()
                }) {
                Icon(
                    Icons.Outlined.Delete, "Delete", modifier = Modifier
                        .align(Alignment.Center)
                        .size(Dp(50f))
                )
            };
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dp(10.0f)),
        verticalArrangement = Arrangement.spacedBy(Dp(10.0f), Alignment.Bottom),
        horizontalAlignment = Alignment.End
    ) {
        for (followee in viewModel.followees) {
            FolloweeButton(followee,
                onClick = { scope.launch { viewModel.requestFolloweeLocation(followee) } },
                onDragStart = {position ->
                    draggedFollowee = followee
                    draggedFolloweePosition = position
                },
                onDrag = { position -> draggedFolloweePosition = position },
                onDragEnd = {
                    if (deleting) {
                        viewModel.removeFollowee(followee)
                    }
                    draggedFollowee = null
                },
                onDragCancel = { draggedFollowee = null })
        }
    }

    if (viewModel.currentFolloweeLocationRequest != null) {
        val request = viewModel.currentFolloweeLocationRequest!!

        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { request.cancel() }, sheetState = sheetState) {
            Column(modifier = Modifier.padding(start=Dp(10f), top=Dp(1f), bottom=Dp(100f))) {
                for (m in request.log) {
                    Text(m)
                }
/*
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Button(
                        // Note: If you provide logic outside of onDismissRequest to remove the sheet,
                        // you must additionally handle intended state cleanup, if any.
                        onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    showBottomSheet = false
                                }
                            }
                        }
                    ) {
                        Text("Hide Bottom Sheet")
                    }
                }
*/
            }

        }
    }
}

/*
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    GdzieJestKuKuTheme {
        GdzieJestKukuScreen(GdzieJestKukuViewModel())
    }
}*/
