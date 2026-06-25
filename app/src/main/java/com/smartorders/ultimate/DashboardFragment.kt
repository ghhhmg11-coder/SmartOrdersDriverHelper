package com.smartorders.ultimate

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.smartorders.ultimate.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateUI()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSavedStats()
    }

    override fun onResume() {
        super.onResume()
        JeenyUltimateService.onStatsUpdated = { handler.post { updateUI() } }
        handler.post(refreshRunnable)
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
        updateUI()
    }

    private fun updateUI() {
        val b = _binding ?: return

        // ── Stats ─────────────────────────────────────────────────────────────
        b.tvDetected.text  = JeenyUltimateService.countDetected.toString()
        b.tvAccepted.text  = JeenyUltimateService.countAccepted.toString()
        b.tvRejected.text  = JeenyUltimateService.countRejected.toString()

        val total      = JeenyUltimateService.countDetected
        val acceptRate = if (total > 0) (JeenyUltimateService.countAccepted * 100) / total else 0
        b.progressAccept.progress = acceptRate
        b.tvAcceptRate.text       = "$acceptRate%"

        val enabled = JeenyUltimateService.isAutoAcceptEnabled
        b.tvServiceStatus.text = if (enabled) "● الخدمة نشطة" else "○ الخدمة متوقفة"
        b.tvServiceStatus.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (enabled) R.color.gold_primary else R.color.text_dim
            )
        )

        // ── Debug fields ──────────────────────────────────────────────────────
        b.tvDebugPackage.text = JeenyUltimateService.debugLastPackage

        val found = JeenyUltimateService.debugAcceptFound
        b.tvDebugAcceptFound.text = if (found) "✅ YES" else "❌ NO"
        b.tvDebugAcceptFound.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (found) R.color.green_accept else R.color.red_reject
            )
        )

        val clickRes = JeenyUltimateService.debugClickResult
        b.tvDebugClickResult.text = clickRes
        b.tvDebugClickResult.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                when {
                    clickRes.contains("SUCCESS") || clickRes.contains("SENT") || clickRes.contains("TAP") ->
                        R.color.green_accept
                    clickRes.contains("FAIL") -> R.color.red_reject
                    else -> R.color.text_dim
                }
            )
        )

        b.tvDebugTexts.text = JeenyUltimateService.debugVisibleTexts
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
