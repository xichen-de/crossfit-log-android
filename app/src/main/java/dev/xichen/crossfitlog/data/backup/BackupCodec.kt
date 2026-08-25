package dev.xichen.crossfitlog.data.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupCodec {
    val json = Json { prettyPrint = true; ignoreUnknownKeys = true; explicitNulls = false }
    fun encodeManifest(value: BackupManifest): String = json.encodeToString(value)
    fun decodeManifest(value: String): BackupManifest = json.decodeFromString(value)

    fun validateManifest(manifest: BackupManifest) {
        require(manifest.format == BACKUP_FORMAT) { "This is not a CrossFit Log backup." }
        require(manifest.formatVersion == BACKUP_VERSION) { "This backup version is not supported." }
        require(manifest.sessionCount >= 0) { "The backup manifest is invalid." }
        require(manifest.files.isNotEmpty()) { "The backup file list is empty." }
        require(manifest.files.any { it.path == BACKUP_DATABASE_PATH }) { "The backup database is missing." }
        require(manifest.files.map { it.path }.distinct().size == manifest.files.size) { "The backup file list contains duplicates." }
        manifest.files.forEach {
            require(safeZipPath(it.path) && it.path != "manifest.json") { "The backup file list is invalid." }
            require(it.size >= 0 && it.sha256.matches(Regex("[0-9a-f]{64}"))) { "The backup file list is invalid." }
        }
    }

    fun safeZipPath(name: String): Boolean {
        if (name.isBlank() || name.startsWith('/') || name.startsWith('\\')) return false
        val parts = name.replace('\\', '/').split('/')
        return parts.none { it == ".." || it.isBlank() } && !name.contains(':')
    }

    fun friendlyFailure(error: Throwable): String = when (error) {
        is SerializationException -> "The backup contains malformed data."
        is IllegalArgumentException -> error.message ?: "The backup is invalid."
        else -> "The backup could not be read."
    }

    fun writeArchive(output: OutputStream, root: File, manifest: BackupManifest) {
        validateManifest(manifest)
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(encodeManifest(manifest).encodeToByteArray())
            zip.closeEntry()
            manifest.files.forEach { item ->
                val source = File(root, item.path)
                require(source.isFile && source.length() == item.size) { "A backup source file changed while it was being archived." }
                zip.putNextEntry(ZipEntry(item.path))
                val digest = MessageDigest.getInstance("SHA-256")
                DigestInputStream(source.inputStream(), digest).use { it.copyTo(zip) }
                zip.closeEntry()
                require(digest.digest().joinToString("") { "%02x".format(it) } == item.sha256) { "A backup source file changed while it was being archived." }
            }
        }
    }

    fun extractAndValidate(input: InputStream, destination: File): BackupManifest {
        destination.mkdirs()
        var manifest: BackupManifest? = null
        val extracted = linkedMapOf<String, File>()
        var totalBytes = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && safeZipPath(entry.name)) { "The backup contains an unsafe or unexpected entry." }
                require(entry.name !in extracted && !(entry.name == "manifest.json" && manifest != null)) { "The backup contains duplicate files." }
                if (entry.name == "manifest.json") {
                    manifest = decodeManifest(zip.readLimited(1_000_000).decodeToString()).also(::validateManifest)
                } else {
                    val target = File(destination, entry.name)
                    target.parentFile?.mkdirs()
                    target.outputStream().use { totalBytes += zip.copyLimited(it, MAX_ENTRY_BYTES) }
                    require(totalBytes <= MAX_ARCHIVE_BYTES) { "The backup is too large." }
                    extracted[entry.name] = target
                }
                zip.closeEntry()
            }
        }
        val result = requireNotNull(manifest) { "The backup manifest is missing." }
        require(extracted.keys == result.files.mapTo(linkedSetOf()) { it.path }) { "The backup contents do not match its manifest." }
        result.files.forEach { item ->
            val file = extracted.getValue(item.path)
            require(file.length() == item.size && sha256(file) == item.sha256) { "A backup file is corrupted: ${item.path}" }
        }
        return result
    }

    fun describeFile(root: File, path: String): BackupFile {
        require(safeZipPath(path))
        val file = File(root, path)
        require(file.isFile)
        return BackupFile(path, file.length(), sha256(file))
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun ZipInputStream.readLimited(max: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        copyLimited(output, max)
        return output.toByteArray()
    }

    private fun ZipInputStream.copyLimited(output: OutputStream, max: Long): Long {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= max) { "A backup entry is too large." }
            output.write(buffer, 0, count)
        }
        return total
    }

    private const val MAX_ENTRY_BYTES = 250_000_000L
    private const val MAX_ARCHIVE_BYTES = 1_000_000_000L
}
