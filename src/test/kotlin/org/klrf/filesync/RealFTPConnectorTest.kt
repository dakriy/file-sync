package org.klrf.filesync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.sequences.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.utils.io.errors.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.test.Test
import org.junit.jupiter.api.assertThrows
import org.klrf.filesync.gateways.FTPConnection
import org.klrf.filesync.gateways.RealFTPConnector
import org.mockftpserver.fake.FakeFtpServer
import org.mockftpserver.fake.UserAccount
import org.mockftpserver.fake.filesystem.DirectoryEntry
import org.mockftpserver.fake.filesystem.FileEntry
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem


class RealFTPConnectorTest {
    companion object {
        const val DEFAULT_USER = "user"
        const val DEFAULT_PASS = "password"
        const val HOME = "/home"
        const val PORT = 40822
    }

    private fun FTPServerTest(
        user: String = DEFAULT_USER,
        password: String = DEFAULT_PASS,
        directories: List<String> = listOf(HOME),
        files: List<FileEntry> = emptyList(),
        action: () -> Unit,
    ) {
        val ftpServer = FakeFtpServer().apply {
            addUserAccount(UserAccount(user, password, HOME))
            serverControlPort = PORT
            fileSystem = UnixFakeFileSystem().apply {
                directories.forEach { add(DirectoryEntry(it)) }
                files.forEach { add(it) }
            }
        }

        ftpServer.start()
        try {
            action()
        } finally {
            ftpServer.stop()
        }
    }

    private fun connection(path: String? = null) =
        RealFTPConnector(FTPConnection("localhost", DEFAULT_USER, DEFAULT_PASS, path, PORT))

    @Test
    fun `given invalid username it fails`() {
        val ex = assertThrows<IOException> {
            FTPServerTest("user2") {
                connection().listFiles()
            }
        }

        ex.message shouldBe "Unable to login to FTP server"
    }

    @Test
    fun `given invalid password it fails`() {
        val ex = assertThrows<IOException> {
            FTPServerTest(password = "trip it up lol") {
                connection().listFiles()
            }
        }

        ex.message shouldBe "Unable to login to FTP server"
    }

    @Test
    fun `given valid ftp server with no files, it lists no files`() {
        FTPServerTest {
            connection().listFiles().shouldBeEmpty()
        }
    }

    @Test
    fun `no files in non-existent directory`() {
        FTPServerTest {
            connection(path = "/stupid").listFiles().shouldBeEmpty()
        }
    }

    @Test
    fun `lists files in existing directory`() {
        val instant = Instant.now().minusSeconds(100).truncatedTo(ChronoUnit.MINUTES)
        val date = Date.from(instant)

        FTPServerTest(
            directories = listOf(HOME, "$HOME/dir"),
            files = listOf(FileEntry("$HOME/file1.txt").apply {
                lastModified = date
            }, FileEntry("$HOME/dir/file2.txt"))
        ) {
            connection().listFiles().toList() shouldBe listOf("file1.txt" to instant)
        }
    }

    @Test
    fun `can list files in a specific directory`() {
        val instant = Instant.now().minusSeconds(100).truncatedTo(ChronoUnit.MINUTES)
        val date = Date.from(instant)

        FTPServerTest(
            directories = listOf("$HOME/somedir"),
            files = listOf(FileEntry("$HOME/somedir/test-file.mp3").apply {
                lastModified = date
            }, FileEntry("$HOME/somedir/a second file.exe").apply { lastModified = date })
        ) {
            val files = connection(path = "$HOME/somedir").listFiles().toList()
            files shouldBe listOf("test-file.mp3" to instant, "a second file.exe" to instant)
        }
    }

    @Test
    fun `can download a specific file`() {
        val contents = "The file contents"
        FTPServerTest(
            directories = listOf("$HOME/some dir"),
            files = listOf(FileEntry("$HOME/some dir/test-file.txt", contents))
        ) {
            val result = connection(path = "$HOME/some dir").downloadFile("test-file.txt")
            result.readAllBytes() shouldBe contents.toByteArray()
        }
    }

    @Test
    fun `downloading invalid file does something`() {
        val contents = "The file contents"
        val ex = shouldThrow<IOException> {
            FTPServerTest(
                directories = listOf("$HOME/some dir"),
                files = listOf(FileEntry("$HOME/some dir/test-file.txt", contents))
            ) {
                connection().downloadFile("test-file.txt")
            }
        }

        ex.message shouldBe "File not found: /test-file.txt"
    }
}
