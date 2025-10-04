package com.example.trackme // Use your actual package name

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
//import androidx.preference.PreferenceManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

import android.annotation.SuppressLint
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import android.os.Looper
import org.osmdroid.views.overlay.Marker // IMPORTANT for adding the location marker
import org.osmdroid.tileprovider.tilesource.TileSourceFactory // ADD THIS IMPORT
import org.osmdroid.views.overlay.Polyline // ADD THIS LINE






@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    // 1. UI Elements (The map, button, and status text)
    private lateinit var map: MapView
    private lateinit var trackButton: Button
    private lateinit var statusText: TextView

    // 2. Location Provider (Google's modern location API)
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Default location (e.g., somewhere central or your home city)
    private val DEFAULT_START_POINT = GeoPoint(51.505, -0.09)

    // Add these new properties near line 30, below 'private lateinit var fusedLocationClient: FusedLocationProviderClient'

    // 3. Trail and Marker Components
    private val pathPoints = mutableListOf<GeoPoint>() // Stores all points for the trail
    private val trailOverlay = Polyline()             // The actual line drawn on the map
    private var currentLocationMarker: Marker? = null  // Holds the single, moving marker



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // **IMPORTANT OSMDROID CONFIGURATION**
        // This is needed to configure the map tiles and tile cache.
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))

        setContentView(R.layout.activity_main)

        // Initialize UI components by finding them using their IDs from activity_main.xml
        map = findViewById(R.id.osm_map)
        trackButton = findViewById(R.id.track_button)
        statusText = findViewById(R.id.status_text)

        // Initialize the location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Start the permission check process
        checkLocationPermissions()

        // Configure the map view
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0) // Zoom level

        trackButton.setOnClickListener {
            if (trackButton.text.toString() == "START TRACKING") {
                checkLocationPermissions() // Will call startLocationUpdates if allowed
            } else {
                stopLocationUpdates()
            }
        }





    }
    // This is the variable that will manage the permission request dialog
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->

            // Check the results from the dialog
            when {
                // Check if FINE location was granted (the most accurate)
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                    // Permission granted! We can now initialize the location-dependent features.
                    setupMapAndLocation()
                }
                // Check if only COARSE location was granted (less accurate)
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                    // Only approximate location, which is fine for basic tracking
                    setupMapAndLocation()
                }
                // Permission denied
                else -> {
                    // 🛑 The user denied access.
                    statusText.text = "Error: Location permission denied. Map is restricted."
                    setupMapAndLocation() // Initialize map without location features
                }
            }
        }

    private fun checkLocationPermissions() {
        // Check if permissions are already granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Permissions are already granted, proceed directly to setup
            //setupMapAndLocation()
            startLocationUpdates()
        } else {
            // Permissions not granted, launch the request dialog
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // New function to handle map and location initialization AFTER permission is granted
    private fun setupMapAndLocation() {
        // 1. Set the Map's initial view
        map.controller.setZoom(15.0)
        map.controller.setCenter(DEFAULT_START_POINT)

        // 2. Set the initial status message
        statusText.text = "Ready to track. Press the button."

        // **Next step: We will fill in the logic to get the current location here!**
    }


    // ... we will add more functions here later ...

    // 7. Location Request Definition
    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000) // Update every 5 seconds
        .setMinUpdateIntervalMillis(2000) // Not faster than 2 seconds
        .build()

    // 8. Location Callback (Where the magic happens when a new location arrives)
//    private val locationCallback = object : LocationCallback() {
//        override fun onLocationResult(locationResult: LocationResult) {
//            locationResult.lastLocation?.let { location ->
//                val newLocation = GeoPoint(location.latitude, location.longitude)
//
//                // 1. Update map center
//                map.controller.animateTo(newLocation)
//
//                // 2. Add marker (optional, but helpful)
//                addMarker(newLocation)
//
//                // 3. Update status text
//                updateStatus("Lat: ${location.latitude}, Lon: ${location.longitude}")
//            }
//        }
//    }



    // Replace your existing 'private val locationCallback = object : LocationCallback() { ... }' block:

    // 8. Location Callback (Where the magic happens when a new location arrives)
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                val newLocation = GeoPoint(location.latitude, location.longitude)

                // 1. Record the point for the trail
                pathPoints.add(newLocation)

                // 2. Update the Polyline trail
                trailOverlay.setPoints(pathPoints)

                // 3. Ensure the trail is on the map (only adds it once)
                if (!map.overlays.contains(trailOverlay)) {
                    map.overlays.add(trailOverlay)
                }

                // 4. Update map center
                map.controller.animateTo(newLocation)

                // 5. Update the single, moving marker
                addOrUpdateMarker(newLocation)

                // 6. Update status text
                updateStatus("Lat: ${location.latitude}, Lon: ${location.longitude}")

                // 7. Force map redraw
                map.invalidate()
            }
        }
    }

    // 9. Function to start location updates
    @SuppressLint("MissingPermission") // We check permissions in checkLocationPermission()
    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        trackButton.text = "STOP TRACKING"
    }

//    // 10. Function to stop location updates
//    private fun stopLocationUpdates() {
//        fusedLocationClient.removeLocationUpdates(locationCallback)
//        trackButton.text = "START TRACKING"
//        updateStatus("Tracking stopped.")
//    }

// Replace your existing 'private fun stopLocationUpdates() { ... }' block:

    // 10. Function to stop location updates and clear the trail
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)

        // Reset the trail
        if (map.overlays.contains(trailOverlay)) {
            map.overlays.remove(trailOverlay)
        }
        pathPoints.clear() // Clear the recorded coordinates

        // Remove the marker
        currentLocationMarker?.let { map.overlays.remove(it) }
        currentLocationMarker = null

        trackButton.text = "START TRACKING"
        updateStatus("Tracking stopped. Trail cleared.")
        map.invalidate()
    }


    // 11. Helper function for status updates
    private fun updateStatus(message: String) {
        // This is optional, but helpful for debugging
        runOnUiThread {
            statusText.text = "Status: $message"
        }
    }

//    // 12. Add a simple marker for the current location (Requires an import for Marker)
//    private fun addMarker(point: GeoPoint) {
//        // Clear previous markers to only show the latest location
//        map.overlays.clear()
//
//        val marker = Marker(map)
//        marker.position = point
//        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
//        marker.title = "Current Location"
//        map.overlays.add(marker)
//        map.invalidate() // Redraw the map
//    }

// Replace your existing 'private fun addMarker(point: GeoPoint) { ... }' block with this new function:

    // 12. Add/Update the single marker (does not clear the Polyline)
    private fun addOrUpdateMarker(point: GeoPoint) {
        if (currentLocationMarker == null) {
            // Create and add the marker if it doesn't exist
            currentLocationMarker = Marker(map).apply {
                position = point
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Current Location"
            }
            map.overlays.add(currentLocationMarker)
        } else {
            // Just update the position if the marker already exists
            currentLocationMarker!!.position = point
        }
        // No map.invalidate() here, as it's called at the end of locationCallback
    }





}