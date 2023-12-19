package app.entertainment.googlemaps

import android.Manifest
import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ReportFragment.Companion.reportFragment
import app.entertainment.googlemaps.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private lateinit var locationProviderClient: FusedLocationProviderClient
    private lateinit var currentLocation: Location

    private lateinit var map: GoogleMap

    private var marker: Marker? = null

    private val progressDialog by lazy {
        ProgressDialog(this@MainActivity).also {
            it.setMessage("Searching...")
            it.setCancelable(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        locationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        if (!isPermissionGranted())
            requestPermissions()
        else
            fetchCurrentLocation()
    }

    override fun onResume() {
        super.onResume()

        for (textView in findChildrenByClass(binding.searchView, TextView::class.java)!!) {
            textView.setTextColor(Color.BLACK)
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                progressDialog.show()

                val targetLocationName = binding.searchView.query.toString().trim()
                var addresses: List<Address>? = null
                val geocoder = Geocoder(this@MainActivity)

                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        addresses = async(Dispatchers.IO) { geocoder.getFromLocationName(targetLocationName, 1) }.await()
                    } catch (ex: Exception) {
                        progressDialog.hide()
                        Toast.makeText(this@MainActivity, ex.message, Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    if (addresses == null) {
                        Toast.makeText(this@MainActivity, "Address list is NULL", Toast.LENGTH_SHORT).show()
                        progressDialog.hide()
                        return@launch
                    }

                    if (addresses?.size!! > 0) {
                        val address = addresses!![0]
                        val location = address.let { LatLng(it.latitude, it.longitude) }

                        location.let {
                            marker?.remove()
                            marker = map.addMarker(
                                MarkerOptions()
                                    .position(it)
                                    .title(targetLocationName)
                            )
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 10F))
                        }
                        progressDialog.hide()
                    } else {
                        Toast.makeText(this@MainActivity, "$targetLocationName can't be found", Toast.LENGTH_SHORT).show()
                        progressDialog.hide()
                    }
                }

                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })
    }

    override fun onMapReady(googleMap: GoogleMap) {
        val myLocation = LatLng(currentLocation.latitude, currentLocation.longitude)

        map = googleMap

        map.apply {
            marker = addMarker(
                MarkerOptions()
                    .position(myLocation)
                    .title("My Location")
            )
            moveCamera(CameraUpdateFactory.newLatLng(myLocation))
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation() {
        val locationTask = locationProviderClient.lastLocation
        locationTask.addOnSuccessListener { location ->
            location?.let {
                currentLocation = it

                val mapsFragment =
                    supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
                mapsFragment?.getMapAsync(this)
            } ?: Toast.makeText(this, "Location is null!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isPermissionGranted(): Boolean {
        val fineLocationPermissionResult = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocationPermissionResult = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        return fineLocationPermissionResult == PackageManager.PERMISSION_GRANTED && coarseLocationPermissionResult == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_CODE
        )
    }

    private fun <V : View?> findChildrenByClass(
        viewGroup: ViewGroup,
        clazz: Class<V>
    ): Collection<V>? {
        return gatherChildrenByClass(viewGroup, clazz, ArrayList())
    }

    private fun <V : View?> gatherChildrenByClass(
        viewGroup: ViewGroup,
        clazz: Class<V>,
        childrenFound: MutableCollection<V>
    ): Collection<V>? {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (clazz.isAssignableFrom(child.javaClass)) {
                childrenFound.add(child as V)
            }
            if (child is ViewGroup) {
                gatherChildrenByClass(child, clazz, childrenFound)
            }
        }
        return childrenFound
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE -> if (grantResults.isNotEmpty()) {
                val locationPermissionGranted =
                    grantResults[0] === PackageManager.PERMISSION_GRANTED
                if (locationPermissionGranted) {
                    fetchCurrentLocation()
                } else {
                    Toast.makeText(this, "Location permission is necessary!", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    companion object {
        private const val REQUEST_CODE = 101
    }
}