package com.smartorders.ultimate

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.smartorders.ultimate.databinding.FragmentMapBinding
import org.json.JSONArray
import org.json.JSONObject

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!
    private var googleMap: GoogleMap? = null
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences("jeeny_prefs", 0)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.btnClearZones.setOnClickListener { clearAllZones() }
        binding.tvZoneCount.text = "المناطق المحظورة: ${JeenyUltimateService.blacklistedZones.size}"
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isCompassEnabled = true

        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(24.7136, 46.6753), 10f)
        )

        loadSavedZones()
        drawAllZones()

        map.setOnMapLongClickListener { latLng ->
            addBlackZone(latLng.latitude, latLng.longitude, 500f)
        }

        map.setOnMarkerClickListener { marker ->
            val pos = marker.position
            val zone = JeenyUltimateService.blacklistedZones.find {
                it.lat == pos.latitude && it.lng == pos.longitude
            }
            if (zone != null) {
                JeenyUltimateService.blacklistedZones.remove(zone)
                marker.remove()
                saveZones()
                drawAllZones()
                binding.tvZoneCount.text = "المناطق المحظورة: ${JeenyUltimateService.blacklistedZones.size}"
                Toast.makeText(requireContext(), "تم حذف المنطقة", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    private fun addBlackZone(lat: Double, lng: Double, radius: Float) {
        val zone = JeenyUltimateService.BlackZone(lat, lng, radius)
        JeenyUltimateService.blacklistedZones.add(zone)
        saveZones()
        drawZone(zone)
        binding.tvZoneCount.text = "المناطق المحظورة: ${JeenyUltimateService.blacklistedZones.size}"
        Toast.makeText(requireContext(), "تمت إضافة منطقة محظورة", Toast.LENGTH_SHORT).show()
    }

    private fun drawAllZones() {
        googleMap?.clear()
        for (zone in JeenyUltimateService.blacklistedZones) {
            drawZone(zone)
        }
    }

    private fun drawZone(zone: JeenyUltimateService.BlackZone) {
        val map = googleMap ?: return
        val position = LatLng(zone.lat, zone.lng)

        map.addCircle(
            CircleOptions()
                .center(position)
                .radius(zone.radius.toDouble())
                .strokeColor(0xFFFFD700.toInt())
                .fillColor(0x33FF0000)
                .strokeWidth(3f)
        )

        map.addMarker(
            MarkerOptions()
                .position(position)
                .title("منطقة محظورة")
                .snippet("اضغط للحذف")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )
    }

    private fun saveZones() {
        val array = JSONArray()
        for (zone in JeenyUltimateService.blacklistedZones) {
            val obj = JSONObject()
            obj.put("lat", zone.lat)
            obj.put("lng", zone.lng)
            obj.put("radius", zone.radius)
            array.put(obj)
        }
        prefs.edit().putString("black_zones", array.toString()).apply()
    }

    private fun loadSavedZones() {
        JeenyUltimateService.blacklistedZones.clear()
        val saved = prefs.getString("black_zones", "[]") ?: "[]"
        val array = JSONArray(saved)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            JeenyUltimateService.blacklistedZones.add(
                JeenyUltimateService.BlackZone(
                    obj.getDouble("lat"),
                    obj.getDouble("lng"),
                    obj.getDouble("radius").toFloat()
                )
            )
        }
    }

    private fun clearAllZones() {
        JeenyUltimateService.blacklistedZones.clear()
        prefs.edit().remove("black_zones").apply()
        googleMap?.clear()
        binding.tvZoneCount.text = "المناطق المحظورة: 0"
        Toast.makeText(requireContext(), "تم حذف جميع المناطق", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
