package com.fungarium.gdziejestkuku
/*
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidmads.library.qrgenearator.QRGContents
import androidmads.library.qrgenearator.QRGEncoder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fungarium.gdziejestkuku.ui.theme.GdzieJestKuKuTheme
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning


class MainActivity : ComponentActivity() {

    // Declare the launcher at the top of your Activity/Fragment:
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            isGranted: Boolean ->
                if (isGranted) {
                    // FCM SDK (and your app) can post notifications.
                } else {
                    // TODO: Inform user that that your app will not show notifications.
                }
        }

    private lateinit var functions: FirebaseFunctions;

    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
            } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                // TODO: display an educational UI explaining to the user the features that will be enabled
                //       by them granting the POST_NOTIFICATION permission. This UI should provide the user
                //       "OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
                //       If the user selects "No thanks," allow the user to continue without notifications.
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var activity = this;

        this.functions = Firebase.functions;
        
        setContent {
            GdzieJestKuKuTheme {
                val navController = rememberNavController()
                val folowees = remember { mutableListOf<Pair<String, String>>() }

                LaunchedEffect(Unit) {
                    var prefs = activity.getPreferences(Context.MODE_PRIVATE);
                    var f = prefs.getStringSet("folowees", HashSet())
                    folowees.addAll(f!!.map { s -> Pair(s.substringBefore(':'), s.substringAfter(':')) })
                }

                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable(route = "home") {
                            HomeScreen(
                                folowees,
                                { navController.navigate("share-location") },
                                { navController.navigate("add-folowee") },
                                { nickname, token -> navController.navigate("where-is/"+token) })
                        }
                        composable(route = "share-location") {
                            ShareLocationScreen()
                        }
                        composable(route = "add-folowee") {
                            AddFoloweeScreen(
                                folowees,
                                { navController.popBackStack() },
                                { navController.popBackStack() }
                            )
                        }
                        composable(route = "where-is/{token}") {
                            WhereIsKuKuScreen(
                                it.arguments?.getString("token")!!)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(folowees : MutableList<Pair<String, String>>, onClickShareLocation: () -> Unit = {}, onClickAddFolowee : () -> Unit, onWhereIs : (nickname: String, token : String) -> Unit, modifier: Modifier = Modifier) {
    var activity = LocalContext.current as Activity;

    Column {
        Text("Foo")
        Button(onClick = onClickShareLocation) {
            Text(text = "Share your location")
        }
        Button(onClick = onClickAddFolowee) {
            Text(text = "Add Folowee")
        }
        for ((nickname, token) in folowees){
            Button(onClick = { onWhereIs(nickname, token) }) {
                Text(text = "Where is " + nickname)
            }
        }
    }
}

@Composable
fun ShareLocationScreen(modifier: Modifier = Modifier) {
    var img = produceState<ImageBitmap?>(initialValue = null) {

        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("Foo", "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result

            val qrgEncoder =  QRGEncoder(token, null, QRGContents.Type.TEXT, 500)
            qrgEncoder.setColorBlack(Color.WHITE)
            qrgEncoder.setColorWhite(Color.BLACK)
            value = qrgEncoder.bitmap.asImageBitmap()
        })
    }

    Column {
        Text("Share location")
        if (img.value != null)
            Image(bitmap = img.value!!, "QRCode")
        else
            Text("Loading...");
    }
}

@Composable
fun AddFoloweeScreen(folowees : MutableList<Pair<String, String>>, onAddedFolowee: () -> Unit, onCanceled: () -> Unit = {}, modifier: Modifier = Modifier) {

    var activity = LocalContext.current as Activity;

    var code = produceState(initialValue = "") {
        val scanner = GmsBarcodeScanning.getClient(activity);
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                value = barcode.rawValue ?: ""
            }
            .addOnCanceledListener {
                onCanceled()
            }
            .addOnFailureListener { e ->
                value = e.toString()
            }
    };

    fun addFolowee(nickname: String, token : String) {
        var prefs = activity.getPreferences(Context.MODE_PRIVATE);
        var f = HashSet(prefs.getStringSet("folowees", HashSet()));
        var prefsEditor = prefs.edit();
        f!!.add(nickname + ":" + token)
        prefsEditor.putStringSet("folowees", f);
        prefsEditor.apply();
        folowees.add(Pair(nickname, token))
        onAddedFolowee();
    }

    var nickname by remember { mutableStateOf("") }

    Column {
        Text("Follow")
        Text(code.value)
        if (code.value != null) {
            BasicTextField(value = nickname, onValueChange = { s -> nickname = s })
            Button(onClick = { addFolowee(nickname, code.value) }) {
                Text("Add")
            }
        }
    }
}

@Composable
fun WhereIsKuKuScreen(foloweeToken : String, modifier: Modifier = Modifier) {
    var functions = remember { FirebaseFunctions.getInstance() }

    LaunchedEffect(Unit) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("Foo", "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }

            // Get new FCM registration token
            val myToken = task.result
            var r = functions
                .getHttpsCallable("requestLocation")
                .call(hashMapOf("recipient" to foloweeToken, "sender" to myToken))
        })
    }

    val location = Locations.getLastLocationOf(foloweeToken)
    if (location != null) {
        val latLng = LatLng(location.first, location.second)
        val cameraPositionState = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(latLng, 10f)
        }
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            Marker(
                state = MarkerState(position = latLng),
                title = "Kuku"
            )
        }

    } else
    {
        Text("Updating...")
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    GdzieJestKuKuTheme {
        HomeScreen(mutableListOf<Pair<String, String>>(), {}, {}, { nickname, token ->  })
    }
}

@Preview(showBackground = true)
@Composable
fun ShareLocationScreenPreview() {
    GdzieJestKuKuTheme {
        ShareLocationScreen()
    }
}*/
