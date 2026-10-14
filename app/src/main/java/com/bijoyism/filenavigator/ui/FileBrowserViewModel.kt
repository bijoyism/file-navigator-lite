package com.bijoyism.filenavigator.ui

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bijoyism.filenavigator.data.UserPreferencesRepository
import com.bijoyism.filenavigator.storage.BrowserEntry
import com.bijoyism.filenavigator.storage.StorageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BrowserUiState {
    data object Loading : BrowserUiState
    data object NoFolderSelected : BrowserUiState
    data class Content(
        val currentUri: String,
        val isAtRoot: Boolean,
        val trail: List<String>,
        val entries: List<BrowserEntry>,
    ) : BrowserUiState
    data class Error(val message: String) : BrowserUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class FileBrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = UserPreferencesRepository(application)
    private val storageRepository = StorageRepository(application)
    private data class NavigationEntry(val uri: String, val name: String)

    private val navigationStack = MutableStateFlow<List<NavigationEntry>>(emptyList())
    private val refreshCounter = MutableStateFlow(0)

    val rootTreeUri: StateFlow<String?> = preferences.rootTreeUri.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    private data class Location(
        val rootUri: String?,
        val currentUri: String?,
        val isAtRoot: Boolean,
        val trail: List<String>,
    )

    val browserState: StateFlow<BrowserUiState> = combine(
        preferences.rootTreeUri,
        navigationStack,
        refreshCounter,
    ) { rootUri, stack, _ ->
        Location(
            rootUri = rootUri,
            currentUri = stack.lastOrNull()?.uri ?: rootUri,
            isAtRoot = stack.isEmpty(),
            trail = listOf("Selected folder") + stack.map { it.name },
        )
    }.mapLatest { location ->
        val rootUri = location.rootUri
        val currentUri = location.currentUri
        if (rootUri == null || currentUri == null) {
            BrowserUiState.NoFolderSelected
        } else {
            try {
                val entries = storageRepository.listDirectory(Uri.parse(rootUri), Uri.parse(currentUri))
                BrowserUiState.Content(currentUri, location.isAtRoot, location.trail, entries)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                BrowserUiState.Error(error.message ?: "Couldn't read this folder.")
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowserUiState.Loading)

    fun saveSelectedFolder(uri: Uri) {
        viewModelScope.launch {
            preferences.saveRootTreeUri(uri.toString())
            navigationStack.value = emptyList()
        }
    }

    fun openFolder(uri: String, name: String) {
        navigationStack.value = navigationStack.value + NavigationEntry(uri, name)
    }

    fun openFavorite(treeUri: Uri, folderUri: String, name: String) {
        viewModelScope.launch {
            preferences.saveRootTreeUri(treeUri.toString())
            val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            val folderId = runCatching { DocumentsContract.getDocumentId(Uri.parse(folderUri)) }.getOrNull()
            navigationStack.value = if (rootId != null && rootId == folderId) {
                emptyList()
            } else {
                listOf(NavigationEntry(folderUri, name))
            }
        }
    }

    fun navigateUp() {
        if (navigationStack.value.isNotEmpty()) {
            navigationStack.value = navigationStack.value.dropLast(1)
        }
    }

    fun refresh() {
        refreshCounter.value += 1
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(FileBrowserViewModel::class.java)) {
                        return FileBrowserViewModel(application) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
