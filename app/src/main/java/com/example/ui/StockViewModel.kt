package com.example.ui

import android.content.Context
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.StockEntry
import com.example.data.StockRepository
import com.example.data.SyncStatusState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StockViewModel(private val repository: StockRepository, private val context: Context) : ViewModel() {

    companion object {
        private const val TAG = "StockViewModel"
    }

    // Form states
    var riceType by mutableStateOf("")
    var riceType1 by mutableStateOf("")
    var riceType2 by mutableStateOf("")
    var riceType3 by mutableStateOf("")
    var riceType4 by mutableStateOf("")
    var daraStatus by mutableStateOf("") // Yes / No
    var partyName by mutableStateOf("")
    var status by mutableStateOf("") // In / Out
    var station by mutableStateOf("")
    var weight by mutableStateOf("")
    var bags by mutableStateOf("")
    var location by mutableStateOf("")

    // List of selected temporary uris from camera/gallery
    val selectedPhotoUris = mutableStateListOf<Uri>()

    // Settings config
    var scriptUrlInput by mutableStateOf(repository.getScriptUrl())
    var imgbbKeyInput by mutableStateOf(repository.getImgbbKey())
    var isSettingsExpanded by mutableStateOf(false)

    // Sync status indicators
    private val _syncState = MutableStateFlow<SyncStatusState>(SyncStatusState.Idle)
    val syncState: StateFlow<SyncStatusState> = _syncState.asStateFlow()

    // Database entries list
    val allEntries: StateFlow<List<StockEntry>> = repository.allEntries
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Network status tracker
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
            Log.d(TAG, "Network available. Launching organic auto-sync...")
            triggerAutoSync()
        }

        override fun onLost(network: Network) {
            _isOnline.value = false
            Log.d(TAG, "Network lost.")
        }
    }

    init {
        // Register network listener
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
            // Check initial network state
            val active = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(active)
            _isOnline.value = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) {
            Log.e(TAG, "Failed registering network callback", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callback", e)
        }
    }

    fun saveApiSettings() {
        repository.saveApiSettings(scriptUrlInput, imgbbKeyInput)
        isSettingsExpanded = false
    }

    fun resetApiSettings() {
        scriptUrlInput = StockRepository.DEFAULT_SCRIPT_URL
        imgbbKeyInput = StockRepository.DEFAULT_IMGBB_KEY
        repository.saveApiSettings(StockRepository.DEFAULT_SCRIPT_URL, StockRepository.DEFAULT_IMGBB_KEY)
    }

    fun addPhotoUri(uri: Uri) {
        selectedPhotoUris.add(uri)
    }

    fun removePhotoUri(uri: Uri) {
        selectedPhotoUris.remove(uri)
    }

    /**
     * Validates form inputs and saves a new StockEntry.
     * Returns error message if validation fails, or null if success.
     */
    suspend fun submitForm(): String? = withContext(Dispatchers.IO) {
        if (daraStatus.isEmpty()) return@withContext "Please select Dara Status"

        if (daraStatus == "Yes") {
            if (partyName.trim().isEmpty()) return@withContext "Please fill: Party Name"
            if (station.trim().isEmpty()) return@withContext "Please fill: Station"
        }

        if (status.isEmpty()) return@withContext "Please select Status (In / Out)"
        
        val weightVal = weight.trim().toDoubleOrNull()
        if (weightVal == null || weightVal <= 0) return@withContext "Please enter a valid Weight (K.G)"

        val bagsVal = bags.trim().toIntOrNull()
        if (bagsVal == null || bagsVal <= 0) return@withContext "Please enter a valid Number of Bags"

        if (location.isEmpty()) return@withContext "Please select Location"

        try {
            // Save local physical images in BG thread
            val savedPaths = selectedPhotoUris.map { uri ->
                repository.saveImageToInternalStorage(uri)
            }

            val entry = StockEntry(
                riceType = riceType,
                riceType1 = riceType1,
                riceType2 = riceType2,
                riceType3 = riceType3,
                riceType4 = riceType4,
                daraStatus = daraStatus,
                partyName = if (daraStatus == "Yes") partyName.substring(0, minOf(partyName.length, 100)) else "",
                status = status,
                station = if (daraStatus == "Yes") station.trim() else "",
                weight = weightVal,
                bags = bagsVal,
                location = location,
                localPhotoPaths = savedPaths.joinToString(","),
                syncStatus = "PENDING"
            )

            repository.saveEntry(entry)

            // Clear form inputs
            withContext(Dispatchers.Main) {
                clearFormInputs()
            }

            // Trigger sync immediately if online
            if (_isOnline.value) {
                triggerAutoSync()
            }

            return@withContext null // Success
        } catch (e: Exception) {
            Log.e(TAG, "Error saving entry template", e)
            return@withContext "Error writing entry: ${e.message}"
        }
    }

    private fun clearFormInputs() {
        riceType = ""
        riceType1 = ""
        riceType2 = ""
        riceType3 = ""
        riceType4 = ""
        daraStatus = ""
        partyName = ""
        status = ""
        station = ""
        weight = ""
        bags = ""
        location = ""
        selectedPhotoUris.clear()
    }

    fun triggerAutoSync() {
        viewModelScope.launch {
            repository.syncPendingEntries { state ->
                _syncState.value = state
            }
        }
    }

    fun deleteEntry(entry: StockEntry) {
        viewModelScope.launch {
            repository.deleteEntry(entry)
        }
    }
}

class StockViewModelFactory(private val repository: StockRepository, private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StockViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StockViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
