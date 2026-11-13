package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.CloudCloneRecord
import com.example.data.cloud.ClonixCloudRepository
import com.example.data.local.ClonedAppEntity
import com.example.data.local.ClonixDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.local.SandboxAuditLogEntity
import com.example.data.local.UserPreferences
import com.example.data.model.AppCategory
import com.example.data.model.DevicePlatform
import com.example.data.model.InstalledAppInfo
import com.example.data.model.LaunchMode
import com.example.data.model.NetworkExitReport
import com.example.data.model.ProvisioningUiState
import com.example.data.model.ProxyConfig
import com.example.data.model.VirtualDeviceProfile
import com.example.data.model.VirtualRegion
import com.example.data.model.VirtualRegionCatalog
import com.example.data.repository.ClonixRepository
import com.example.data.repository.InstalledAppsRepository
import com.example.security.HostDeviceBaseline
import com.example.security.HostIsolationCapabilities
import com.example.security.VaultCryptoManager
import com.example.ui.theme.ThemeMode
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CatalogUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val selectedCategory: AppCategory = AppCategory.ALL,
    val deviceApps: List<InstalledAppInfo> = emptyList(),
    val instantPresets: List<InstalledAppInfo> = emptyList()
) {
    val filteredDeviceApps: List<InstalledAppInfo>
        get() = deviceApps.filter { app ->
            val matchesCat = selectedCategory == AppCategory.ALL || app.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }

    val filteredPresets: List<InstalledAppInfo>
        get() = instantPresets.filter { app ->
            val matchesCat = selectedCategory == AppCategory.ALL || app.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
}

sealed interface CloudSyncState {
    data object LocalOnly : CloudSyncState
    data object Loading : CloudSyncState
    data class Synced(val records: List<CloudCloneRecord>) : CloudSyncState
    data class Error(val message: String) : CloudSyncState
}

class ClonixViewModel(
    appContext: Context,
    val currentUserId: String?,
    userDisplayName: String,
    private val cloudRepository: ClonixCloudRepository?
) : ViewModel() {

    private val database = ClonixDatabase.getInstance(appContext)
    private val cryptoManager = VaultCryptoManager(appContext)
    private val preferencesRepository = PreferencesRepository(appContext)
    private val installedAppsRepository = InstalledAppsRepository(appContext)
    val clonixRepository = ClonixRepository(appContext, database.clonixDao(), cryptoManager, cloudRepository)

    val hostBaseline: HostDeviceBaseline = clonixRepository.getHostBaseline()
    val hostIsolationCapabilities: HostIsolationCapabilities = clonixRepository.getHostIsolationCapabilities()
    val isHardwareKeystoreReady: Boolean = cryptoManager.isHardwareKeystoreReady()

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val clonedApps: StateFlow<List<ClonedAppEntity>> = clonixRepository.clonedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<SandboxAuditLogEntity>> = clonixRepository.auditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cloudSyncState: StateFlow<CloudSyncState> = if (currentUserId != null && cloudRepository != null) {
        cloudRepository.observeCloudClones(currentUserId)
            .map<List<CloudCloneRecord>, CloudSyncState> { CloudSyncState.Synced(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w("ClonixVM", "Cloud sync observation error", root)
                emit(CloudSyncState.Error(root.localizedMessage ?: "Sinkronisasi cloud tertunda"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CloudSyncState.Loading)
    } else {
        flowOf(CloudSyncState.LocalOnly as CloudSyncState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, CloudSyncState.LocalOnly)
    }

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow(AppCategory.ALL)
    private val _deviceApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val _isLoadingApps = MutableStateFlow(true)

    val catalogState: StateFlow<CatalogUiState> = combine(
        _isLoadingApps,
        _searchQuery,
        _selectedCategory,
        _deviceApps
    ) { loading, query, category, apps ->
        CatalogUiState(
            isLoading = loading,
            searchQuery = query,
            selectedCategory = category,
            deviceApps = apps,
            instantPresets = installedAppsRepository.instantSocialPresets
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CatalogUiState())

    private val _provisioningState = MutableStateFlow<ProvisioningUiState>(ProvisioningUiState.Idle)
    val provisioningState: StateFlow<ProvisioningUiState> = _provisioningState.asStateFlow()

    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    private val _vaultSizeBytes = MutableStateFlow(0L)
    val vaultSizeBytes: StateFlow<Long> = _vaultSizeBytes.asStateFlow()

    private val _exitIpReport = MutableStateFlow<NetworkExitReport?>(null)
    val exitIpReport: StateFlow<NetworkExitReport?> = _exitIpReport.asStateFlow()

    init {
        refreshInstalledApps()
        refreshVaultSize()
        viewModelScope.launch {
            val prefs = preferences.value
            if (currentUserId != null && cloudRepository != null) {
                cloudRepository.ensureUserProfile(
                    displayName = userDisplayName,
                    defaultRegionCode = prefs.defaultRegionCode,
                    strictAntiDetect = prefs.strictAntiDetect
                )
            }
            preferences.collect { p ->
                if (!p.isPinConfigured) {
                    _isVaultUnlocked.value = true
                }
            }
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            _deviceApps.value = installedAppsRepository.loadInstalledDeviceApps()
            _isLoadingApps.value = false
        }
    }

    fun refreshVaultSize() {
        _vaultSizeBytes.value = cryptoManager.getTotalVaultBytes()
    }

    fun getAppIconBitmap(packageName: String): Bitmap? =
        installedAppsRepository.getCachedIcon(packageName)

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateCategoryFilter(category: AppCategory) {
        _selectedCategory.value = category
    }

    fun probeNetworkExitIp(proxyConfig: ProxyConfig?, targetRegionCode: String) {
        viewModelScope.launch {
            _exitIpReport.value = NetworkExitReport(isLoading = true)
            _exitIpReport.value = clonixRepository.probeExitIp(proxyConfig, targetRegionCode)
        }
    }

    fun clearExitIpReport() {
        _exitIpReport.value = null
    }

    fun startCloning(
        app: InstalledAppInfo,
        region: VirtualRegion,
        platform: DevicePlatform,
        customLabel: String,
        preferredModelIndex: Int?,
        launchMode: LaunchMode,
        customPortalUrl: String,
        proxyConfig: ProxyConfig,
        preferDesktopWeb: Boolean
    ) {
        viewModelScope.launch {
            clonixRepository.provisionClone(
                app = app,
                region = region,
                platform = platform,
                customLabel = customLabel,
                preferredModelIndex = preferredModelIndex,
                launchMode = launchMode,
                customPortalUrl = customPortalUrl,
                proxyConfig = proxyConfig,
                preferDesktopWeb = preferDesktopWeb
            ).collect { state ->
                _provisioningState.value = state
                if (state is ProvisioningUiState.Success) {
                    refreshVaultSize()
                }
            }
        }
    }

    fun resetProvisioningState() {
        _provisioningState.value = ProvisioningUiState.Idle
    }

    fun regenerateIdentity(
        cloneId: String,
        regionCode: String? = null,
        platform: DevicePlatform? = null,
        onDone: (ClonedAppEntity?) -> Unit = {}
    ) {
        viewModelScope.launch {
            val updated = clonixRepository.regenerateVirtualIdentity(cloneId, regionCode, platform)
            refreshVaultSize()
            onDone(updated)
        }
    }

    fun switchLaunchMode(cloneId: String, mode: LaunchMode, onDone: (ClonedAppEntity?) -> Unit = {}) {
        viewModelScope.launch {
            val updated = clonixRepository.updateCloneLaunchMode(cloneId, mode)
            onDone(updated)
        }
    }

    fun updateCloneProxy(cloneId: String, proxyConfig: ProxyConfig, onDone: (ClonedAppEntity?) -> Unit = {}) {
        viewModelScope.launch {
            val updated = clonixRepository.updateCloneProxy(cloneId, proxyConfig)
            refreshVaultSize()
            onDone(updated)
        }
    }

    fun loadEncryptedProxy(cloneId: String): ProxyConfig = clonixRepository.loadEncryptedProxy(cloneId)

    fun deleteClone(clone: ClonedAppEntity) {
        viewModelScope.launch {
            clonixRepository.deleteClone(clone)
            refreshVaultSize()
        }
    }

    fun wipeAllSandboxes() {
        viewModelScope.launch {
            clonixRepository.wipeAllSandboxes()
            refreshVaultSize()
        }
    }

    suspend fun loadSandboxProfile(clone: ClonedAppEntity): VirtualDeviceProfile {
        clonixRepository.markCloneLaunched(clone.id)
        return clonixRepository.loadDecryptedProfile(clone)
    }

    fun buildAntiDetectJs(
        profile: VirtualDeviceProfile,
        desktopMode: Boolean,
        isWhatsAppIosMode: Boolean = false
    ): String {
        val region = VirtualRegionCatalog.findByCode(profile.regionCode)
        return cryptoManager.buildAntiDetectJsShim(
            profile = profile,
            region = region,
            desktopMode = desktopMode,
            isWhatsAppIosMode = isWhatsAppIosMode,
            strictAntiDetect = preferences.value.strictAntiDetect
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setDefaultRegion(code: String) {
        viewModelScope.launch { preferencesRepository.setDefaultRegion(code) }
    }

    fun setStrictAntiDetect(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setStrictAntiDetect(enabled) }
    }

    fun setAutoLockOnExit(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoLockOnExit(enabled) }
    }

    fun verifyPin(pin: String) {
        val prefs = preferences.value
        val salt = prefs.pinSalt ?: return
        val expectedHash = prefs.pinHash ?: return
        val actualHash = cryptoManager.hashPin(pin, salt)
        if (actualHash == expectedHash) {
            _pinError.value = null
            _isVaultUnlocked.value = true
        } else {
            _pinError.value = "PIN brankas tidak cocok"
        }
    }

    fun configurePin(pin: String) {
        viewModelScope.launch {
            val salt = cryptoManager.generateSalt()
            val hash = cryptoManager.hashPin(pin, salt)
            preferencesRepository.setPinCredential(hash, salt)
            _pinError.value = null
            _isVaultUnlocked.value = true
        }
    }

    fun removePin() {
        viewModelScope.launch {
            preferencesRepository.setPinCredential(null, null)
            _pinError.value = null
            _isVaultUnlocked.value = true
        }
    }

    fun lockVaultNow() {
        if (preferences.value.isPinConfigured) {
            _isVaultUnlocked.value = false
        }
    }
}
