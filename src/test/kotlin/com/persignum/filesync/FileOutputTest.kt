package com.persignum.filesync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.paths.shouldContainFile
import io.kotest.matchers.paths.shouldContainFiles
import io.kotest.matchers.paths.shouldExist
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.io.path.*
import kotlin.math.absoluteValue
import kotlin.test.Test

class FileOutputTest {
    @Test
    fun `given no input no files should be output`() = fileSyncTest {
        config(
            """
          fileSync:
            programs:
              - name: program
        """.trimIndent()
        )

        addSource("program")

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
              - name: program
        """.trimIndent()
        )

        val item1 = MemoryItem("file 1")
        addSource("program", item1)

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
              - name: program
        """.trimIndent()
        )

        val contents = "mine turtle"

        val item1 = MemoryItem("file 1", data = contents.toByteArray())
        addSource("program", item1)

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
              - name: prog
        """.trimIndent()
        )

        val contents = "mine turtle"

        val item1 = MemoryItem("file 1", data = contents.toByteArray())
        val item2 = MemoryItem("file 2", data = contents.toByteArray())
        addSource("prog", item1, item2)

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
              dryRun: true
              dir: /the/path/to/dest
            programs:
              - name: program
        """.trimIndent()
        )

        val item1 = MemoryItem("file 1")
        addSource("program", item1)

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
              - name: program
        """.trimIndent()
        )

        val createdAt = Instant.now().minusSeconds(100)
            .truncatedTo(ChronoUnit.SECONDS)

        val item1 = MemoryItem("file 1", createdAt)
        addSource("program", item1)

        assert {
            val path = fs.getPath("/the/path/to/dest/program/file 1")
            val attrs = path.readAttributes<BasicFileAttributes>()
            attrs.creationTime() shouldBe FileTime.from(createdAt)
        }
    }

    @Test
    fun `should not re-download already downloaded files`() = fileSyncTest {
        config(
            """
          fileSync:
            output:
              dir: ""
            programs:
              - name: program
        """.trimIndent()
        )

        val item1 = MemoryItem("file", data = "new file data".toByteArray())
        addSource("program", item1)
        val path = fs.getPath("program")
        path.createDirectories()
        val file = path / "file"
        file.createFile()
        file.writeBytes("hello".toByteArray())

        assert {
            file.readText() shouldBe "hello"
        }
    }

    @Test
    fun `default output directory is named output`() = fileSyncTest {
        config(
            """
          fileSync:
            programs:
              - name: program
        """.trimIndent()
        )

        val item1 = MemoryItem("file")
        addSource("program", item1)

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
              - name: program
        """.trimIndent()
        )

        val item1 = MemoryItem("file")
        addSource("program", item1)

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
              - name: program
                output:
                  filename: renamed
                  format: mp3
        """.trimIndent()
            )

            val item1 = MemoryItem("file.mp3", data = "new file data".toByteArray())
            addSource("program", item1)
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
                  - name: program
                    output:
                      filename: test
                      format: mp3
            """.trimIndent()
            )
            val createdAt = Instant.now().minus(10, ChronoUnit.DAYS)
                .truncatedTo(ChronoUnit.SECONDS)
            val item1 = MemoryItem(
                "test.ogg",
                data =
                this::class.java.classLoader.getResource("file_example_OOG_1MG.ogg")!!.readBytes(),
                createdAt = createdAt,
            )
            addSource("program", item1)

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

                val attrs = file.readAttributes<BasicFileAttributes>()
                attrs.lastModifiedTime() shouldBe FileTime.from(createdAt)
            }
        }
    } finally {
        File("build/test-output").deleteRecursively()
    }

    @Test
    fun `setting invalid id3 version throws an error and tells you correct versions`() {
        val ex = shouldThrow<IllegalStateException> {
            fileSyncTest {
                config(
                    """
                  fileSync:
                    output:
                      id3Version: asdfasdf
                    programs:
                      - name: program
                """.trimIndent()
                )

                val item1 = MemoryItem("file 1")
                addSource("program", item1)
            }
        }

        ex.message shouldBe "Unknown id3Version 'asdfasdf'. Valid values are [ID3_V22, ID3_V23, ID3_V24]."
    }

    @Test
    fun `given audio tags, output files are tagged properly`() {
        try {
            fileSyncTest {
                config(
                    """
              fileSync:
                output:
                  dir: build/test-output
                programs:
                  - name: program
                    output:
                      filename: test
                      format: mp3
                      tags:
                        genre: Program
                        artist: Dr. David DeRose
                        album: American Indians
                        comment: Hello
                        title: The_title
            """.trimIndent()
                )

                fs = FileSystems.getDefault()

                val item1 = MemoryItem(
                    "test.ogg",
                    data = this::class.java.classLoader.getResource("file_example_OOG_1MG.ogg")!!
                        .readBytes(),
                )
                addSource("program", item1)

                assert {
                    val file = File("build/test-output/transform/program/test.mp3")
                    file.shouldExist()

                    val audioFile = AudioFileIO.read(file)
                    val tag = audioFile.tag
                    listOf(
                        FieldKey.GENRE to "Program",
                        FieldKey.ARTIST to "Dr. David DeRose",
                        FieldKey.ALBUM to "American Indians",
                        FieldKey.COMMENT to "Hello",
                        FieldKey.TITLE to "The_title",
                    ).forEach { (key, value) ->
                        tag.getFirst(key) shouldBe value
                    }
                }
            }
        } finally {
            File("build/test-output").deleteRecursively()
        }
    }

    @Test
    fun `files should be uploaded to LibreTime`() = fileSyncTest {
        config(
            """
              fileSync:
                programs:
                  - name: program
            """.trimIndent()
        )

        val item1 = MemoryItem("file1.mp3")
        val item2 = MemoryItem("file2.mp3")
        addSource("program", item1, item2)

        assert {
            val base = fs.getPath("output/transform/program")
            libreTimeConnector.uploads shouldContainAll listOf(
                base / "file1.mp3",
                base / "file2.mp3"
            )
        }
    }

    @Test
    fun `files that exist in LibreTime should not be re-uploaded`() = fileSyncTest {
        config(
            """
              fileSync:
                programs:
                  - name: program
            """.trimIndent()
        )

        addLibreTimeHistory("file1.mp3")

        val item1 = MemoryItem("file1.mp3")
        val item2 = MemoryItem("file2.mp3")
        addSource("program", item1, item2)

        assert {
            libreTimeConnector.uploads shouldHaveSingleElement fs.getPath("output/transform/program/file2.mp3")
        }
    }

    @Test
    fun `no files are uploaded to LibreTime during a dry run`() = fileSyncTest {
        config(
            """
              fileSync:
                output:
                  dryRun: true
                programs:
                  - name: program
            """.trimIndent()
        )

        val item1 = MemoryItem("file1.mp3")
        addSource("program", item1)

        assert {
            libreTimeConnector.uploads.shouldBeEmpty()
        }
    }

    @Test
    fun `items are downloaded concurrently`() = fileSyncTest {
        var item1Begin: Instant? = null
        var item2Begin: Instant? = null
        config(
            """
              fileSync:
                programs:
                  - name: program1
            """.trimIndent()
        )

        val item1 = MemoryItem("file1.mp3") {
            item1Begin = Instant.now()
            delay(25)
        }
        val item2 = MemoryItem("file2.mp3") {
            item2Begin = Instant.now()
            delay(25)
        }
        addSource("program1", item1, item2)
        assert {
            val duration = Duration.between(item1Begin, item2Begin).toMillis().absoluteValue
            duration shouldBeLessThan 5
        }
    }

    @Test
    fun `programs can have download rate limits`() = fileSyncTest {
        var item1Begin: Instant? = null
        var item2Begin: Instant? = null
        config(
            """
              fileSync:
                sources:
                  - name: src
                    type: Empty
                    maxConcurrentDownloads: 1
                programs:
                  - name: program
                    source:
                      name: src
            """.trimIndent()
        )

        val item1 = MemoryItem("file1.mp3") {
            item1Begin = Instant.now()
            delay(25)
        }
        val item2 = MemoryItem("file2.mp3") {
            item2Begin = Instant.now()
            delay(25)
        }
        addSource("src", item1, item2)
        assert {
            val duration = Duration.between(item1Begin, item2Begin).toMillis().absoluteValue
            duration shouldBeGreaterThan 24
        }
    }

    @Test
    fun `programs respect global download rate limits`() = fileSyncTest {
        var item1Begin: Instant? = null
        var item2Begin: Instant? = null
        config(
            """
              fileSync:
                maxConcurrentDownloads: 1
                sources:
                  - name: src
                    type: Empty
                programs:
                  - name: program
                    source:
                      name: src
            """.trimIndent()
        )

        val item1 = MemoryItem("file1.mp3") {
            item1Begin = Instant.now()
            delay(25)
        }
        val item2 = MemoryItem("file2.mp3") {
            item2Begin = Instant.now()
            delay(25)
        }
        addSource("src", item1, item2)
        assert {
            val duration = Duration.between(item1Begin, item2Begin).toMillis().absoluteValue
            duration shouldBeGreaterThan 24
        }
    }
}
