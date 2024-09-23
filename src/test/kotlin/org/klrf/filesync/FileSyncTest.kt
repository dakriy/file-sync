package org.klrf.filesync

import com.uchuhimo.konf.source.LoadException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.regex.PatternSyntaxException
import kotlin.test.Test
import org.klrf.filesync.gateways.FTPConnection

class FileSyncTest {
    @Test
    fun `should do nothing when there are no programs`() = fileSyncTest {
        config(
            """
            fileSync:
            """.trimIndent()
        )

        assert { result ->
            result.shouldBeEmpty()
        }
    }

    @Test
    fun `should error given program does not contain source`() {
        shouldThrow<LoadException> {
            fileSyncTest {
                config(
                    """
                    fileSync:
                      programs:
                        ail:
                    """.trimIndent()
                )
            }
        }
    }

    @Test
    fun `should do nothing given a program with an empty source`() = fileSyncTest {
        config(
            """
            fileSync:
              programs:
                ail:
                  source:
                    type: Empty
            """.trimIndent()
        )

        assert { result ->
            result.shouldBeEmpty()
        }
    }

    @Test
    fun `should error given ftp server without url`() {
        val ex = shouldThrow<IllegalStateException> {
            fileSyncTest {
                config(
                    """
                    fileSync:
                      programs:
                        programName:
                          source:
                            type: FTP
                    """.trimIndent()
                )
            }
        }

        ex.message shouldContain "url is required for ftp source"
    }

    @Test
    fun `should output program items given a program with an item`() = fileSyncTest {
        config(
            """
            fileSync:
              programs:
                programName:
                  source:
                    type: FTP
                    url: fake.url
             """.trimIndent()
        )

        val item = MemoryItem("programName", "item 1")

        ftpConnector("fake.url", item)

        assert { result ->
            result shouldMatch listOf(item)
        }
    }

    @Test
    fun `should return 2 items given 2 items`() = fileSyncTest {
        config(
            """
            fileSync:
              programs:
                programName:
                  source:
                    type: FTP
                    url: fake.url
             """.trimIndent()
        )

        val item1 = MemoryItem("programName", "item 1")
        val item2 = MemoryItem("programName", "item 2")

        ftpConnector("fake.url", item1, item2)

        assert { result ->
            result shouldMatch listOf(item1, item2)
        }
    }

    @Test
    fun `should return 2 items given 2 items in 2 different programs`() = fileSyncTest {
        config(
            """
            fileSync:
              programs:
                program1:
                  source:
                    type: FTP
                    url: fake.url
                program2:
                  source:
                    type: FTP
                    url: fake2.url
             """.trimIndent()
        )

        val item1 = MemoryItem("program1", "item 1")
        val item2 = MemoryItem("program2", "item 2")

        ftpConnector("fake.url", item1)
        ftpConnector("fake2.url", item2)

        assert { result ->
            result shouldMatch listOf(item1, item2)
        }
    }


    @Test
    fun `should find ftp connector given all the properties`() = fileSyncTest {
        config(
            """
            fileSync:
              programs:
                programName:
                  source:
                    type: FTP
                    url: test.ftp.url
                    username: user
                    password: pass
                    path: /the/ftp/path
            """.trimIndent()
        )

        ftpConnector(
            FTPClientStub(
                FTPConnection(
                    url = "test.ftp.url",
                    username = "user",
                    password = "pass",
                    path = "/the/ftp/path",
                )
            )
        )

        assert {
            // test succeeded if we got here. Just watching for an exception
            true.shouldBeTrue()
        }
    }

    @Test
    fun `parse spec requires a regex`() {
        val ex = shouldThrow<LoadException> {
            fileSyncTest {
                config(
                    """
            fileSync:
              programs:
                programName:
                  source:
                    type: Empty
                  parse:
             """.trimIndent()
                )
            }
        }

        ex.message shouldContain "fail to load"
        ex.cause?.message shouldContain "to value of type"
    }

    @Test
    fun `should parse given regex`() {
        fileSyncTest {
            config(
                """
            fileSync:
              programs:
                programName:
                  source:
                    type: Empty
                  parse:
                    regex: the regex
             """.trimIndent()
            )
        }
    }

    @Test
    fun `should error given an invalid regex`() {
        val ex = shouldThrow<PatternSyntaxException> {
            fileSyncTest {
                config(
                    """
            fileSync:
              programs:
                programName:
                  source:
                    type: Empty
                  parse:
                    regex: (?<invalid_capture_group>.*)
             """.trimIndent()
                )
            }
        }

        ex.message shouldContain "named capturing group is missing trailing '>' near index 10"
    }

    @Test
    fun `should filter given a regex`() = fileSyncTest {
        config(
            """
            fileSync:
              programs:
                programName:
                  source:
                    type: FTP
                    url: fake.url
                  parse:
                    regex: .*special string.*
             """.trimIndent()
        )

        val item1 = MemoryItem("programName", "this is the item we want with the special string")
        val item2 = MemoryItem("programName", "some other random item")

        ftpConnector("fake.url", item1, item2)

        assert { result ->
            result shouldMatch listOf(item1)
        }
    }

    @Test
    fun `should map filename to output item given no output config`() = fileSyncTest {
        config(
            """
            fileSync:
              programs:
                programName:
                  source:
                    type: FTP
                    url: fake.url
             """.trimIndent()
        )

        val item1 = MemoryItem("programName", "item 1")
        val item2 = MemoryItem("programName", "item 2")

        ftpConnector("fake.url", item1, item2)

        assert { result ->
            result shouldMatch listOf(
                TestOutputItem("programName/item 1.mp3"),
                TestOutputItem("programName/item 2.mp3"),
            )
        }
    }

    @Test
    fun `should default output format to mp3 given a file with no extension`() = fileSyncTest {
        config(
            """
            fileSync:
              programs:
                programName:
                  source:
                    type: FTP
                    url: fake.url
             """.trimIndent()
        )

        val item = MemoryItem("programName", "item 1")

        ftpConnector("fake.url", item)

        assert { result ->
            result shouldMatch listOf(
                TestOutputItem("programName/item 1.mp3"),
            )
        }
    }

    @Test
    fun `should set output format to flac given a file with a flac extension`() = fileSyncTest {
        config(
            """
            fileSync:
              programs:
                programName:
                  source:
                    type: FTP
                    url: fake.url
             """.trimIndent()
        )

        val item = MemoryItem("programName", "testfile.flac")

        ftpConnector("fake.url", item)

        assert { result ->
            result shouldMatch listOf(
                TestOutputItem("programName/testfile.flac"),
            )
        }
    }

    @Test
    fun `should set format to flac given config output format as flac`() = fileSyncTest {
        config(
            """
            fileSync:
              programs:
                programName:
                  source:
                    type: FTP
                    url: fake.url
                  output:
                    format: "flac"
             """.trimIndent()
        )

        val item = MemoryItem("programName", "testfile.wav")

        ftpConnector("fake.url", item)

        assert { result ->
            result shouldMatch listOf(
                TestOutputItem("programName/testfile.flac"),
            )
        }
    }

    @Test
    fun `should override filename given overriding config`() = fileSyncTest {
        config(
            """
            fileSync:
              programs:
                programName:
                  source:
                    type: FTP
                    url: fake.url
                  output:
                    filename: new file name
             """.trimIndent()
        )

        val item = MemoryItem("programName", "testfile.wav")

        ftpConnector("fake.url", item)

        assert { result ->
            result shouldMatch listOf(
                TestOutputItem("programName/new file name.wav"),
            )
        }
    }

    @Test
    fun `should set tags given tag config`() = fileSyncTest {
        config(
            """
            fileSync:
              programs:
                programName:
                  source:
                    type: FTP
                    url: fake.url
                  output:
                    tags:
                      genre: Program
                      author: Dr. David DeRose
                      album: American Indian And Alaskan Nature Living
             """.trimIndent()
        )

        val item = MemoryItem("programName", "testfile.wav")

        ftpConnector("fake.url", item)

        assert { result ->
            val tags = mapOf(
                "genre" to "Program",
                "author" to "Dr. David DeRose",
                "album" to "American Indian And Alaskan Nature Living",
            )

            result shouldMatch listOf(
                TestOutputItem("programName/testfile.wav", tags = tags),
            )
        }
    }

    @Test
    fun `should inject old filename into tags given tag config that uses old filename`() =
        fileSyncTest {
            config(
                """
            fileSync:
              programs:
                programName:
                  source:
                    type: FTP
                    url: fake.url
                  output:
                    tags:
                      comments: "{old_filename}"
             """.trimIndent()
            )

            val item = MemoryItem("programName", "testfile.mp3")

            ftpConnector("fake.url", item)

            assert { result ->
                val tags = mapOf("comments" to "testfile")

                result shouldMatch listOf(
                    TestOutputItem("programName/testfile.mp3", tags = tags),
                )
            }
        }

    @Test
    fun `should inject old filename into filename given name config that uses old filename`() =
        fileSyncTest {
            config(
                """
            fileSync:
              programs:
                programName:
                  source:
                    type: FTP
                    url: fake.url
                  output:
                    filename: "OLD {old_filename}"
             """.trimIndent()
            )

            val item = MemoryItem("programName", "testfile.mp3")

            ftpConnector("fake.url", item)

            assert { result ->
                result shouldMatch listOf(
                    TestOutputItem("programName/OLD testfile.mp3"),
                )
            }
        }

    @Test
    fun `should inject old filename, old extension, and raw filename given name config that uses those`() =
        fileSyncTest {
            config(
                """
            fileSync:
              programs:
                programName:
                  source:
                    type: FTP
                    url: fake.url
                  output:
                    tags:
                      comments: "{raw_filename} {old_filename}.{old_extension}"
             """.trimIndent()
            )

            val item = MemoryItem("programName", "testfile.mp3")

            ftpConnector("fake.url", item)

            assert { result ->
                val tags = mapOf("comments" to "testfile.mp3 testfile.mp3")

                result shouldMatch listOf(
                    TestOutputItem("programName/testfile.mp3", tags = tags),
                )
            }
        }

    @Test
    fun `should do replacements with regex capture groups given regex with capture groups and config that uses the capture groups`() =
        fileSyncTest {
            config(
                """
            fileSync:
              programs:
                programName:
                  source:
                    type: FTP
                    url: fake.url
                  parse:
                    regex: AIL.*---(?<title>.*)\.flac
                  output:
                    filename: "{title}"
             """.trimIndent()
            )

            val item =
                MemoryItem("programName", "AIL who cares what text goes here---the real title.flac")

            ftpConnector("fake.url", item)

            assert { result ->
                result shouldMatch listOf(
                    TestOutputItem("programName/the real title.flac"),
                )
            }
        }

    @Test
    fun `should allow for date config given date config`() {
        fileSyncTest {
            config(
                """
            fileSync:
              programs:
                programName:
                  source:
                    type: Empty
                  parse:
                    regex: YEET
                    dates:
                      name: YY-MM-DD
             """.trimIndent()
            )
        }
    }

    @Test
    fun `should error given invalid date parse format`() {
        val ex = shouldThrow<IllegalArgumentException> {
            fileSyncTest {
                config(
                    """
            fileSync:
              programs:
                programName:
                  source:
                    type: Empty
                  parse:
                    regex: YEET
                    dates:
                      name: INVALID FORMAT
             """.trimIndent()
                )
            }
        }

        ex.message shouldBe "Unknown pattern letter: I"
    }

    @Test
    fun `should error given valid parse format but date was not a capture group`() {
        val ex = shouldThrow<IllegalStateException> {
            fileSyncTest {
                config(
                    """
                    fileSync:
                      stopOnFailure: true
                      programs:
                        program:
                          source:
                            type: FTP
                            url: fake.url
                          parse:
                            regex: file (?<date>\d+-\d+-\d+)
                            dates:
                              other: YYYY-MM-DD
                     """.trimIndent()
                )

                val item = MemoryItem(
                    "program",
                    "file 1-1-1",
                )

                ftpConnector("fake.url", item)
            }
        }

        ex.message shouldBe "Capture group 'other' does not exist in 'file (?<date>\\d+-\\d+-\\d+)' for program 'program'"
    }

    @Test
    fun `should error given valid parse format but specified date does not follow format`() {
        val ex = shouldThrow<DateTimeParseException> {
            fileSyncTest {
                config(
                    """
                    fileSync:
                      stopOnFailure: true
                      programs:
                        program:
                          source:
                            type: FTP
                            url: fake.url
                          parse:
                            regex: file (?<date>\d+-\d+-\d+)
                            dates:
                              date: YYYY.MM.DD
                     """.trimIndent()
                )

                val item = MemoryItem(
                    "program",
                    "file 1-1-1",
                )

                ftpConnector("fake.url", item)
            }
        }

        ex.message shouldBe "Text '1-1-1' could not be parsed at index 0"
    }

    @Test
    fun `should error given invalid output format for date`() {
        val ex = shouldThrow<IllegalArgumentException> {
            fileSyncTest {
                config(
                    """
                    fileSync:
                      stopOnFailure: true
                      programs:
                        program:
                          source:
                            type: FTP
                            url: fake.url
                          parse:
                            regex: file (?<date>\d+-\d+-\d+)
                            dates:
                              date: MM-dd-yy
                          output:
                            filename: "{date:INVALID FORMAT}"
                     """.trimIndent()
                )

                val item = MemoryItem(
                    "program",
                    "file 03-02-23",
                )

                ftpConnector("fake.url", item)
            }
        }
        ex.message shouldContain "Unknown pattern letter: I"
    }

    @Test
    fun `should inject formatted date given a date to parse`() {
        fileSyncTest {
            config(
                """
                fileSync:
                  programs:
                    program:
                      source:
                        type: FTP
                        url: fake.url
                      parse:
                        regex: file (?<date>\d+-\d+-\d+)
                        dates:
                          date: MM-dd-yy
                      output:
                        filename: "{date:yyyy-MM-dd}"
                 """.trimIndent()
            )

            val item = MemoryItem(
                "program",
                "file 03-02-23",
            )

            ftpConnector("fake.url", item)

            assert { results ->
                results shouldMatch listOf(TestOutputItem("program/2023-03-02.mp3"))
            }
        }
    }

    @Test
    fun `should inject multiple formatted dates given many dates to parse`() {
        fileSyncTest {
            config(
                """
                fileSync:
                  programs:
                    program:
                      source:
                        type: FTP
                        url: fake.url
                      parse:
                        regex: file (?<date>\d+-\d+-\d+)
                        dates:
                          date: MM-dd-yy
                      output:
                        filename: "{date:yyyy-MM-dd} {date:yy-MM-dd}"
                 """.trimIndent()
            )

            val item = MemoryItem(
                "program",
                "file 03-02-23",
            )

            ftpConnector("fake.url", item)

            assert { results ->
                results shouldMatch listOf(TestOutputItem("program/2023-03-02 23-03-02.mp3"))
            }
        }
    }

    @Test
    fun `should only download last 5 configured files given limit of 5`() {
        fileSyncTest {
            config(
                """
                fileSync:
                  programs:
                    program:
                      source:
                        type: FTP
                        url: fake.url
                      parse:
                        regex: item \d
                      output:
                        limit: 5
                 """.trimIndent()
            )

            val items = (1..20).map { i ->
                val j = i / 2
                val otherText = if (i % 2 == 0) {
                    "other"
                } else "item"
                MemoryItem("program", "$otherText $j")
            }

            ftpConnector("fake.url", *items.toTypedArray())

            assert { results ->
                results shouldMatch items
                    .filterIndexed { index, _ -> index % 2 == 0 }
                    .take(5)
            }
        }
    }

    @Test
    fun `should match partial by default`() = fileSyncTest {
            config(
                """
                fileSync:
                  programs:
                    program:
                      source:
                        type: FTP
                        url: fake.url
                      parse:
                        regex: item
                 """.trimIndent()
            )

            val item = MemoryItem("program", "an item")

            ftpConnector("fake.url", item)

            assert { results ->
                results shouldMatch listOf(item)
            }
    }

    @Test
    fun `should match full given match full in config`() = fileSyncTest {
        config(
            """
                fileSync:
                  programs:
                    program:
                      source:
                        type: FTP
                        url: fake.url
                      parse:
                        regex: item
                        entireMatch: true
                 """.trimIndent()
        )

        val item = MemoryItem("program", "an item")

        ftpConnector("fake.url", item)

        assert { results ->
            results.shouldBeEmpty()
        }
    }

    @Test
    fun `should error on parse failure given flag to error on parse failure`() {
        val ex = shouldThrow<IllegalStateException> {
            fileSyncTest {
                config(
                    """
                fileSync:
                  stopOnFailure: true
                  programs:
                    program:
                      source:
                        type: FTP
                        url: fake.url
                      parse:
                        regex: item \d
                        strict: true
                 """.trimIndent()
                )

                val item = MemoryItem("program", "an item")

                ftpConnector("fake.url", item)
            }
        }

        ex.message shouldContain "Item in program did not match 'an item'"
    }

    @Test
    fun `should be ordered with newest first given oldest first`() = fileSyncTest {
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

        val item1 = MemoryItem("program", "file 1", Instant.now().minusSeconds(1000))
        val item2 = MemoryItem("program", "file 2", Instant.now())

        ftpConnector("fake.url", item1, item2)

        assert { results ->
            results shouldMatch listOf(item2, item1)
        }
    }
}
