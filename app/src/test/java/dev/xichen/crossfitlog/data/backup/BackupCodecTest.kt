package dev.xichen.crossfitlog.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupCodecTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun archiveRoundTripsDatabaseAndPhotos() {
        val source = temporaryFolder.newFolder("source")
        File(source, BACKUP_DATABASE_PATH).writeBytes(byteArrayOf(1, 2, 3))
        File(source, "photos/thumbnails").mkdirs()
        File(source, "photos/photo.jpg").writeBytes(byteArrayOf(4, 5))
        File(source, "photos/thumbnails/photo.jpg").writeBytes(byteArrayOf(6))
        val paths = listOf(BACKUP_DATABASE_PATH, "photos/photo.jpg", "photos/thumbnails/photo.jpg")
        val manifest = manifest(source, paths)

        val archive = ByteArrayOutputStream().also { BackupCodec.writeArchive(it, source, manifest) }.toByteArray()
        val restored = temporaryFolder.newFolder("restored")
        assertEquals(manifest, BackupCodec.extractAndValidate(ByteArrayInputStream(archive), restored))
        paths.forEach { assertArrayEquals(File(source, it).readBytes(), File(restored, it).readBytes()) }
    }

    @Test fun unsupportedVersionIsRejected() {
        val file = temporaryFolder.newFile().apply { writeText("db") }
        val item = BackupFile(BACKUP_DATABASE_PATH, file.length(), BackupCodec.sha256(file))
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.validateManifest(BackupManifest(BACKUP_FORMAT, 99, 0, "x", 0, listOf(item)))
        }
    }

    @Test fun corruptedFileIsRejected() {
        val source = temporaryFolder.newFolder("corrupt-source")
        File(source, BACKUP_DATABASE_PATH).writeText("database")
        val item = BackupCodec.describeFile(source, BACKUP_DATABASE_PATH)
        val manifest = BackupManifest(BACKUP_FORMAT, BACKUP_VERSION, 0, "test", 0, listOf(item))
        val archive = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(BackupCodec.encodeManifest(manifest).encodeToByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry(BACKUP_DATABASE_PATH)); zip.write("tampered".encodeToByteArray()); zip.closeEntry()
            }
        }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.extractAndValidate(ByteArrayInputStream(archive), temporaryFolder.newFolder("corrupt-restored"))
        }
    }

    @Test fun missingManifestFileIsRejected() {
        val source = temporaryFolder.newFolder("missing-source")
        File(source, BACKUP_DATABASE_PATH).writeText("database")
        val manifest = manifest(source, listOf(BACKUP_DATABASE_PATH)).copy(
            files = listOf(
                BackupCodec.describeFile(source, BACKUP_DATABASE_PATH),
                BackupFile("photos/missing.jpg", 1, "0".repeat(64)),
            ),
        )
        val archive = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(BackupCodec.encodeManifest(manifest).encodeToByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry(BACKUP_DATABASE_PATH)); zip.write("database".encodeToByteArray()); zip.closeEntry()
            }
        }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.extractAndValidate(ByteArrayInputStream(archive), temporaryFolder.newFolder("missing-restored"))
        }
    }

    @Test fun zipTraversalIsRejected() {
        assertFalse(BackupCodec.safeZipPath("../database.sqlite"))
        assertFalse(BackupCodec.safeZipPath("photos/../../evil"))
        assertFalse(BackupCodec.safeZipPath("/absolute"))
        assertFalse(BackupCodec.safeZipPath("C:\\evil"))
        assertTrue(BackupCodec.safeZipPath("photos/photo.jpg"))
    }

    private fun manifest(root: File, paths: List<String>) = BackupManifest(
        BACKUP_FORMAT, BACKUP_VERSION, 123, "test", 1, paths.map { BackupCodec.describeFile(root, it) },
    )
}
