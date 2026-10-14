package com.bijoyism.filenavigator.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import java.text.DateFormat
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.launch
import java.util.Date

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
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    LaunchedEffect(startDestination) {
        if (startDestination != "home" || currentDestination?.route != "home") {
            navController.navigate(startDestination) { launchSingleTop = true }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
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
            composable("files") { FileBrowserScreen(fileBrowserViewModel, appData) }
            composable("automation") { AutomationScreen(appData) }
            composable("activity") { ActivityScreen(appData) }
            composable("settings") { SettingsScreen(appData, settings) }
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("File Navigator Lite", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "A private, lightweight file manager. Your files stay on your device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (rootUri == null) "Start by choosing a folder" else "Your folder is ready", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (rootUri == null) "Pick a folder with Android's folder picker. You choose which location the app can access."
                    else "File access is limited to your selected folder and its descendants.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onBrowse, modifier = Modifier.fillMaxWidth()) { Text(if (rootUri == null) "Choose folder" else "Browse files") }
                OutlinedButton(onClick = {
                    WorkerScheduler.scanNow(context)
                    Toast.makeText(context, "Scan queued. Detection depends on Android background limits.", Toast.LENGTH_LONG).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("Scan now") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryCard("Rules", rules.count { it.enabled }.toString(), Modifier.weight(1f), onAutomation)
            SummaryCard("Favorites", favorites.size.toString(), Modifier.weight(1f), onBrowse)
            SummaryCard("Operations", history.size.toString(), Modifier.weight(1f), onActivity)
        }
        if (favorites.isNotEmpty()) {
            Text("Favorites", style = MaterialTheme.typography.titleMedium)
            favorites.take(5).forEach { favorite ->
                ListItem(
                    headlineContent = { Text(favorite.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(if (favorite.isDirectory) "Folder" else favorite.mimeType ?: "File") },
                    leadingContent = { Icon(Icons.Default.Star, contentDescription = "Favorite") },
                    trailingContent = { TextButton(onClick = { scope.launch { appData.removeFavorite(favorite.uri) } }) { Text("Remove") } },
                    modifier = Modifier.clickable {
                        if (!favorite.isDirectory) {
                            openUri(context, Uri.parse(favorite.uri), favorite.mimeType)
                        } else {
                            runCatching {
                                val folderUri = Uri.parse(favorite.uri)
                                val authority = folderUri.authority ?: error("The favorite location is invalid.")
                                val treeId = DocumentsContract.getTreeDocumentId(folderUri)
                                val treeUri = DocumentsContract.buildTreeDocumentUri(authority, treeId)
                                val hasGrant = context.contentResolver.persistedUriPermissions.any {
                                    it.uri == treeUri && it.isReadPermission
                                }
                                if (!hasGrant) error("Access to this favorite has expired. Choose its folder again in Files.")
                                viewModel.openFavorite(treeUri, favorite.uri, favorite.name)
                                onBrowse()
                            }.onFailure {
                                Toast.makeText(context, it.message ?: "This favorite is no longer accessible.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                )
            }
        } else {
            Spacer(Modifier.height(2.dp))
            Text("Tip: long-press an item in Files to select it. Use Automation to preview rules before executing them.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val recentAccesses = history.filter { it.operation == "OPEN" || it.operation == "SHARE" }.take(5)
        if (recentAccesses.isNotEmpty()) {
            Text("Recently accessed", style = MaterialTheme.typography.titleMedium)
            recentAccesses.forEach { record ->
                ListItem(
                    headlineContent = { Text(record.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${record.operation.lowercase().replaceFirstChar(Char::uppercase)} · ${formatDate(record.timestampMillis)}") },
                    leadingContent = { Text(if (record.operation == "SHARE") "↗" else "◷") },
                    modifier = Modifier.clickable { if (record.sourceUri.isNotBlank()) openUri(context, Uri.parse(record.sourceUri), null) },
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private enum class SortMode(val label: String) {
    NAME("Name"), SIZE("Size"), DATE("Date modified"), TYPE("Type")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun FileBrowserScreen(viewModel: FileBrowserViewModel, appData: AppDataRepository) {
    val context = LocalContext.current
    val browserState by viewModel.browserState.collectAsState()
    val rootTreeUri by viewModel.rootTreeUri.collectAsState()
    val favorites by appData.favorites.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val storage = remember(context) { StorageRepository(context) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var detailsEntry by remember { mutableStateOf<BrowserEntry?>(null) }
    var renameEntry by remember { mutableStateOf<BrowserEntry?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var createFolderDialog by remember { mutableStateOf(false) }
    var folderName by rememberSaveable { mutableStateOf("") }
    var deleteConfirm by remember { mutableStateOf(false) }
    var destinationAction by remember { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(SortMode.NAME) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var gridView by rememberSaveable { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val selectedUris = remember { mutableStateListOf<String>() }
    var selectionTree by remember { mutableStateOf("") }

    val rootPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                persistTreeGrant(context, uri)
                viewModel.saveSelectedFolder(uri)
                selectedUris.clear()
                searchQuery = ""
            } catch (error: Exception) {
                Toast.makeText(context, error.message ?: "Folder permission could not be saved.", Toast.LENGTH_LONG).show()
            }
        }
    }
    val destinationPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && rootTreeUri != null && destinationAction.isNotBlank()) {
            val action = destinationAction
            destinationAction = ""
            scope.launch {
                working = true
                try {
                    persistTreeGrant(context, uri)
                    val sourceTree = Uri.parse(rootTreeUri!!)
                    val destinationTree = uri
                    val destinationRoot = storage.rootDocumentUri(destinationTree)
                    var successes = 0
                    var failures = 0
                    val chosen = (browserState as? BrowserUiState.Content)?.entries.orEmpty().filter { it.uri in selectedUris }
                    if (action == "ZIP" || action == "EXTRACT") {
                        runCatching {
                            if (action == "ZIP") storage.createZip(chosen, destinationTree, destinationRoot, "File Navigator Archive")
                            else storage.extractZip(chosen.single(), destinationTree, destinationRoot)
                        }.onSuccess { result ->
                            appData.addHistory(result.toHistoryRecord())
                            if (result.success) successes++ else failures++
                        }.onFailure { error ->
                            failures++
                            appData.addHistory(OperationRecord(operation = action, name = chosen.firstOrNull()?.name ?: "Archive", destinationParentUri = destinationRoot.toString(), success = false, message = error.message ?: "Archive operation failed."))
                        }
                    } else {
                        for (entry in chosen) {
                            runCatching {
                                val result = if (action == "MOVE") {
                                    storage.move(sourceTree, entry, destinationTree, destinationRoot, ConflictPolicy.UNIQUE_NAME)
                                } else {
                                    storage.copy(sourceTree, entry, destinationTree, destinationRoot, ConflictPolicy.UNIQUE_NAME)
                                }
                                appData.addHistory(result.toHistoryRecord())
                                if (result.success) successes++ else failures++
                            }.onFailure { error ->
                                failures++
                                appData.addHistory(OperationRecord(
                                    operation = action,
                                    name = entry.name,
                                    sourceUri = entry.uri,
                                    sourceParentUri = entry.parentUri,
                                    destinationParentUri = destinationRoot.toString(),
                                    success = false,
                                    message = error.message ?: "Operation failed.",
                                ))
                            }
                        }
                    }
                    Toast.makeText(context, "$successes succeeded, $failures failed or skipped.", Toast.LENGTH_LONG).show()
                    selectedUris.clear()
                    viewModel.refresh()
                } catch (error: Exception) {
                    Toast.makeText(context, error.message ?: "Operation failed.", Toast.LENGTH_LONG).show()
                } finally {
                    working = false
                }
            }
        }
    }

    val content = browserState as? BrowserUiState.Content
    val filteredEntries by remember(browserState, searchQuery, sortMode) {
        derivedStateOf {
            content?.entries.orEmpty().filter { it.name.contains(searchQuery, ignoreCase = true) }.let { entries ->
                when (sortMode) {
                    SortMode.NAME -> entries.sortedWith(compareBy<BrowserEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
                    SortMode.SIZE -> entries.sortedWith(compareBy<BrowserEntry> { !it.isDirectory }.thenByDescending { it.sizeBytes })
                    SortMode.DATE -> entries.sortedWith(compareBy<BrowserEntry> { !it.isDirectory }.thenByDescending { it.lastModifiedMillis })
                    SortMode.TYPE -> entries.sortedWith(compareBy<BrowserEntry> { !it.isDirectory }.thenBy { it.mimeType.orEmpty() }.thenBy { it.name.lowercase() })
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Files") },
            navigationIcon = {
                if (content != null && !content.isAtRoot) IconButton(onClick = { viewModel.navigateUp(); selectedUris.clear() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Go up")
                }
            },
            actions = {
                IconButton(onClick = { createFolderDialog = true }) { Text("+", style = MaterialTheme.typography.titleLarge) }
                IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
                IconButton(onClick = { rootPicker.launch(null) }) { Icon(Icons.Default.Home, contentDescription = "Choose folder") }
            },
        )
        if (content != null) {
            Text(
                content.trail.joinToString("  ›  "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (rootTreeUri != null) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                placeholder = { Text("Search this folder") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    OutlinedButton(onClick = { sortMenuExpanded = true }) { Text("Sort: ${sortMode.label}") }
                    DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                        SortMode.entries.forEach { mode -> DropdownMenuItem(text = { Text(mode.label) }, onClick = { sortMode = mode; sortMenuExpanded = false }) }
                    }
                }
                OutlinedButton(onClick = { gridView = !gridView }) { Text(if (gridView) "List view" else "Grid view") }
                if (selectedUris.isNotEmpty()) Text("${selectedUris.size} selected", style = MaterialTheme.typography.labelMedium)
            }
            if (selectedUris.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TextButton(onClick = { destinationAction = "COPY"; destinationPicker.launch(null) }) { Text("Copy to…") }
                    TextButton(onClick = { destinationAction = "MOVE"; destinationPicker.launch(null) }) { Text("Move to…") }
                    if (content?.entries.orEmpty().filter { it.uri in selectedUris }.all { !it.isDirectory }) {
                        TextButton(onClick = { destinationAction = "ZIP"; destinationPicker.launch(null) }) { Text("ZIP") }
                    }
                    val selectedItemsForArchive = content?.entries.orEmpty().filter { it.uri in selectedUris }
                    if (selectedItemsForArchive.size == 1 && selectedItemsForArchive.first().name.endsWith(".zip", true)) {
                        TextButton(onClick = { destinationAction = "EXTRACT"; destinationPicker.launch(null) }) { Text("Extract") }
                    }
                    TextButton(onClick = { deleteConfirm = true }) { Text("Delete") }
                    TextButton(onClick = { selectedUris.clear() }) { Text("Cancel") }
                }
            }
        }

        when (browserState) {
            BrowserUiState.Loading -> LoadingContent()
            BrowserUiState.NoFolderSelected -> EmptyFolderSelection { rootPicker.launch(null) }
            is BrowserUiState.Error -> ErrorContent((browserState as BrowserUiState.Error).message, { rootPicker.launch(null) }, viewModel::refresh)
            is BrowserUiState.Content -> {
                if (filteredEntries.isEmpty()) {
                    EmptyContent(searchQuery.isNotBlank())
                } else if (gridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 132.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        gridItems(filteredEntries, key = { it.uri }) { entry ->
                            FileGridCard(
                                entry = entry,
                                selected = entry.uri in selectedUris,
                                favorite = favorites.any { it.uri == entry.uri },
                                onClick = { handleEntryClick(entry, selectedUris, content, viewModel, context, appData, scope) },
                                onLongClick = { toggleSelection(entry, selectedUris, content) },
                                onDetails = { detailsEntry = entry },
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
                        item { Text("${filteredEntries.size} items", modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(filteredEntries, key = { it.uri }) { entry ->
                            FileEntryRow(
                                entry = entry,
                                selected = entry.uri in selectedUris,
                                favorite = favorites.any { it.uri == entry.uri },
                                onClick = { handleEntryClick(entry, selectedUris, content, viewModel, context, appData, scope) },
                                onLongClick = { toggleSelection(entry, selectedUris, content) },
                                onDetails = { detailsEntry = entry },
                                onFavorite = {
                                    scope.launch {
                                        val existing = favorites.any { it.uri == entry.uri }
                                        if (existing) appData.removeFavorite(entry.uri) else appData.saveFavorite(
                                            FavoriteRecord(entry.uri, entry.name, entry.mimeType, entry.isDirectory),
                                        )
                                    }
                                },
                                onShare = {
                                    val launched = shareUri(context, Uri.parse(entry.uri), entry.mimeType)
                                    scope.launch {
                                        appData.addHistory(
                                            OperationRecord(
                                                operation = "SHARE",
                                                name = entry.name,
                                                sourceUri = entry.uri,
                                                success = launched,
                                                message = if (launched) "Share sheet launched." else "Share sheet couldn't be launched.",
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        if (working) Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)).padding(12.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Working on files…")
            }
        }
    }

    if (createFolderDialog) {
        TextInputDialog(
            title = "Create folder",
            label = "Folder name",
            value = folderName,
            onValueChange = { folderName = it },
            confirmLabel = "Create",
            onDismiss = { createFolderDialog = false; folderName = "" },
            onConfirm = {
                val selectedTree = rootTreeUri?.let(Uri::parse)
                val current = content?.currentUri?.let(Uri::parse)
                if (selectedTree != null && current != null) scope.launch {
                    runCatching { storage.createFolder(selectedTree, current, folderName) }
                        .onSuccess { result -> appData.addHistory(result.toHistoryRecord()); Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show(); viewModel.refresh() }
                        .onFailure { Toast.makeText(context, it.message ?: "Folder creation failed.", Toast.LENGTH_LONG).show() }
                    createFolderDialog = false
                    folderName = ""
                }
            },
        )
    }

    renameEntry?.let { entry ->
        TextInputDialog(
            title = "Rename item",
            label = "New name",
            value = renameText,
            onValueChange = { renameText = it },
            confirmLabel = "Rename",
            onDismiss = { renameEntry = null },
            onConfirm = {
                val tree = rootTreeUri?.let(Uri::parse)
                if (tree != null) scope.launch {
                    runCatching { storage.rename(tree, entry, renameText) }
                        .onSuccess { result -> appData.addHistory(result.toHistoryRecord()); Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show(); viewModel.refresh() }
                        .onFailure { Toast.makeText(context, it.message ?: "Rename failed.", Toast.LENGTH_LONG).show() }
                    renameEntry = null
                }
            },
        )
    }

    detailsEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { detailsEntry = null },
            title = { Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(if (entry.isDirectory) "Folder" else "File")
                    if (!entry.isDirectory) Text("Size: ${formatBytes(entry.sizeBytes)}")
                    Text("Type: ${entry.mimeType ?: if (entry.isDirectory) "Folder" else "Unknown"}")
                    Text("Modified: ${formatDate(entry.lastModifiedMillis)}")
                    Text("Location: ${Uri.parse(entry.parentUri).lastPathSegment ?: "Selected folder"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { detailsEntry = null }) { Text("Close") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { renameText = entry.name; renameEntry = entry; detailsEntry = null }) { Text("Rename") }
                    TextButton(onClick = {
                        scope.launch { appData.saveFavorite(FavoriteRecord(entry.uri, entry.name, entry.mimeType, entry.isDirectory)) }
                        detailsEntry = null
                        Toast.makeText(context, "Added to favorites", Toast.LENGTH_SHORT).show()
                    }) { Text("Favorite") }
                }
            },
        )
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("Delete ${selectedUris.size} item(s)?") },
            text = { Text("This uses the selected storage provider's delete operation. Deleted items might not be recoverable.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteConfirm = false
                    val tree = rootTreeUri?.let(Uri::parse)
                    val chosen = content?.entries.orEmpty().filter { it.uri in selectedUris }
                    if (tree != null) scope.launch {
                        working = true
                        var failures = 0
                        chosen.forEach { entry ->
                            runCatching { storage.delete(entry) }
                                .onSuccess { appData.addHistory(it.toHistoryRecord()) }
                                .onFailure { failures++; appData.addHistory(OperationRecord(operation = "DELETE", name = entry.name, sourceUri = entry.uri, sourceParentUri = entry.parentUri, success = false, message = it.message ?: "Delete failed.")) }
                        }
                        selectedUris.clear()
                        viewModel.refresh()
                        working = false
                        Toast.makeText(context, "Delete finished; $failures failed.", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Cancel") } },
        )
    }
}

private fun handleEntryClick(
    entry: BrowserEntry,
    selectedUris: MutableList<String>,
    content: BrowserUiState.Content?,
    viewModel: FileBrowserViewModel,
    context: Context,
    appData: AppDataRepository,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (selectedUris.isNotEmpty()) {
        toggleSelection(entry, selectedUris, content)
    } else if (entry.isDirectory) {
        viewModel.openFolder(entry.uri, entry.name)
    } else {
        val launched = openUri(context, Uri.parse(entry.uri), entry.mimeType)
        scope.launch {
            appData.addHistory(
                OperationRecord(
                    operation = "OPEN",
                    name = entry.name,
                    sourceUri = entry.uri,
                    success = launched,
                    message = if (launched) "Open chooser launched." else "Open chooser couldn't be launched.",
                ),
            )
        }
    }
}

private fun toggleSelection(entry: BrowserEntry, selectedUris: MutableList<String>, content: BrowserUiState.Content?) {
    if (entry.uri in selectedUris) selectedUris.remove(entry.uri) else selectedUris.add(entry.uri)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileEntryRow(
    entry: BrowserEntry,
    selected: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDetails: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                if (entry.isDirectory) "Folder · ${formatDate(entry.lastModifiedMillis)}"
                else "${formatBytes(entry.sizeBytes)} · ${formatDate(entry.lastModifiedMillis)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            if (selected) Checkbox(checked = true, onCheckedChange = { onClick() }) else {
                Text(fileGlyph(entry), style = MaterialTheme.typography.titleLarge)
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDetails) { Icon(Icons.Default.Info, contentDescription = "Details") }
                if (favorite) IconButton(onClick = onFavorite) { Icon(Icons.Default.Star, contentDescription = "Remove favorite") }
                if (!entry.isDirectory) TextButton(onClick = onShare) { Text("Share") }
            }
        },
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
    )
    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridCard(
    entry: BrowserEntry,
    selected: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDetails: () -> Unit,
) {
    Card(
        modifier = Modifier.aspectRatio(0.92f).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(fileGlyph(entry), style = MaterialTheme.typography.headlineMedium)
                if (selected) Checkbox(checked = true, onCheckedChange = { onClick() })
                else IconButton(onClick = onDetails) { Icon(Icons.Default.Info, contentDescription = "Details") }
            }
            Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (entry.isDirectory) "Folder" else formatBytes(entry.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (favorite) Text("★ Favorite", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EmptyFolderSelection(onChooseFolder: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(16.dp))
        Text("No folder selected", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Choose a folder using Android's folder picker. The app can access only the location you grant.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onChooseFolder) { Text("Choose folder") }
    }
}

@Composable
private fun EmptyContent(searching: Boolean) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(if (searching) "No matching files" else "This folder is empty", style = MaterialTheme.typography.titleMedium)
        Text("Try another search or open a different folder.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoadingContent() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Reading folder…")
    }
}

@Composable
private fun ErrorContent(message: String, onChooseFolder: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Can't open this folder", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRetry) { Text("Retry") }
            Button(onClick = onChooseFolder) { Text("Choose again") }
        }
    }
}

@Composable
private fun AutomationScreen(appData: AppDataRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage = remember(context) { StorageRepository(context) }
    val rules by appData.rules.collectAsState(initial = emptyList())
    var editorRule by remember { mutableStateOf<OrganizationRule?>(null) }
    var folderPickSide by remember { mutableStateOf("") }
    var previewRule by remember { mutableStateOf<OrganizationRule?>(null) }
    var previewEntries by remember { mutableStateOf<List<BrowserEntry>>(emptyList()) }
    var previewError by remember { mutableStateOf("") }
    var previewLoading by remember { mutableStateOf(false) }
    var confirmDeleteRule by remember { mutableStateOf<OrganizationRule?>(null) }
    var busy by remember { mutableStateOf(false) }

    val sourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { persistTreeGrant(context, uri) }.onFailure { Toast.makeText(context, it.message ?: "Could not save folder permission.", Toast.LENGTH_LONG).show() }
            editorRule = editorRule?.copy(sourceTreeUri = uri.toString())
        }
    }
    val destinationPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { persistTreeGrant(context, uri) }.onFailure { Toast.makeText(context, it.message ?: "Could not save folder permission.", Toast.LENGTH_LONG).show() }
            editorRule = editorRule?.copy(destinationTreeUri = uri.toString())
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Automation", style = MaterialTheme.typography.headlineSmall)
                Text("Rules match filenames and file metadata inside folders you choose.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { editorRule = OrganizationRule(name = "") }) { Text("New rule") }
        }
        Spacer(Modifier.height(12.dp))
        if (rules.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("No rules yet", style = MaterialTheme.typography.titleLarge)
                    Text("Create a rule, pick a source and destination, then preview the affected files before executing it.")
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rules, key = { it.id }) { rule ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(rule.name, style = MaterialTheme.typography.titleMedium)
                                    Text(if (rule.enabled) "Enabled · ${if (rule.moveFiles) "Move" else "Copy"}" else "Disabled", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = rule.enabled, onCheckedChange = { enabled -> scope.launch { appData.saveRule(rule.copy(enabled = enabled)) } })
                            }
                            Text("Match: ${rule.extensions.joinToString().ifBlank { "any extension" }}${if (rule.filenameKeywords.isNotEmpty()) " · keywords: ${rule.filenameKeywords.joinToString()}" else ""}", style = MaterialTheme.typography.bodySmall)
                            Text("Source: ${folderLabel(rule.sourceTreeUri)}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Destination: ${folderLabel(rule.destinationTreeUri)}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Last run: ${rule.lastRunSummary}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = {
                                    previewLoading = true
                                    previewError = ""
                                    previewEntries = emptyList()
                                    previewRule = rule
                                    scope.launch {
                                        runCatching {
                                            val sourceTree = Uri.parse(rule.sourceTreeUri)
                                            val destinationTree = Uri.parse(rule.destinationTreeUri)
                                            val sourceRoot = storage.rootDocumentUri(sourceTree)
                                            val destinationRoot = storage.rootDocumentUri(destinationTree)
                                            // Verify both grants during preview, not only after the user presses Run.
                                            storage.listDirectory(destinationTree, destinationRoot)
                                            if (sourceRoot.authority == destinationRoot.authority &&
                                                DocumentsContract.getDocumentId(sourceRoot) == DocumentsContract.getDocumentId(destinationRoot)
                                            ) {
                                                error("Source and destination refer to the same folder. Edit the rule first.")
                                            }
                                            storage.listDirectory(sourceTree, sourceRoot)
                                                .filter { !it.isDirectory && RuleMatcher.matches(it.name, it.mimeType, it.sizeBytes, rule) }
                                        }.onSuccess { previewEntries = it }
                                            .onFailure { previewError = it.message ?: "Preview failed. Check folder permissions." }
                                        previewLoading = false
                                    }
                                }) { Text("Preview / Run") }
                                TextButton(onClick = { editorRule = rule }) { Text("Edit") }
                                TextButton(onClick = { confirmDeleteRule = rule }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }

    editorRule?.let { rule ->
        RuleEditorDialog(
            rule = rule,
            onRuleChange = { editorRule = it },
            onChooseSource = { folderPickSide = "source"; sourcePicker.launch(null) },
            onChooseDestination = { folderPickSide = "destination"; destinationPicker.launch(null) },
            onDismiss = { editorRule = null },
            onSave = { updated ->
                val error = updated.isValid()
                if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show() else scope.launch {
                    appData.saveRule(updated)
                    editorRule = null
                    Toast.makeText(context, "Rule saved.", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    previewRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { previewRule = null },
            title = { Text("Preview: ${rule.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        previewLoading -> CircularProgressIndicator()
                        previewError.isNotBlank() -> Text(previewError, color = MaterialTheme.colorScheme.error)
                        previewEntries.isEmpty() -> Text("No files in this source folder match the rule.")
                        else -> {
                            Text("${previewEntries.size} matching files found. ${if (rule.moveFiles) "They will be moved" else "They will be copied"} to the selected destination.")
                            LazyColumn(modifier = Modifier.height(190.dp)) {
                                items(previewEntries.take(100), key = { it.uri }) { Text("• ${it.name}", modifier = Modifier.padding(vertical = 2.dp)) }
                            }
                            if (previewEntries.size > 100) Text("Showing the first 100 matches.", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !previewLoading && previewError.isBlank() && previewEntries.isNotEmpty() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            var success = 0
                            var failed = 0
                            try {
                                val sourceTree = Uri.parse(rule.sourceTreeUri)
                                val destinationTree = Uri.parse(rule.destinationTreeUri)
                                val destinationRoot = storage.rootDocumentUri(destinationTree)
                                previewEntries.forEach { entry ->
                                    runCatching {
                                        if (rule.moveFiles) storage.move(sourceTree, entry, destinationTree, destinationRoot, rule.conflictPolicy)
                                        else storage.copy(sourceTree, entry, destinationTree, destinationRoot, rule.conflictPolicy)
                                    }.onSuccess {
                                        appData.addHistory(it.toHistoryRecord())
                                        if (it.success) success++ else failed++
                                    }.onFailure { error ->
                                        failed++
                                        appData.addHistory(OperationRecord(operation = if (rule.moveFiles) "MOVE" else "COPY", name = entry.name, sourceUri = entry.uri, sourceParentUri = entry.parentUri, destinationParentUri = destinationRoot.toString(), success = false, message = error.message ?: "Operation failed."))
                                    }
                                }
                                val summary = "$success succeeded, $failed failed/skipped"
                                appData.updateRuleResult(rule.id, summary)
                                Toast.makeText(context, summary, Toast.LENGTH_LONG).show()
                                previewRule = null
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text(if (busy) "Running…" else "Execute rule") }
            },
            dismissButton = { TextButton(onClick = { previewRule = null }) { Text("Close") } },
        )
    }

    confirmDeleteRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { confirmDeleteRule = null },
            title = { Text("Delete rule?") },
            text = { Text("The rule '${rule.name}' will be removed. Files will not be deleted.") },
            confirmButton = { TextButton(onClick = { scope.launch { appData.deleteRule(rule.id); confirmDeleteRule = null } }) { Text("Delete rule") } },
            dismissButton = { TextButton(onClick = { confirmDeleteRule = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RuleEditorDialog(
    rule: OrganizationRule,
    onRuleChange: (OrganizationRule) -> Unit,
    onChooseSource: () -> Unit,
    onChooseDestination: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (OrganizationRule) -> Unit,
) {
    var extensions by remember(rule.id) { mutableStateOf(rule.extensions.joinToString(", ")) }
    var keywords by remember(rule.id) { mutableStateOf(rule.filenameKeywords.joinToString(", ")) }
    var mimeTypes by remember(rule.id) { mutableStateOf(rule.mimeTypes.joinToString(", ")) }
    var minSize by remember(rule.id) { mutableStateOf(rule.minBytes?.let { (it / (1024 * 1024)).toString() }.orEmpty()) }
    var maxSize by remember(rule.id) { mutableStateOf(rule.maxBytes?.let { (it / (1024 * 1024)).toString() }.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule.sourceTreeUri.isBlank()) "Create organization rule" else "Edit organization rule") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(480.dp)) {
                item { OutlinedTextField(value = rule.name, onValueChange = { onRuleChange(rule.copy(name = it)) }, label = { Text("Rule name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedButton(onClick = onChooseSource, modifier = Modifier.fillMaxWidth()) { Text("Source: ${folderLabel(rule.sourceTreeUri)}") } }
                item { OutlinedButton(onClick = onChooseDestination, modifier = Modifier.fillMaxWidth()) { Text("Destination: ${folderLabel(rule.destinationTreeUri)}") } }
                item { OutlinedTextField(value = extensions, onValueChange = { extensions = it }, label = { Text("Extensions (e.g. pdf, zip, apk)") }, supportingText = { Text("Comma-separated; leave empty to ignore extension") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = mimeTypes, onValueChange = { mimeTypes = it }, label = { Text("MIME types (optional)") }, supportingText = { Text("Example: application/pdf or image/*") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = keywords, onValueChange = { keywords = it }, label = { Text("Filename keywords (optional)") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = minSize, onValueChange = { minSize = it.filter(Char::isDigit) }, label = { Text("Minimum size in MiB (optional)") }, keyboardOptions = KeyboardOptions.Default.copy(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = maxSize, onValueChange = { maxSize = it.filter(Char::isDigit) }, label = { Text("Maximum size in MiB (optional)") }, keyboardOptions = KeyboardOptions.Default.copy(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Move matched files", style = MaterialTheme.typography.bodyMedium)
                            Text("Turn off to copy instead.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = rule.moveFiles, onCheckedChange = { onRuleChange(rule.copy(moveFiles = it)) })
                    }
                }
                item {
                    Text("Conflict handling", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val unique = rule.conflictPolicy == ConflictPolicy.UNIQUE_NAME
                        OutlinedButton(onClick = { onRuleChange(rule.copy(conflictPolicy = ConflictPolicy.UNIQUE_NAME)) }) { Text(if (unique) "✓ Unique name" else "Unique name") }
                        OutlinedButton(onClick = { onRuleChange(rule.copy(conflictPolicy = ConflictPolicy.SKIP)) }) { Text(if (!unique) "✓ Skip" else "Skip") }
                    }
                    Text("Existing files are never silently overwritten. Safe replacement depends on the storage provider and is not enabled here.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val normalized = rule.copy(
                    extensions = extensions.split(',').map { it.trim().removePrefix(".") }.filter { it.isNotBlank() }.toSet(),
                    filenameKeywords = keywords.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                    mimeTypes = mimeTypes.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                    minBytes = minSize.toLongOrNull()?.times(1024L * 1024L),
                    maxBytes = maxSize.toLongOrNull()?.times(1024L * 1024L),
                )
                onSave(normalized)
            }) { Text("Save rule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ActivityScreen(appData: AppDataRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history by appData.history.collectAsState(initial = emptyList())
    val storage = remember(context) { StorageRepository(context) }
    var selectedUndo by remember { mutableStateOf<OperationRecord?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Activity", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { scope.launch { appData.clearHistory() } }, enabled = history.isNotEmpty()) { Text("Clear history") }
        }
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No operations yet", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 14.dp)) {
                items(history, key = { it.id }) { record ->
                    ListItem(
                        headlineContent = { Text("${record.operation.replace('_', ' ')} · ${record.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${if (record.success) "Success" else "Failed"} · ${formatDate(record.timestampMillis)}\n${record.message}", maxLines = 3, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Text(if (record.success) "✓" else "!") },
                        trailingContent = {
                            if (record.undoAction.isNotBlank() && record.success) TextButton(onClick = { selectedUndo = record }) { Text("Undo") }
                        },
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
    selectedUndo?.let { record ->
        AlertDialog(
            onDismissRequest = { selectedUndo = null },
            title = { Text("Undo operation?") },
            text = { Text("Undo depends on support from the storage provider. The app will not claim success unless the provider confirms it.") },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    runCatching { storage.undo(record) }
                        .onSuccess {
                            appData.addHistory(OperationRecord(operation = "UNDO", name = record.name, sourceUri = record.destinationUri, success = it.success, message = it.message))
                            if (it.success) appData.markUndoUsed(record.id)
                        }
                        .onFailure {
                            appData.addHistory(OperationRecord(operation = "UNDO", name = record.name, sourceUri = record.destinationUri, success = false, message = it.message ?: "Undo failed."))
                            Toast.makeText(context, it.message ?: "Undo failed.", Toast.LENGTH_LONG).show()
                        }
                    selectedUndo = null
                }
            }) { Text("Undo") } },
            dismissButton = { TextButton(onClick = { selectedUndo = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsScreen(appData: AppDataRepository, settings: AppSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch { appData.updateSettings(settings.copy(notificationsEnabled = granted)) }
        if (!granted) Toast.makeText(context, "Notifications are disabled. You can enable them in Android settings.", Toast.LENGTH_LONG).show()
    }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        SettingSwitch(
            title = "Automatic file detection",
            description = "Check selected rule folders periodically. Android may delay background work.",
            checked = settings.automaticScanning,
            onCheckedChange = { enabled ->
                scope.launch { appData.updateSettings(settings.copy(automaticScanning = enabled)) }
                WorkerScheduler.updatePeriodicScanning(context, enabled)
            },
        )
        SettingSwitch(
            title = "Notifications",
            description = "Notify when newly discovered files match a rule.",
            checked = settings.notificationsEnabled,
            onCheckedChange = { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else scope.launch { appData.updateSettings(settings.copy(notificationsEnabled = enabled)) }
            },
        )
        SettingSwitch(
            title = "Quiet mode",
            description = "Suppress organization notifications while continuing scheduled scans.",
            checked = settings.quietMode,
            onCheckedChange = { enabled -> scope.launch { appData.updateSettings(settings.copy(quietMode = enabled)) } },
        )
        SettingSwitch(
            title = "Dark theme",
            description = "Use a dark, AMOLED-friendly interface.",
            checked = settings.darkTheme,
            onCheckedChange = { enabled -> scope.launch { appData.updateSettings(settings.copy(darkTheme = enabled)) } },
        )
        Spacer(Modifier.height(8.dp))
        Text("Storage and privacy", style = MaterialTheme.typography.titleMedium)
        Text("The app requests access only to folders you select through Android's Storage Access Framework. Rules, favorites, and history are stored locally. File contents are not uploaded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Limitations", style = MaterialTheme.typography.titleMedium)
        Text("Protected Android/data and Android/obb locations may be unavailable. Background scans run no more often than Android's periodic-work minimum and may be delayed by battery policies. Automatic rules currently inspect direct files in each selected source folder, not nested subfolders.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(onClick = onConfirm, enabled = value.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun persistTreeGrant(context: Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    } catch (writeDenied: SecurityException) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (readDenied: SecurityException) {
            throw SecurityException("Android did not allow persistent access to this folder. Try another folder.", readDenied)
        }
    }
}

private fun openUri(context: Context, uri: Uri, mimeType: String?): Boolean {
    return try {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Open with"))
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No installed app can open this file.", Toast.LENGTH_LONG).show()
        false
    } catch (_: SecurityException) {
        Toast.makeText(context, "Access to this file was denied. Choose the folder again.", Toast.LENGTH_LONG).show()
        false
    }
}

private fun shareUri(context: Context, uri: Uri, mimeType: String?): Boolean {
    return try {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = mimeType ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share file"))
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app is available to share this file.", Toast.LENGTH_LONG).show()
        false
    } catch (_: SecurityException) {
        Toast.makeText(context, "Access to this file was denied.", Toast.LENGTH_LONG).show()
        false
    }
}

private fun BrowserEntry.toFavoriteRecord() = FavoriteRecord(uri, name, mimeType, isDirectory)

private fun StorageOperationResult.toHistoryRecord() = OperationRecord(
    operation = operation,
    name = name,
    sourceUri = sourceUri,
    destinationUri = destinationUri,
    sourceParentUri = sourceParentUri,
    destinationParentUri = destinationParentUri,
    success = success,
    message = message,
    undoAction = undoAction,
)

private fun folderLabel(uri: String): String = if (uri.isBlank()) "Not selected" else Uri.parse(uri).lastPathSegment?.substringAfterLast(':')?.take(48) ?: "Selected folder"

private fun fileGlyph(entry: BrowserEntry): String = when {
    entry.isDirectory -> "📁"
    entry.mimeType?.startsWith("image/") == true -> "▧"
    entry.mimeType?.startsWith("audio/") == true -> "♫"
    entry.mimeType?.startsWith("video/") == true -> "▶"
    entry.mimeType == "application/pdf" || entry.name.endsWith(".pdf", true) -> "PDF"
    entry.name.endsWith(".apk", true) -> "APK"
    entry.name.endsWith(".zip", true) || entry.name.endsWith(".7z", true) || entry.name.endsWith(".rar", true) -> "ZIP"
    entry.mimeType?.startsWith("text/") == true -> "TXT"
    else -> "FILE"
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

private fun formatDate(timestamp: Long): String = if (timestamp <= 0L) {
    "Date unavailable"
} else {
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
}
