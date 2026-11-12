package com.example.trackly.data.backup

import android.content.Context
import android.net.Uri
import com.example.trackly.domain.repository.DocumentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** [DocumentStore] for files picked through the Storage Access Framework (no storage permission). */
class ContentResolverDocumentStore(context: Context) : DocumentStore {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun read(uri: String): String = withContext(Dispatchers.IO) {
        val stream = resolver.openInputStream(Uri.parse(uri)) ?: throw IOException("Cannot open $uri")
        stream.use { input ->
            val bytes = input.readNBytesCompat(MAX_BYTES + 1)
            if (bytes.size > MAX_BYTES) throw IOException("Backup file is too large")
            bytes.toString(Charsets.UTF_8)
        }
    }

    override suspend fun write(uri: String, text: String) = withContext(Dispatchers.IO) {
        // "wt" truncates an existing file instead of leaving old bytes at the end.
        val stream = resolver.openOutputStream(Uri.parse(uri), "wt") ?: throw IOException("Cannot open $uri")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private companion object {
        const val MAX_BYTES = 32 * 1024 * 1024
    }
}
