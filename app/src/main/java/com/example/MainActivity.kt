package com.example

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.cloud.ClonixCloudRepository
import com.example.data.local.ClonedAppEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.UserPreferences
import com.example.data.model.InstalledAppInfo
import com.example.data.model.LaunchMode
import com.example.data.model.ProvisioningUiState
import com.example.security.NativeLaunchResult
import com.example.security.WorkProfileManager
import com.example.ui.ClonixViewModel
import com.example.ui.components.ClonixTab
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassBottomBar
import com.example.ui.components.PinLockGateOverlay
import com.example.ui.screens.AppCatalogScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CloneInspectorSheet
import com.example.ui.screens.ConfigureCloneSheet
import com.example.ui.screens.IsolatedSandboxScreen
import com.example.ui.screens.ProvisioningDialog
import com.example.ui.screens.VaultSettingsScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.screens.onGoogleSignInClicked
import com.example.ui.screens.signOutClonix
import com.example.ui.theme.ClonixTheme
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.theme.ThemeMode
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

private const val ROUTE_SANDBOX = "sandbox/{cloneId}"

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClonixEntryGate()
        }
    }
}

@Composable
fun ClonixEntryGate(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsRepo = remember(context) { PreferencesRepository(context.applicationContext) }
    val prefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(initialValue = UserPreferences())
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null && !prefs.hasSkippedCloudAuth) {
        ClonixTheme(themeMode = prefs.themeMode) {
            AuthScreen(
                onAuthSuccess = {},
                onSkipAuth = {
                    scope.launch {
                        prefsRepo.setHasSkippedCloudAuth(true)
                    }
                }
            )
        }
    } else {
        val sessionKey = user?.uid ?: "local_vault"
        key(sessionKey) {
            val viewModel: ClonixViewModel = viewModel(
                key = sessionKey,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val cloudRepo = if (user != null) ClonixCloudRepository(app.applicationContext) else null
                        ClonixViewModel(
                            appContext = app.applicationContext,
                            currentUserId = user?.uid,
                            userDisplayName = user?.displayName ?: user?.email ?: "Clonix Local",
                            cloudRepository = cloudRepo
                        )
                    }
                }
            )
            ClonixRootApp(
                viewModel = viewModel,
                isSignedIn = user != null,
                userDisplayName = user?.displayName,
                userEmail = user?.email,
                onRequireAuthPrompt = {
                    scope.launch { prefsRepo.setHasSkippedCloudAuth(false) }
                }
            )
        }
    }
}

@Composable
fun ClonixRootApp(
    viewModel: ClonixViewModel,
    isSignedIn: Boolean,
    userDisplayName: String?,
    userEmail: String?,
    onRequireAuthPrompt: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val clonedApps by viewModel.clonedApps.collectAsStateWithLifecycle()
    val catalogState by viewModel.catalogState.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val cloudSyncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()
    val provisioningState by viewModel.provisioningState.collectAsStateWithLifecycle()
    val exitIpReport by viewModel.exitIpReport.collectAsStateWithLifecycle()
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsStateWithLifecycle()
    val pinError by viewModel.pinError.collectAsStateWithLifecycle()
    val vaultSizeBytes by viewModel.vaultSizeBytes.collectAsStateWithLifecycle()

    // Auto-lock: when the user leaves Clonix and the preference is on, the PIN gate
    // returns on the next foreground (real behaviour instead of an inert setting).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, preferences.autoLockOnExit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && preferences.autoLockOnExit) {
                viewModel.lockVaultNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ClonixTheme(themeMode = preferences.themeMode) {
        if (!isVaultUnlocked && preferences.isPinConfigured) {
            PinLockGateOverlay(
                pinError = pinError,
                onVerifyPin = { viewModel.verifyPin(it) }
            )
            return@ClonixTheme
        }

        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route ?: ClonixTab.WORKSPACE.route
        val isInSandbox = currentRoute.startsWith("sandbox/")

        val workProfileManager = remember(context) { WorkProfileManager(context) }
        var pendingNativeCloneId by remember { mutableStateOf<String?>(null) }
        var nativeNotice by remember { mutableStateOf<String?>(null) }

        val provisionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val cloneId = pendingNativeCloneId
            pendingNativeCloneId = null
            val clone = clonedApps.firstOrNull { it.id == cloneId }
            if (result.resultCode == Activity.RESULT_OK && clone != null) {
                nativeNotice = when (
                    val launch = workProfileManager.ensureAndLaunch(clone.packageName, clone.originalAppName)
                ) {
                    NativeLaunchResult.Launched -> null
                    NativeLaunchResult.NeedsProfile -> "Profil kerja belum siap. Coba buka lagi."
                    is NativeLaunchResult.NeedsInstall -> launch.message
                    is NativeLaunchResult.Failed -> launch.message
                }
            } else {
                nativeNotice = "Profil kerja tidak dibuat. Klon tetap tersimpan dan bisa dibuka lagi dari halaman klon."
            }
        }

        var appToConfigure by remember { mutableStateOf<InstalledAppInfo?>(null) }
        var cloneToInspect by remember { mutableStateOf<ClonedAppEntity?>(null) }
        var authConnectError by remember { mutableStateOf<String?>(null) }

        fun navigateToTab(tab: ClonixTab) {
            navController.navigate(tab.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        fun openSandbox(cloneId: String) {
            com.example.runtime.CloneRuntimeActivity.launchCloneTask(context, cloneId)
        }

        // Native clones live in an Android work profile, so opening one may need the user's
        // one-time consent to create that profile, and the app may still have to be installed
        // there. Every outcome is reported instead of silently doing nothing.
        fun openClone(clone: ClonedAppEntity) {
            if (!LaunchMode.fromStored(clone.launchMode).isNative) {
                openSandbox(clone.id)
                return
            }
            viewModel.markCloneOpened(clone.id)
            when (val result = workProfileManager.ensureAndLaunch(clone.packageName, clone.originalAppName)) {
                NativeLaunchResult.Launched -> nativeNotice = null
                NativeLaunchResult.NeedsProfile -> {
                    val intent = workProfileManager.provisioningIntent()
                    if (intent != null) {
                        pendingNativeCloneId = clone.id
                        provisionLauncher.launch(intent)
                    } else {
                        nativeNotice = "Perangkat ini tidak mendukung profil kerja Android."
                    }
                }
                is NativeLaunchResult.NeedsInstall -> nativeNotice = result.message
                is NativeLaunchResult.Failed -> nativeNotice = result.message
            }
        }

        LiquidGlassBackdrop {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 680.dp && !isInSandbox

                Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        if (!isInSandbox && !useNavRail) {
                            LiquidGlassBottomBar(
                                currentRoute = currentRoute,
                                onNavigate = { navigateToTab(it) }
                            )
                        }
                    }
                ) { _ ->
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (useNavRail) {
                            val glass = LocalLiquidGlass.current
                            NavigationRail(
                                containerColor = glass.glassNavBarFill,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Spacer(modifier = Modifier.padding(top = 18.dp))
                                ClonixTab.entries.forEach { tab ->
                                    val selected = currentRoute == tab.route
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { navigateToTab(tab) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = stringResource(tab.labelRes)
                                            )
                                        },
                                        label = { Text(stringResource(tab.labelRes)) }
                                    )
                                }
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = ClonixTab.WORKSPACE.route,
                            modifier = Modifier.weight(1f)
                        ) {
                            composable(ClonixTab.WORKSPACE.route) {
                                WorkspaceScreen(
                                    clonedApps = clonedApps,
                                    cloudSyncState = cloudSyncState,
                                    vaultSizeBytes = vaultSizeBytes,
                                    themeMode = preferences.themeMode,
                                    isPinConfigured = preferences.isPinConfigured,
                                    getIconBitmap = { pkg -> viewModel.getAppIconBitmap(pkg) },
                                    onCycleTheme = {
                                        val next = when (preferences.themeMode) {
                                            ThemeMode.SYSTEM -> ThemeMode.DARK
                                            ThemeMode.DARK -> ThemeMode.LIGHT
                                            ThemeMode.LIGHT -> ThemeMode.SYSTEM
                                        }
                                        viewModel.setThemeMode(next)
                                    },
                                    onLockVault = { viewModel.lockVaultNow() },
                                    onNavigateToCatalog = { navigateToTab(ClonixTab.CATALOG) },
                                    onLaunchClone = { clone -> openClone(clone) },
                                    onInspectClone = { clone -> cloneToInspect = clone }
                                )
                            }

                            composable(ClonixTab.CATALOG.route) {
                                AppCatalogScreen(
                                    catalogState = catalogState,
                                    defaultRegionCode = preferences.defaultRegionCode,
                                    onSearchChange = { viewModel.updateSearchQuery(it) },
                                    onCategorySelect = { viewModel.updateCategoryFilter(it) },
                                    onRefreshApps = { viewModel.refreshInstalledApps() },
                                    onSelectAppToClone = { app ->
                                        viewModel.clearExitIpReport()
                                        appToConfigure = app
                                    }
                                )
                            }

                            composable(ClonixTab.VAULT.route) {
                                VaultSettingsScreen(
                                    preferences = preferences,
                                    hostBaseline = viewModel.hostBaseline,
                                    isHardwareKeystoreReady = viewModel.isHardwareKeystoreReady,
                                    vaultSizeBytes = vaultSizeBytes,
                                    auditLogs = auditLogs,
                                    isSignedIn = isSignedIn,
                                    userDisplayName = userDisplayName,
                                    userEmail = userEmail,
                                    cloudSyncState = cloudSyncState,
                                    authConnectError = authConnectError,
                                    onConnectGoogleAccount = {
                                        authConnectError = null
                                        onGoogleSignInClicked(
                                            context = context,
                                            credentialManager = credentialManager,
                                            scope = scope,
                                            onAuthSuccess = { authConnectError = null },
                                            onAuthError = { msg -> authConnectError = msg }
                                        )
                                    },
                                    onSignOut = {
                                        signOutClonix(credentialManager, scope) {
                                            onRequireAuthPrompt()
                                        }
                                    },
                                    onThemeChange = { viewModel.setThemeMode(it) },
                                    onDefaultRegionChange = { viewModel.setDefaultRegion(it) },
                                    onStrictAntiDetectChange = { viewModel.setStrictAntiDetect(it) },
                                    onAutoLockChange = { viewModel.setAutoLockOnExit(it) },
                                    onConfigurePin = { viewModel.configurePin(it) },
                                    onRemovePin = { viewModel.removePin() },
                                    onWipeAll = { viewModel.wipeAllSandboxes() }
                                )
                            }

                            composable(
                                route = ROUTE_SANDBOX,
                                arguments = listOf(navArgument("cloneId") { type = NavType.StringType })
                            ) { entry ->
                                val cloneId = entry.arguments?.getString("cloneId").orEmpty()
                                val targetClone = clonedApps.firstOrNull { it.id == cloneId }
                                if (targetClone != null) {
                                    IsolatedSandboxScreen(
                                        clone = targetClone,
                                        viewModel = viewModel,
                                        onExitSandbox = { navController.popBackStack() }
                                    )
                                } else {
                                    Text(
                                        text = "Kontainer tidak ditemukan.",
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        appToConfigure?.let { targetApp ->
            ConfigureCloneSheet(
                app = targetApp,
                defaultRegionCode = preferences.defaultRegionCode,
                exitIpReport = exitIpReport,
                existingCloneCount = clonedApps.count { it.packageName == targetApp.packageName },
                onProbeExitIp = { proxyCfg, regionCode ->
                    viewModel.probeNetworkExitIp(proxyCfg, regionCode)
                },
                onDismiss = {
                    viewModel.clearExitIpReport()
                    appToConfigure = null
                },
                onConfirmClone = { region, platform, customLabel, modelIdx, launchMode, portalUrl, proxyConfig, preferDesktopWeb ->
                    viewModel.clearExitIpReport()
                    appToConfigure = null
                    viewModel.startCloning(
                        app = targetApp,
                        region = region,
                        platform = platform,
                        customLabel = customLabel,
                        preferredModelIndex = modelIdx,
                        launchMode = launchMode,
                        customPortalUrl = portalUrl,
                        proxyConfig = proxyConfig,
                        preferDesktopWeb = preferDesktopWeb
                    )
                }
            )
        }

        cloneToInspect?.let { inspecting ->
            val freshClone = clonedApps.firstOrNull { it.id == inspecting.id } ?: inspecting
            CloneInspectorSheet(
                clone = freshClone,
                hostBaseline = viewModel.hostBaseline,
                iconBitmap = viewModel.getAppIconBitmap(freshClone.packageName),
                onDismiss = { cloneToInspect = null },
                onLaunch = {
                    cloneToInspect = null
                    openClone(freshClone)
                },
                onRegenerateIdentity = { newRegionCode, newPlatform ->
                    viewModel.regenerateIdentity(freshClone.id, newRegionCode, newPlatform) { updated ->
                        if (updated != null) cloneToInspect = updated
                    }
                },
                onDeleteClone = {
                    viewModel.deleteClone(freshClone)
                    cloneToInspect = null
                }
            )
        }

        nativeNotice?.let { message ->
            AlertDialog(
                onDismissRequest = { nativeNotice = null },
                title = { Text("Klon Profil Kerja") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { nativeNotice = null }) {
                        Text("Mengerti")
                    }
                }
            )
        }

        if (provisioningState !is ProvisioningUiState.Idle) {
            ProvisioningDialog(
                state = provisioningState,
                onOpenClone = { newCloneId ->
                    viewModel.resetProvisioningState()
                    navigateToTab(ClonixTab.WORKSPACE)
                    openSandbox(newCloneId)
                },
                onDone = {
                    viewModel.resetProvisioningState()
                    navigateToTab(ClonixTab.WORKSPACE)
                }
            )
        }
    }
}
