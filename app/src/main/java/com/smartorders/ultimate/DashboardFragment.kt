package com.smartorders.ultimate

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.smartorders.ultimate.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateStats()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSavedStats()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
        JeenyUltimateService.onStatsUpdated = { updateStats() }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun loadSavedStats() {
        val prefs = requireContext().getSharedPreferences("jeeny_prefs", 0)
        JeenyUltimateService.countDetected = prefs.getInt("count_detected", 0)
        JeenyUltimateService.countAccepted = prefs.getInt("count_accepted", 0)
        JeenyUltimateService.countRejected = prefs.getInt("count_rejected", 0)
        updateStats()
    }

    private fun updateStats() {
        _binding?.let {
            it.tvDetected.text = JeenyUltimateService.countDetected.toString()
            it.tvAccepted.text = JeenyUltimateService.countAccepted.toString()
            it.tvRejected.text = JeenyUltimateService.countRejected.toString()

            val total = JeenyUltimateService.countDetected
            val acceptRate = if (total > 0) {
                (JeenyUltimateService.countAccepted * 100) / total
            } else 0

            it.progressAccept.progress = acceptRate
            it.tvAcceptRate.text = "$acceptRate%"

            it.tvServiceStatus.text = if (JeenyUltimateService.isAutoAcceptEnabled)
                "● الخدمة نشطة" else "○ الخدمة متوقفة"
            it.tvServiceStatus.setTextColor(
                resources.getColor(
                    if (JeenyUltimateService.isAutoAcceptEnabled) R.color.gold_primary else R.color.text_dim,
                    null
                )
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
