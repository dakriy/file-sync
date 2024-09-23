package org.klrf.filesync

import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.paths.shouldContainFile
import io.kotest.matchers.paths.shouldContainFiles
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.io.path.*
import kotlin.test.Test

//class FileOutput : Output {
//    override suspend fun save(items: List<OutputItem>) {
//        // write data to file
//        // file convert
//        // write ID3 tags
//        // audio normalization
//        // libretime upload
//    }
//}

class FileOutputTest {
    @Test
    fun `given no input no files should be output`() = fileSyncTest {
        config(
            """
          fileSync:
            programs:
              program:
                source:
                  type: Empty
        """.trimIndent()
        )

        assert {
            fs.getPath("").listDirectoryEntries().shouldBeEmpty()
        }
    }

    @Test
    fun `given an input file it should be created on disk`() = fileSyncTest {
        config(
            """
          fileSync:
            output:
              dir: /the/path/to/dest
            programs:
              program:
                source:
                  type: FTP
                  url: fake.url
        """.trimIndent()
        )

        val item1 = MemoryItem("program", "file 1")
        ftpConnector("fake.url", item1)

        assert {
            val path = fs.getPath("/the/path/to/dest/program")
            path.shouldContainFiles("file 1")
        }
    }

    @Test
    fun `given an input file it should write its contents to disk`() = fileSyncTest {
        config(
            """
          fileSync:
            output:
              dir: /the/path/to/dest
            programs:
              program:
                source:
                  type: FTP
                  url: fake.url
        """.trimIndent()
        )

        val contents = "mine turtle"

        val item1 = MemoryItem("program", "file 1", data = contents.toByteArray())
        ftpConnector("fake.url", item1)

        assert {
            val path = fs.getPath("/the/path/to/dest/program")
            val file = path / "file 1"
            path shouldContainFile "file 1"
            file.readText() shouldBe contents
        }
    }

    @Test
    fun `given many input files all should be output`() = fileSyncTest {
        config(
            """
          fileSync:
            output:
              dir: /the/path/to/dest
            programs:
              prog:
                source:
                  type: FTP
                  url: fake.url
        """.trimIndent()
        )

        val contents = "mine turtle"

        val item1 = MemoryItem("program", "file 1", data = contents.toByteArray())
        val item2 = MemoryItem("program", "file 2", data = contents.toByteArray())
        ftpConnector("fake.url", item1, item2)

        assert {
            val path = fs.getPath("/the/path/to/dest/prog")
            val file1 = path / "file 1"
            val file2 = path / "file 2"
            path.shouldContainFiles("file 1", "file 2")
            file1.readText() shouldBe contents
            file2.readText() shouldBe contents
        }
    }

    @Test
    fun `given output disabled no files should be output`() = fileSyncTest {
        config(
            """
          fileSync:
            output:
              enabled: false
              dir: /the/path/to/dest
            programs:
              program:
                source:
                  type: FTP
                  url: fake.url
        """.trimIndent()
        )

        val item1 = MemoryItem("program", "file 1")
        ftpConnector("fake.url", item1)

        assert {
            val path = fs.getPath("")
            path.listDirectoryEntries().shouldBeEmpty()
        }
    }

    @Test
    fun `given file to download, creation date should be the item creation date`() = fileSyncTest {
        config(
            """
          fileSync:
            output:
              dir: /the/path/to/dest
            programs:
              program:
                source:
                  type: FTP
                  url: fake.url
        """.trimIndent()
        )

        val createdAt = Instant.now().minusSeconds(100)
            .let { it.minusNanos(it.nano.toLong()) }

        val item1 = MemoryItem("program", "file 1", createdAt)
        ftpConnector("fake.url", item1)

        assert {
            val path = fs.getPath("/the/path/to/dest/program/file 1")
            val attrs = path.readAttributes<BasicFileAttributes>()
            attrs.creationTime() shouldBe FileTime.from(createdAt)
        }
    }

    @Test
    fun `should overwrite file given file that already exists`() = fileSyncTest {
        config(
            """
          fileSync:
            output:
              dir: ""
            programs:
              program:
                source:
                  type: FTP
                  url: fake.url
        """.trimIndent()
        )

        val item1 = MemoryItem("program", "file", data = "new file data".toByteArray())
        ftpConnector("fake.url", item1)
        val path = fs.getPath("program")
        path.createDirectories()
        val file = path / "file"
        file.createFile()
        file.writeBytes("hello".toByteArray())

        assert {
            file.readText() shouldBe "new file data"
        }
    }

    @Test
    fun `default output directory is named output`() = fileSyncTest {
        config(
            """
          fileSync:
            programs:
              program:
                source:
                  type: FTP
                  url: fake.url
        """.trimIndent()
        )

        val item1 = MemoryItem("program", "file")
        ftpConnector("fake.url", item1)

        assert {
            fs.getPath("output/transform/program").shouldExist()
        }
    }

    @Test
    fun `transform directory should be created`() = fileSyncTest {
        config(
            """
          fileSync:
            programs:
              program:
                source:
                  type: FTP
                  url: fake.url
        """.trimIndent()
        )

        val item1 = MemoryItem("program", "file")
        ftpConnector("fake.url", item1)

        assert {
            fs.getPath("output/transform/program").shouldExist()
        }
    }

    @Test
    fun `should straight move files to transform with their final filename if they don't need to be converted`() =
        fileSyncTest {
            config(
                """
          fileSync:
            output:
              dir: output
            programs:
              program:
                source:
                  type: FTP
                  url: fake.url
                output:
                  filename: renamed
                  format: mp3
        """.trimIndent()
            )

            val item1 = MemoryItem("program", "file.mp3", data = "new file data".toByteArray())
            ftpConnector("fake.url", item1)
            assert {
                val path = fs.getPath("output/transform/program")
                path.shouldExist()
                path shouldContainFile "renamed.mp3"
                (path / "renamed.mp3").readText() shouldBe "new file data"
            }
        }

    @Test
    fun `converts files if the extension does not match`() = try {
        fileSyncTest {
            config(
                """
              fileSync:
                output:
                  dir: build/test-output
                programs:
                  program:
                    source:
                      type: FTP
                      url: fake.url
                    output:
                      filename: test
                      format: mp3
            """.trimIndent()
            )
            val item1 = MemoryItem(
                "program", "test.ogg", data =
                this::class.java.classLoader.getResource("file_example_OOG_1MG.ogg")!!.readBytes()
            )
            ftpConnector("fake.url", item1)

            fs = FileSystems.getDefault()

            assert {
                val file = fs.getPath("build/test-output/transform/program/test.mp3")
                file.shouldExist()
                val bytes = file.readBytes()
                val matchesMp3Format = bytes.take(3) == listOf<Byte>(0x49, 0x44, 0x33)
                        || (bytes.take(6) == listOf(
                    0xFF, 0xFB, 0xFF, 0xF3, 0xFF, 0xF2,
                ).map { it.toByte() })

                withClue("First bytes were ${
                    bytes.take(6).joinToString { String.format("%02x", it) }
                }") {
                    matchesMp3Format.shouldBeTrue()
                }
            }
        }
    } finally {
        File("build/test-output").deleteRecursively()
    }
}
