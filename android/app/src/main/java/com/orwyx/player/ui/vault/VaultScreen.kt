package com.orwyx.player.ui.vault

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.orwyx.player.ui.library.LibraryEventHandler
import com.orwyx.player.ui.library.LibraryViewModel
import com.orwyx.player.ui.library.SelectionActionsBar
import com.orwyx.player.ui.library.SelectionTopBar
import com.orwyx.player.ui.library.VideoGrid

/**
 * Private folder: PIN-protected (PBKDF2) with optional fingerprint unlock.
 * Videos moved here disappear from every other view and query.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
    vaultViewModel: VaultViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    LibraryEventHandler(viewModel, snackbar)

    val unlocked by vaultViewModel.unlocked.collectAsState()
    val pinConfigured by vaultViewModel.pinConfigured.collectAsState()

    // The library query only includes private rows after unlock.
    LaunchedEffect(unlocked) {
        viewModel.setVaultMode(unlocked)
        viewModel.setFolder(null)
    }

    val selected by viewModel.selectedVideos.collectAsState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (selected.isNotEmpty()) {
                SelectionTopBar(
                    count = selected.size,
                    onClose = viewModel::clearSelection,
                    extraActions = {
                        IconButton(onClick = viewModel::togglePrivateForSelection) {
                            Icon(Icons.Filled.LockOpen, "Remove from private folder")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Private folder") },
                    navigationIcon = {
                        IconButton(onClick = {
                            vaultViewModel.lock()
                            onBack()
                        }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                )
            }
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                SelectionActionsBar(viewModel = viewModel, selectedCount = selected.size, showRename = selected.size == 1)
            }
        },
    ) { padding ->
        when {
            !unlocked -> UnlockPane(
                pinConfigured = pinConfigured,
                vaultViewModel = vaultViewModel,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )
            else -> {
                val videos = viewModel.videos.collectAsLazyPagingItems()
                Column(Modifier.padding(padding)) {
                    VideoGrid(
                        items = videos,
                        viewModel = viewModel,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UnlockPane(
    pinConfigured: Boolean,
    vaultViewModel: VaultViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pin by rememberSaveable { mutableStateOf("") }
    var confirmPin by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(32.dp),
    ) {
        Text(
            if (pinConfigured) "Enter PIN" else "Create a PIN",
            style = MaterialTheme.typography.headlineSmall,
        )
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            label = { Text("PIN") },
            modifier = Modifier.padding(top = 16.dp),
        )
        if (!pinConfigured) {
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirmPin = it },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                label = { Text("Confirm PIN") },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
        Button(
            onClick = {
                if (pinConfigured) {
                    vaultViewModel.tryUnlock(pin) { ok -> if (!ok) error = "Wrong PIN, try again" }
                } else {
                    when {
                        pin.length < 4 -> error = "Use at least 4 digits"
                        pin != confirmPin -> error = "PINs don't match"
                        else -> vaultViewModel.createPin(pin)
                    }
                }
            },
            modifier = Modifier.padding(top = 20.dp),
        ) { Text(if (pinConfigured) "Unlock" else "Create") }

        if (pinConfigured && vaultViewModel.biometricsAvailable(context)) {
            OutlinedButton(
                onClick = {
                    (context as? FragmentActivity)?.let { activity ->
                        showBiometricPrompt(activity) { vaultViewModel.unlockViaBiometric() }
                    }
                },
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null)
                Text("  Unlock with fingerprint")
            }
        }
    }
}

private fun showBiometricPrompt(activity: FragmentActivity, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock private folder")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText("Use PIN")
            .build(),
    )
}
