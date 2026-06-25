package com.smartorders.ultimate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.smartorders.ultimate.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = requireContext().getSharedPreferences("jeeny_prefs", 0)

        binding.seekbarPrice.progress = (JeenyUltimateService.minPriceThreshold * 10).toInt()
        binding.tvPriceValue.text = "%.1f".format(JeenyUltimateService.minPriceThreshold)

        binding.seekbarPrice.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val price = progress / 10.0
                JeenyUltimateService.minPriceThreshold = price
                binding.tvPriceValue.text = "%.1f".format(price)
                prefs.edit().putFloat("min_price", price.toFloat()).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        binding.btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnToggleFloat.setOnClickListener {
            if (Settings.canDrawOverlays(requireContext())) {
                val intent = Intent(requireContext(), FloatingControllerService::class.java)
                requireContext().startService(intent)
                Toast.makeText(requireContext(), "تم تشغيل الزر العائم", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")
                )
                startActivity(intent)
            }
        }

        binding.btnStopFloat.setOnClickListener {
            requireContext().stopService(Intent(requireContext(), FloatingControllerService::class.java))
            Toast.makeText(requireContext(), "تم إيقاف الزر العائم", Toast.LENGTH_SHORT).show()
        }

        binding.btnResetStats.setOnClickListener {
            JeenyUltimateService.countDetected = 0
            JeenyUltimateService.countAccepted = 0
            JeenyUltimateService.countRejected = 0
            prefs.edit()
                .putInt("count_detected", 0)
                .putInt("count_accepted", 0)
                .putInt("count_rejected", 0)
                .apply()
            Toast.makeText(requireContext(), "تم إعادة تعيين الإحصائيات", Toast.LENGTH_SHORT).show()
        }

        refreshServiceStatus()
    }

    fun refreshServiceStatus() {
        _binding?.let {
            val enabled = JeenyUltimateService.isAutoAcceptEnabled
            it.tvServiceState.text = if (enabled) "نشطة ✓" else "متوقفة ✗"
            it.tvServiceState.setTextColor(
                resources.getColor(
                    if (enabled) R.color.gold_primary else R.color.text_dim, null
                )
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
