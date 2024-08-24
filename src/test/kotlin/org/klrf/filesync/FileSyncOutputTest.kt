package org.klrf.filesync

import com.google.common.jimfs.Jimfs
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.paths.shouldBeAFile
import io.kotest.matchers.paths.shouldContainFiles
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.io.path.div
import kotlin.io.path.getAttribute
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test

class FileSyncOutputTest {
    @Test
    fun `given no input no files should be output`() = fileSyncTest {
        config("""
          fileSync:
            programs:
              program:
                source:
                  type: Empty
        """.trimIndent())

        assert {
            fs.getPath("").listDirectoryEntries().shouldBeEmpty()
        }
    }

    @Test
    fun `given no input files but an output path directory should be created`() = fileSyncTest {
        config("""
          fileSync:
            output:
              dir: /the/path/to/dest
            programs:
              program:
                source:
                  type: Empty
        """.trimIndent())

        assert {
            val path = fs.getPath("/the/path/to/dest")
            Files.exists(path).shouldBeTrue()
        }
    }

    @Test
    fun `given an input file it should be created on disk`() = fileSyncTest {
        config("""
          fileSync:
            output:
              dir: /the/path/to/dest
            programs:
              program:
                source:
                  type: FTP
                  url: fake.url
        """.trimIndent())

        val item1 = MemoryItem("program", "file 1")
        ftpConnector("fake.url", item1)

        assert {
            val path = fs.getPath("/the/path/to/dest/program")
            path.shouldContainFiles("file 1.mp3")
        }
    }

    @Test
    fun `given an input file it should write its contents to disk`() = fileSyncTest {
        config("""
          fileSync:
            output:
              dir: /the/path/to/dest
            programs:
              program:
                source:
                  type: FTP
                  url: fake.url
        """.trimIndent())

        val contents = "mine turtle"

        val item1 = MemoryItem("program", "file 1", data = contents.toByteArray())
        ftpConnector("fake.url", item1)

        assert {
            val path = fs.getPath("/the/path/to/dest/program")
            val file = path / "file 1.mp3"
            path.shouldContainFiles("file 1.mp3")
            Files.readString(file) shouldBe contents
        }
    }

    @Test
    fun `given many input files all should be output`() = fileSyncTest {
        config("""
          fileSync:
            output:
              dir: /the/path/to/dest
            programs:
              prog:
                source:
                  type: FTP
                  url: fake.url
        """.trimIndent())

        val contents = "mine turtle"

        val item1 = MemoryItem("program", "file 1", data = contents.toByteArray())
        val item2 = MemoryItem("program", "file 2", data = contents.toByteArray())
        ftpConnector("fake.url", item1, item2)

        assert {
            val path = fs.getPath("/the/path/to/dest/prog")
            val file1 = path / "file 1.mp3"
            val file2 = path / "file 2.mp3"
            path.shouldContainFiles("file 1.mp3", "file 2.mp3")
            Files.readString(file1) shouldBe contents
            Files.readString(file2) shouldBe contents
        }
    }

    @Test
    fun `given output disabled no files should be output`() = fileSyncTest {
        config("""
          fileSync:
            output:
              enabled: false
              dir: /the/path/to/dest
            programs:
              program:
                source:
                  type: FTP
                  url: fake.url
        """.trimIndent())

        val item1 = MemoryItem("program", "file 1")
        ftpConnector("fake.url", item1)

        assert {
            val path = fs.getPath("")
            path.listDirectoryEntries().shouldBeEmpty()
        }
    }

    @Test
    fun `given file to download, creation date should be the item creation date`() = fileSyncTest {
        config("""
          fileSync:
            output:
              dir: /the/path/to/dest
            programs:
              program:
                source:
                  type: FTP
                  url: fake.url
        """.trimIndent())

        val createdAt = Instant.now().minusSeconds(100)
            .let { it.minusNanos(it.nano.toLong()) }

        val item1 = MemoryItem("program", "file 1", createdAt)
        ftpConnector("fake.url", item1)

        assert {
            val path = fs.getPath("/the/path/to/dest/program/file 1.mp3")
            val attrs = Files.readAttributes(path, BasicFileAttributes::class.java)
            println(createdAt.nano)
            attrs.creationTime() shouldBe FileTime.from(createdAt.minusNanos(createdAt.nano.toLong()))
        }
    }
}
