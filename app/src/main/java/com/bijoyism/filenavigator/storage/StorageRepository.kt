package com.bijoyism.filenavigator.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.bijoyism.filenavigator.domain.ConflictPolicy
import com.bijoyism.filenavigator.domain.DestinationNameResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val BUFFER_SIZE = 16 * 1024
private const val MAX_ARCHIVE_ENTRIES = 10_000
private const val MAX_EXTRACTED_BYTES = 1024L * 1024L * 1024L

data class BrowserEntry(
    val name: String,
    val uri: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val mimeType: String?,
    val parentUri: String = "",
)

data class StorageOperationResult(
    val success: Boolean,
    val operation: String,
    val name: String,
    val sourceUri: String = "",
    val destinationUri: String = "",
    val sourceParentUri: String = "",
    val destinationParentUri: String = "",
    val message: String,
    val undoAction: String = "",
)

class StorageRepository(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver

    suspend fun listDirectory(treeUri: Uri, directoryUri: Uri): List<BrowserEntry> =
        withContext(Dispatchers.IO) {
            ensureTreeUri(treeUri)
            val documentId = documentIdFor(treeUri, directoryUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
            val cursor = contentResolver.query(childrenUri, projection, null, null, null)
                ?: throw IOException("Android's document provider couldn't list this folder.")
            val parentDocumentUri = documentUri(treeUri, documentId)
            cursor.use { result -> result.readEntries(treeUri, parentDocumentUri) }
                .sortedWith(compareBy<BrowserEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
        }

    fun rootDocumentUri(treeUri: Uri): Uri {
        ensureTreeUri(treeUri)
        return documentUri(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
    }

    suspend fun createFolder(treeUri: Uri, parentUri: Uri, rawName: String): StorageOperationResult =
        withContext(Dispatchers.IO) {
            val name = validateName(rawName)
            val parent = normalizeParent(treeUri, parentUri)
            val collision = listDirectory(treeUri, parent).any { it.name.equals(name, true) }
            if (collision) throw IOException("A file or folder named '$name' already exists.")
            val created = DocumentsContract.createDocument(
                contentResolver,
                parent,
                DocumentsContract.Document.MIME_TYPE_DIR,
                name,
            ) ?: throw IOException("The storage provider couldn't create this folder.")
            StorageOperationResult(true, "CREATE_FOLDER", name, destinationUri = created.toString(), destinationParentUri = parent.toString(), message = "Folder created.", undoAction = "DELETE_CREATED")
        }

    suspend fun rename(treeUri: Uri, entry: BrowserEntry, rawName: String): StorageOperationResult =
        withContext(Dispatchers.IO) {
            val newName = validateName(rawName)
            val parent = Uri.parse(entry.parentUri)
            val siblings = listDirectory(treeUri, parent)
            if (siblings.any { it.uri != entry.uri && it.name.equals(newName, true) }) {
                throw IOException("A file or folder named '$newName' already exists.")
            }
            val renamed = DocumentsContract.renameDocument(contentResolver, Uri.parse(entry.uri), newName)
                ?: throw IOException("The storage provider doesn't support renaming this item.")
            StorageOperationResult(
                success = true,
                operation = "RENAME",
                name = entry.name,
                sourceUri = entry.uri,
                destinationUri = renamed.toString(),
                sourceParentUri = parent.toString(),
                destinationParentUri = parent.toString(),
                message = "Renamed to $newName.",
                undoAction = "RENAME_BACK:${entry.name}",
            )
        }

    suspend fun delete(entry: BrowserEntry): StorageOperationResult = withContext(Dispatchers.IO) {
        val deleted = DocumentsContract.deleteDocument(contentResolver, Uri.parse(entry.uri))
        if (!deleted) throw IOException("The storage provider couldn't delete '${entry.name}'.")
        StorageOperationResult(
            success = true,
            operation = "DELETE",
            name = entry.name,
            sourceUri = entry.uri,
            sourceParentUri = entry.parentUri,
            message = "Deleted ${entry.name}.",
        )
    }

    suspend fun copy(
        sourceTreeUri: Uri,
        entry: BrowserEntry,
        destinationTreeUri: Uri,
        destinationParentUri: Uri,
        policy: ConflictPolicy = ConflictPolicy.UNIQUE_NAME,
    ): StorageOperationResult = withContext(Dispatchers.IO) {
        requireFile(entry, "copy")
        ensureTreeUri(sourceTreeUri)
        ensureTreeUri(destinationTreeUri)
        val source = Uri.parse(entry.uri)
        val destParent = normalizeParent(destinationTreeUri, destinationParentUri)
        val finalName = resolveName(destinationTreeUri, destParent, entry.name, policy)
            ?: return@withContext StorageOperationResult(false, "COPY", entry.name, sourceUri = entry.uri, sourceParentUri = entry.parentUri, destinationParentUri = destParent.toString(), message = "Skipped: a same-named item already exists.")
        val destination = DocumentsContract.createDocument(
            contentResolver,
            destParent,
            entry.mimeType?.takeIf { it.isNotBlank() } ?: mimeFromName(finalName),
            finalName,
        ) ?: throw IOException("The storage provider couldn't create '$finalName'.")
        try {
            val sourceDigest = copyStream(source, destination)
            val destinationDigest = digest(destination)
            if (!sourceDigest.contentEquals(destinationDigest)) throw IOException("Integrity verification failed for '$finalName'; source was kept.")
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(contentResolver, destination) }
            throw error
        }
        StorageOperationResult(
            true,
            "COPY",
            entry.name,
            sourceUri = entry.uri,
            destinationUri = destination.toString(),
            sourceParentUri = entry.parentUri,
            destinationParentUri = destParent.toString(),
            message = "Copied as $finalName and verified.",
            undoAction = "DELETE_COPY",
        )
    }

    suspend fun move(
        sourceTreeUri: Uri,
        entry: BrowserEntry,
        destinationTreeUri: Uri,
        destinationParentUri: Uri,
        policy: ConflictPolicy = ConflictPolicy.UNIQUE_NAME,
    ): StorageOperationResult = withContext(Dispatchers.IO) {
        ensureTreeUri(sourceTreeUri)
        ensureTreeUri(destinationTreeUri)
        val source = Uri.parse(entry.uri)
        val sourceParent = Uri.parse(entry.parentUri)
        val destParent = normalizeParent(destinationTreeUri, destinationParentUri)
        if (sourceParent.authority == destParent.authority &&
            runCatching { DocumentsContract.getDocumentId(sourceParent) == DocumentsContract.getDocumentId(destParent) }.getOrDefault(false)
        ) {
            throw IOException("The source and destination are the same folder. Choose a different destination to avoid an accidental rename.")
        }
        val finalName = resolveName(destinationTreeUri, destParent, entry.name, policy)
            ?: return@withContext StorageOperationResult(false, "MOVE", entry.name, sourceUri = entry.uri, sourceParentUri = entry.parentUri, destinationParentUri = destParent.toString(), message = "Skipped: a same-named item already exists.")

        if (entry.isDirectory) {
            if (DocumentsContract.getDocumentId(source).isNotBlank() &&
                source.authority == destParent.authority && finalName == entry.name
            ) {
                val moved = runCatching {
                    DocumentsContract.moveDocument(contentResolver, source, sourceParent, destParent)
                }.getOrNull()
                if (moved != null) return@withContext StorageOperationResult(
                    true, "MOVE", entry.name, entry.uri, moved.toString(), entry.parentUri, destParent.toString(),
                    "Folder moved.", "MOVE_BACK",
                )
            }
            throw IOException("This provider cannot safely move this folder. Folder copy/delete isn't attempted.")
        }

        // Prefer a provider-native move only when the destination name is unchanged.
        if (finalName == entry.name && source.authority == destParent.authority) {
            val moved = runCatching {
                DocumentsContract.moveDocument(contentResolver, source, sourceParent, destParent)
            }.getOrNull()
            if (moved != null) return@withContext StorageOperationResult(
                true, "MOVE", entry.name, entry.uri, moved.toString(), entry.parentUri, destParent.toString(),
                "Moved by the storage provider.", "MOVE_BACK",
            )
        }

        // Cross-provider or unsupported moves use copy -> digest verification -> delete.
        val copyEntry = entry.copy(name = finalName)
        val copyResult = copy(sourceTreeUri, copyEntry, destinationTreeUri, destParent, ConflictPolicy.SKIP)
        if (!copyResult.success) return@withContext copyResult.copy(operation = "MOVE")
        val deleted = runCatching { DocumentsContract.deleteDocument(contentResolver, source) }.getOrDefault(false)
        if (!deleted) {
            runCatching { DocumentsContract.deleteDocument(contentResolver, Uri.parse(copyResult.destinationUri)) }
            throw IOException("Destination was copied, but source deletion failed. The copy was rolled back where possible; source remains.")
        }
        copyResult.copy(
            operation = "MOVE",
            name = entry.name,
            message = "Moved as $finalName; copy verified before source deletion.",
            undoAction = "MOVE_BACK",
        )
    }

    suspend fun createZip(
        entries: List<BrowserEntry>,
        destinationTreeUri: Uri,
        destinationParentUri: Uri,
        rawArchiveName: String,
    ): StorageOperationResult = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) throw IOException("Select at least one file to create a ZIP archive.")
        if (entries.any { it.isDirectory }) throw IOException("ZIP creation currently accepts files only. Remove folders from the selection.")
        ensureTreeUri(destinationTreeUri)
        val archiveName = if (rawArchiveName.endsWith(".zip", true)) validateName(rawArchiveName) else validateName("$rawArchiveName.zip")
        val parent = normalizeParent(destinationTreeUri, destinationParentUri)
        val resolvedName = resolveName(destinationTreeUri, parent, archiveName, ConflictPolicy.UNIQUE_NAME)
            ?: throw IOException("Could not resolve an archive filename conflict.")
        val destination = DocumentsContract.createDocument(contentResolver, parent, "application/zip", resolvedName)
            ?: throw IOException("The provider couldn't create the ZIP archive.")
        try {
            val usedNames = mutableSetOf<String>()
            contentResolver.openOutputStream(destination, "w")?.use { output ->
                ZipOutputStream(output).use { zip ->
                    entries.forEach { entry ->
                        val requestedName = validateName(entry.name)
                        val uniqueName = DestinationNameResolver.resolve(requestedName, usedNames, ConflictPolicy.UNIQUE_NAME)
                            ?: requestedName
                        usedNames += uniqueName
                        zip.putNextEntry(ZipEntry(uniqueName))
                        contentResolver.openInputStream(Uri.parse(entry.uri))?.use { input -> input.copyTo(zip, BUFFER_SIZE) }
                            ?: throw IOException("Couldn't read '${entry.name}' while creating the archive.")
                        zip.closeEntry()
                    }
                }
            } ?: throw IOException("The provider couldn't open the ZIP archive for writing.")
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(contentResolver, destination) }
            throw error
        }
        StorageOperationResult(
            success = true,
            operation = "CREATE_ARCHIVE",
            name = resolvedName,
            destinationUri = destination.toString(),
            destinationParentUri = parent.toString(),
            message = "Created ${entries.size} ZIP entr${if (entries.size == 1) "y" else "ies"} as $resolvedName.",
            undoAction = "DELETE_COPY",
        )
    }

    suspend fun extractZip(
        archive: BrowserEntry,
        destinationTreeUri: Uri,
        destinationParentUri: Uri,
    ): StorageOperationResult = withContext(Dispatchers.IO) {
        if (archive.isDirectory || !archive.name.endsWith(".zip", true)) {
            throw IOException("Choose a .zip file to extract.")
        }
        ensureTreeUri(destinationTreeUri)
        val parent = normalizeParent(destinationTreeUri, destinationParentUri)
        val createdUris = mutableListOf<Uri>()
        val existingNames = listDirectory(destinationTreeUri, parent).mapTo(mutableSetOf()) { it.name }
        var extractedCount = 0
        var entriesSeen = 0
        var extractedBytes = 0L
        try {
            val input = contentResolver.openInputStream(Uri.parse(archive.uri))
                ?: throw IOException("The provider couldn't open this archive.")
            ZipInputStream(input).use { zip ->
                var zipEntry = zip.nextEntry
                while (zipEntry != null) {
                    entriesSeen++
                    if (entriesSeen > MAX_ARCHIVE_ENTRIES) {
                        throw IOException("This archive contains more than $MAX_ARCHIVE_ENTRIES entries.")
                    }
                    if (!zipEntry.isDirectory) {
                        val archivePath = zipEntry.name.replace('\\', '/')
                        if (archivePath.startsWith('/') || archivePath.split('/').any { it == ".." } || archivePath.contains('\u0000')) {
                            throw IOException("Unsafe path found in the archive: ${zipEntry.name}")
                        }
                        // Extract to the chosen folder's root; never create archive-supplied paths.
                        val entryName = validateName(archivePath.substringAfterLast('/'))
                        val finalName = DestinationNameResolver.resolve(entryName, existingNames, ConflictPolicy.UNIQUE_NAME)
                            ?: throw IOException("Couldn't choose a safe name for ${zipEntry.name}.")
                        existingNames += finalName
                        val outputUri = DocumentsContract.createDocument(
                            contentResolver,
                            parent,
                            mimeFromName(finalName),
                            finalName,
                        ) ?: throw IOException("Couldn't create '$finalName' while extracting.")
                        createdUris += outputUri
                        try {
                            contentResolver.openOutputStream(outputUri, "w")?.use { output ->
                                val buffer = ByteArray(BUFFER_SIZE)
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count < 0) break
                                    extractedBytes += count
                                    if (extractedBytes > MAX_EXTRACTED_BYTES) {
                                        throw IOException("Extraction exceeds the 1 GiB safety limit.")
                                    }
                                    output.write(buffer, 0, count)
                                }
                            } ?: throw IOException("Couldn't write '$finalName' while extracting.")
                            extractedCount++
                        } catch (error: Exception) {
                            runCatching { DocumentsContract.deleteDocument(contentResolver, outputUri) }
                            createdUris.remove(outputUri)
                            throw error
                        }
                    }
                    zip.closeEntry()
                    zipEntry = zip.nextEntry
                }
            }
        } catch (error: Exception) {
            createdUris.forEach { uri -> runCatching { DocumentsContract.deleteDocument(contentResolver, uri) } }
            throw IOException("ZIP extraction stopped; partial output was rolled back where the provider allowed it. ${error.message}", error)
        }
        StorageOperationResult(
            success = true,
            operation = "EXTRACT_ARCHIVE",
            name = archive.name,
            sourceUri = archive.uri,
            destinationUri = parent.toString(),
            sourceParentUri = archive.parentUri,
            destinationParentUri = parent.toString(),
            message = "Extracted $extractedCount file${if (extractedCount == 1) "" else "s"}.",
        )
    }

    suspend fun undo(record: com.bijoyism.filenavigator.data.OperationRecord): StorageOperationResult = withContext(Dispatchers.IO) {
        when {
            record.undoAction == "DELETE_COPY" -> {
                val uri = Uri.parse(record.destinationUri)
                val success = DocumentsContract.deleteDocument(contentResolver, uri)
                if (!success) throw IOException("Undo failed: copied item couldn't be removed from its destination.")
                StorageOperationResult(true, "UNDO", record.name, sourceUri = record.destinationUri, message = "Undo completed.")
            }
            record.undoAction == "DELETE_CREATED" -> {
                val uri = Uri.parse(record.destinationUri)
                val tree = treeUriFromDocument(uri)
                if (runCatching { listDirectory(tree, uri).isNotEmpty() }.getOrDefault(true)) {
                    throw IOException("The created item is no longer empty, so it wasn't deleted during undo.")
                }
                val success = DocumentsContract.deleteDocument(contentResolver, uri)
                if (!success) throw IOException("Undo failed: created item couldn't be removed.")
                StorageOperationResult(true, "UNDO", record.name, sourceUri = record.destinationUri, message = "Undo completed.")
            }
            record.undoAction == "MOVE_BACK" -> {
                val sourceParent = Uri.parse(record.destinationParentUri)
                val destinationParent = Uri.parse(record.sourceParentUri)
                val movedBack = runCatching {
                    DocumentsContract.moveDocument(
                        contentResolver,
                        Uri.parse(record.destinationUri),
                        sourceParent,
                        destinationParent,
                    )
                }.getOrNull()
                if (movedBack != null) {
                    val desiredName = record.name
                    val returnedName = runCatching { queryDisplayName(movedBack) }.getOrNull()
                    val finalUri = if (returnedName != null && !returnedName.equals(desiredName, true)) {
                        runCatching { DocumentsContract.renameDocument(contentResolver, movedBack, desiredName) }.getOrNull() ?: movedBack
                    } else movedBack
                    StorageOperationResult(true, "UNDO", record.name, sourceUri = record.destinationUri, destinationUri = finalUri.toString(), message = "Move undone.")
                } else {
                    val destinationTree = treeUriFromDocument(sourceParent)
                    val sourceTree = treeUriFromDocument(destinationParent)
                    val entry = BrowserEntry(
                        name = record.name,
                        uri = record.destinationUri,
                        isDirectory = false,
                        sizeBytes = 0L,
                        lastModifiedMillis = 0L,
                        mimeType = contentResolver.getType(Uri.parse(record.destinationUri)),
                        parentUri = sourceParent.toString(),
                    )
                    val result = move(destinationTree, entry, sourceTree, destinationParent, ConflictPolicy.UNIQUE_NAME)
                    if (!result.success) throw IOException("Undo move was skipped: ${result.message}")
                    val returnedUri = Uri.parse(result.destinationUri)
                    val returnedName = runCatching { queryDisplayName(returnedUri) }.getOrNull()
                    val restoredUri = if (returnedName != null && !returnedName.equals(record.name, true)) {
                        runCatching { DocumentsContract.renameDocument(contentResolver, returnedUri, record.name) }.getOrNull() ?: returnedUri
                    } else returnedUri
                    StorageOperationResult(true, "UNDO", record.name, sourceUri = record.destinationUri, destinationUri = restoredUri.toString(), message = "Move undone using verified copy and source deletion.")
                }
            }
            record.undoAction.startsWith("RENAME_BACK:") -> {
                val previousName = record.undoAction.removePrefix("RENAME_BACK:")
                val renamed = DocumentsContract.renameDocument(contentResolver, Uri.parse(record.destinationUri), previousName)
                    ?: throw IOException("This provider doesn't support undoing this rename.")
                StorageOperationResult(true, "UNDO", record.name, destinationUri = renamed.toString(), message = "Rename undone.")
            }
            else -> throw IOException("This operation cannot safely be undone.")
        }
    }

    private suspend fun resolveName(
        treeUri: Uri,
        destinationParent: Uri,
        originalName: String,
        policy: ConflictPolicy,
    ): String? {
        val names = listDirectory(treeUri, destinationParent).map { it.name }.toSet()
        return try {
            DestinationNameResolver.resolve(originalName, names, policy)
        } catch (error: IllegalStateException) {
            throw IOException(error.message ?: "Destination conflict could not be resolved.", error)
        }
    }

    private fun copyStream(source: Uri, destination: Uri): ByteArray =
        contentResolver.openInputStream(source)?.use { input ->
            contentResolver.openOutputStream(destination, "w")?.use { output ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
                output.flush()
                digest.digest()
            } ?: throw IOException("The provider couldn't open the destination for writing.")
        } ?: throw IOException("The provider couldn't open the source for reading.")

    private fun digest(uri: Uri): ByteArray = contentResolver.openInputStream(uri)?.use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest()
    } ?: throw IOException("The provider couldn't verify the copied file.")

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val column = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                if (column >= 0) return cursor.getString(column)
            }
        }
        return null
    }

    private fun treeUriFromDocument(uri: Uri): Uri {
        val authority = uri.authority ?: throw IOException("Cannot restore the storage permission for this location.")
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }
            .getOrElse { throw IOException("The original folder permission is unavailable.", it) }
        return DocumentsContract.buildTreeDocumentUri(authority, treeId)
    }

    private fun ensureTreeUri(uri: Uri) {
        if (!DocumentsContract.isTreeUri(uri)) throw IOException("The saved folder permission is invalid. Choose the folder again.")
    }

    private fun documentIdFor(treeUri: Uri, directoryUri: Uri): String = if (DocumentsContract.isTreeUri(directoryUri)) {
        DocumentsContract.getTreeDocumentId(directoryUri)
    } else if (directoryUri.authority == treeUri.authority && runCatching { DocumentsContract.getDocumentId(directoryUri) }.isSuccess) {
        DocumentsContract.getDocumentId(directoryUri)
    } else {
        throw IOException("This folder is outside the selected tree. Choose an authorized folder.")
    }

    private fun documentUri(treeUri: Uri, documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    private fun normalizeParent(treeUri: Uri, parentUri: Uri): Uri = documentUri(treeUri, documentIdFor(treeUri, parentUri))

    private fun validateName(rawName: String): String {
        val name = rawName.trim()
        if (name.isBlank() || name == "." || name == ".." || name.contains('/') || name.contains('\\')) {
            throw IOException("Enter a valid name without path separators.")
        }
        return name
    }

    private fun requireFile(entry: BrowserEntry, operation: String) {
        if (entry.isDirectory) throw IOException("Folder $operation isn't supported through this action yet; choose files only.")
    }

    private fun mimeFromName(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    private fun android.database.Cursor.readEntries(treeUri: Uri, parentDocumentUri: Uri): List<BrowserEntry> {
        val idColumn = getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameColumn = getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeColumn = getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val sizeColumn = getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        val modifiedColumn = getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        if (idColumn < 0 || nameColumn < 0 || mimeColumn < 0) {
            throw IOException("This document provider returned incomplete file information.")
        }
        val entries = mutableListOf<BrowserEntry>()
        while (moveToNext()) {
            val documentId = getString(idColumn) ?: continue
            val mimeType = getString(mimeColumn)
            entries += BrowserEntry(
                name = getString(nameColumn) ?: "Unnamed item",
                uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString(),
                isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR,
                sizeBytes = if (sizeColumn >= 0 && !isNull(sizeColumn)) getLong(sizeColumn).coerceAtLeast(0L) else 0L,
                lastModifiedMillis = if (modifiedColumn >= 0 && !isNull(modifiedColumn)) getLong(modifiedColumn).coerceAtLeast(0L) else 0L,
                mimeType = mimeType,
                parentUri = parentDocumentUri.toString(),
            )
        }
        return entries
    }
}
