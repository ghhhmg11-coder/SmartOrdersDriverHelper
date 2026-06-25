package com.smartorders.driverhelper.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartorders.driverhelper.data.repository.AppRepository
import com.smartorders.driverhelper.model.AppRules
import com.smartorders.driverhelper.model.DailyStats
import com.smartorders.driverhelper.model.TripLog
import com.smartorders.driverhelper.model.Zone
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppRepository(application)

    val isLoggedIn: StateFlow<Boolean> = repository.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoAcceptEnabled: StateFlow<Boolean> = repository.autoAcceptEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val rules: StateFlow<AppRules> = repository.rules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppRules())

    val allLogs: StateFlow<List<TripLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val zones: StateFlow<List<Zone>> = repository.zones
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _dailyStats = MutableStateFlow(DailyStats())
    val dailyStats: StateFlow<DailyStats> = _dailyStats.asStateFlow()

    private val _lastTrip = MutableStateFlow<TripLog?>(null)
    val lastTrip: StateFlow<TripLog?> = _lastTrip.asStateFlow()

    init {
        refreshStats()
        viewModelScope.launch {
            allLogs.collect { logs ->
                _lastTrip.value = logs.firstOrNull()
                refreshStats()
            }
        }
    }

    fun login(username: String, password: String): Boolean {
        return if (username == "admin" && password == "1234") {
            viewModelScope.launch { repository.setLoggedIn(true) }
            true
        } else false
    }

    fun logout() = viewModelScope.launch { repository.setLoggedIn(false) }

    fun setAutoAccept(enabled: Boolean) = viewModelScope.launch {
        repository.setAutoAccept(enabled)
    }

    fun saveRules(rules: AppRules) = viewModelScope.launch {
        repository.saveRules(rules)
    }

    fun clearTodayStats() = viewModelScope.launch {
        repository.clearTodayStats()
        refreshStats()
    }

    fun clearAllLogs() = viewModelScope.launch { repository.clearAllLogs() }

    fun addZone(zone: Zone) = viewModelScope.launch { repository.addZone(zone) }
    fun deleteZone(zone: Zone) = viewModelScope.launch { repository.deleteZone(zone) }

    fun resetAllSettings() = viewModelScope.launch { repository.resetAllSettings() }

    private fun refreshStats() = viewModelScope.launch {
        _dailyStats.value = repository.getDailyStats()
    }
}
