package com.bijoyism.filenavigator.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bijoyism.filenavigator.data.AppDataRepository
import com.bijoyism.filenavigator.data.AppSettings
import com.bijoyism.filenavigator.data.FavoriteRecord
import com.bijoyism.filenavigator.data.OperationRecord
import com.bijoyism.filenavigator.domain.ConflictPolicy
import com.bijoyism.filenavigator.domain.OrganizationRule
import com.bijoyism.filenavigator.domain.RuleMatcher
import com.bijoyism.filenavigator.storage.BrowserEntry
import com.bijoyism.filenavigator.storage.StorageOperationResult
import com.bijoyism.filenavigator.storage.StorageRepository
import com.bijoyism.filenavigator.worker.WorkerScheduler
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

private data class AppDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val destinations = listOf(
    AppDestination("home", "Home", Icons.Default.Home),
    AppDestination("files", "Files", Icons.Default.Search),
    AppDestination("automation", "Automation", Icons.Default.Star),
    AppDestination("activity", "Activity", Icons.Default.Info),
    AppDestination("settings", "Settings", Icons.Default.Settings),
)

@Composable
fun FileNavigatorApp(
    fileBrowserViewModel: FileBrowserViewModel,
    appData: AppDataRepository,
    settings: AppSettings,
    startDestination: String = "home",
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    LaunchedEffect(startDestination) {
        if (startDestination != "home" || currentDestination?.route != "home") {
            navController.navigate(startDestination) {
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == destination.route } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(
                                    navController.graph.findStartDestination().id,
                                ) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                destination.icon,
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            composable("home") {
                HomeScreen(
                    viewModel = fileBrowserViewModel,
                    appData = appData,
                    onBrowse = { navController.navigate("files") },
                    onAutomation = { navController.navigate("automation") },
                    onActivity = { navController.navigate("activity") },
                )
            }
            composable("files") {
                FileBrowserScreen(fileBrowserViewModel, appData)
            }
            composable("automation") {
                AutomationScreen(appData)
            }
            composable("activity") {
                ActivityScreen(appData)
            }
            composable("settings") {
                SettingsScreen(appData, settings)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    viewModel: FileBrowserViewModel,
    appData: AppDataRepository,
    onBrowse: () -> Unit,
    onAutomation: () -> Unit,
    onActivity: () -> Unit,
) {
    val context = LocalContext.current
    val favorites by appData.favorites.collectAsState(initial = emptyList())
    val rules by appData.rules.collectAsState(initial = emptyList())
    val history by appData.history.collectAsState(initial = emptyList())
    val rootUri by viewModel.rootTreeUri.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "File Navigator Lite",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "A private, lightweight file manager. Your files stay on your device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (rootUri == null) {
                        "Start by choosing a folder"
                    } else {
                        "Your folder is ready"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    if (rootUri == null) {
                        "Pick a folder with Android's folder picker. " +
                            "You choose which location the app can access."
                    } else {
                        "File access is limited to your selected folder " +
                            "and its descendants."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onBrowse,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (rootUri == null) "Choose folder" else "Browse files",
                    )
                }
                OutlinedButton(
                    onClick = {
                        WorkerScheduler.scanNow(context)
                        Toast.makeText(
                            context,
                            "Scan queued. Detection depends on Android background limits.",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Scan now")
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SummaryCard(
                "Rules",
                rules.count { it.enabled }.toString(),
                Modifier.weight(1f),
                onAutomation,
            )
            SummaryCard(
                "Favorites",
                favorites.size.toString(),
                Modifier.weight(1f),
                onBrowse,
            )
            SummaryCard(
                "Operations",
                history.size.toString(),
                Modifier.weight(1f),
                onActivity,
            )
        }

        if (favorites.isNotEmpty()) {
            Text("Favorites", style = MaterialTheme.typography.titleMedium)

            favorites.take(5).forEach { favorite ->
                ListItem(
                    headlineContent = {
                        Text(
                            favorite.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(
                            if (favorite.isDirectory) {
                                "Folder"
                            } else {
                                favorite.mimeType ?: "File"
                            },
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Favorite",
                        )
                    },
                    trailingContent = {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    appData.removeFavorite(favorite.uri)
                                }
                            },
                        ) {
                            Text("Remove")
                        }
                    },
                    modifier = Modifier.clickable {
                        if (!favorite.isDirectory) {
                            openUri(
                                context,
                                Uri.parse(favorite.uri),
                                favorite.mimeType,
                            )
                        } else {
                            runCatching {
                                val folderUri = Uri.parse(favorite.uri)
                                val authority = folderUri.authority
                                    ?: error("The favorite location is invalid.")
                                val treeId =
                                    DocumentsContract.getTreeDocumentId(folderUri)
                                val treeUri =
                                    DocumentsContract.buildTreeDocumentUri(
                                        authority,
                                        treeId,
                                    )
                                val hasGrant =
                                    context.contentResolver
                                        .persistedUriPermissions
                                        .any {
                                            it.uri == treeUri &&
                                                it.isReadPermission
                                        }

                                if (!hasGrant) {
                                    error(
                                        "Access to this favorite has expired. " +
                                            "Choose its folder again in Files.",
                                    )
                                }

                                viewModel.openFavorite(
                                    treeUri,
                                    favorite.uri,
                                    favorite.name,
                                )
                                onBrowse()
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    it.message
                                        ?: "This favorite is no longer accessible.",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                )
            }
        } else {
            Spacer(Modifier.height(2.dp))
            Text(
                "Tip: long-press an item in Files to select it. " +
                    "Use Automation to preview rules before executing them.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val recentAccesses = history
            .filter { it.operation == "OPEN" || it.operation == "SHARE" }
            .take(5)

        if (recentAccesses.isNotEmpty()) {
            Text("Recently accessed", style = MaterialTheme.typography.titleMedium)

            recentAccesses.forEach { record ->
                ListItem(
                    headlineContent = {
                        Text(
                            record.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(
                            "${record.operation.lowercase().replaceFirstChar(Char::uppercase)} " +
                                "· ${formatDate(record.timestampMillis)}",
                        )
                    },
                    leadingContent = {
                        Text(if (record.operation == "SHARE") "↗" else "◷")
                    },
                    modifier = Modifier.clickable {
                        if (record.sourceUri.isNotBlank()) {
                            openUri(context, Uri.parse(record.sourceUri), null)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}