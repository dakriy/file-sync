package org.klrf.filesync

import com.uchuhimo.konf.source.LoadException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.regex.PatternSyntaxException
import kotlin.test.Test

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
    fun `should output program items given a program with an item`() = fileSyncTest {
        config(
            """
            fileSync:
              programs:
                - name: programName
             """.trimIndent()
        )

        val item = MemoryItem("item 1")

        addSource("programName", item)

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
                - name: programName
             """.trimIndent()
        )

        val item1 = MemoryItem("item 1")
        val item2 = MemoryItem("item 2")

        addSource("programName", item1, item2)

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
                - name: program1
                - name: program2
             """.trimIndent()
        )

        val item1 = MemoryItem("item 1")
        val item2 = MemoryItem("item 2")

        addSource("program1", item1)
        addSource("program2", item2)

        assert { result ->
            result shouldMatch listOf(item1, item2)
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
                - name: programName
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
                - name: programName
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
                - name: programName
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
                - name: programName
                  parse:
                    regex: .*special string.*
             """.trimIndent()
        )

        val item1 = MemoryItem("this is the item we want with the special string")
        val item2 = MemoryItem("some other random item")

        addSource("programName", item1, item2)

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
                - name: programName
             """.trimIndent()
        )

        val item1 = MemoryItem("item 1")
        val item2 = MemoryItem("item 2")

        addSource("programName", item1, item2)

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
                - name: programName
             """.trimIndent()
        )

        val item = MemoryItem("item 1")

        addSource("programName", item)

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
                - name: programName
             """.trimIndent()
        )

        val item = MemoryItem("testfile.flac")

        addSource("programName", item)

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
                - name: programName
                  output:
                    format: "flac"
             """.trimIndent()
        )
        useEmptyOutput = true

        val item = MemoryItem("testfile.wav")

        addSource("programName", item)

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
                - name: programName
                  output:
                    filename: new file name
             """.trimIndent()
        )

        val item = MemoryItem("testfile.wav")

        addSource("programName", item)

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
                - name: programName
                  output:
                    tags:
                      genre: Program
                      author: Dr. David DeRose
                      album: American Indian And Alaskan Nature Living
             """.trimIndent()
        )

        val item = MemoryItem("testfile.wav")

        addSource("programName", item)

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
                - name: programName
                  output:
                    tags:
                      comments: "{old_filename}"
             """.trimIndent()
            )

            val item = MemoryItem("testfile.mp3")

            addSource("programName", item)

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
                - name: programName
                  output:
                    filename: "OLD {old_filename}"
             """.trimIndent()
            )

            val item = MemoryItem("testfile.mp3")

            addSource("programName", item)

            assert { result ->
                result shouldMatch listOf(
                    TestOutputItem("programName/OLD testfile.mp3"),
                )
            }
        }

    @Test
    fun `should inject item created at`() = fileSyncTest {
        config(
            """
            fileSync:
              programs:
                - name: programName
                  output:
                    filename: "created at {created_at}"
             """.trimIndent()
        )

        val createdAt = Instant.now()

        val item = MemoryItem("testfile.mp3", createdAt)

        addSource("programName", item)

        assert { result ->
            val today = LocalDateTime.ofInstant(createdAt, ZoneId.systemDefault())
            result shouldMatch listOf(
                TestOutputItem("programName/created at ${today}.mp3"),
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
                - name: programName
                  output:
                    tags:
                      comments: "{raw_filename} {old_filename}.{old_extension}"
             """.trimIndent()
            )

            val item = MemoryItem("testfile.mp3")

            addSource("programName", item)

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
                - name: programName
                  parse:
                    regex: AIL.*---(?<title>.*)\.flac
                  output:
                    filename: "{title}"
             """.trimIndent()
            )

            val item = MemoryItem("AIL who cares what text goes here---the real title.flac")

            addSource("programName", item)

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
                - name: programName
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
                - name: programName
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
                        - name: program
                          parse:
                            regex: file (?<date>\d+-\d+-\d+)
                            dates:
                              other: YYYY-MM-DD
                     """.trimIndent()
                )

                val item = MemoryItem("file 1-1-1")

                addSource("program", item)
            }
        }

        ex.message shouldBe "Capture group 'other' does not exist in 'file (?<date>\\d+-\\d+-\\d+)' for program 'program'"
    }

    @Test
    fun `should error given valid parse format but specified date does not follow format`() {
        val ex = shouldThrow<IllegalArgumentException> {
            fileSyncTest {
                config(
                    """
                    fileSync:
                      stopOnFailure: true
                      programs:
                        - name: program
                          parse:
                            regex: file (?<date>\d+-\d+-\d+)
                            dates:
                              date: YYYY.MM.DD
                     """.trimIndent()
                )

                val item = MemoryItem("file 1-1-1")

                addSource("program", item)
            }
        }

        ex.message shouldBe "Unable to parse date 'date' with value '1-1-1' for program/file 1-1-1."
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
                        - name: program
                          parse:
                            regex: file (?<date>\d+-\d+-\d+)
                            dates:
                              date: MM-dd-yy
                          output:
                            filename: "{date:INVALID FORMAT}"
                     """.trimIndent()
                )

                val item = MemoryItem("file 03-02-23")

                addSource("program", item)
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
                    - name: program
                      parse:
                        regex: file (?<date>\d+-\d+-\d+)
                        dates:
                          date: MM-dd-yy
                      output:
                        filename: "{date:yyyy-MM-dd}"
                 """.trimIndent()
            )

            val item = MemoryItem("file 03-02-23")

            addSource("program", item)

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
                    - name: program
                      parse:
                        regex: file (?<date>\d+-\d+-\d+)
                        dates:
                          date: MM-dd-yy
                      output:
                        filename: "{date:yyyy-MM-dd} {date:yy-MM-dd}"
                 """.trimIndent()
            )

            val item = MemoryItem("file 03-02-23")

            addSource("program", item)

            assert { results ->
                results shouldMatch listOf(TestOutputItem("program/2023-03-02 23-03-02.mp3"))
            }
        }
    }

    @Test
    fun `should be able to parse a date and time`() = fileSyncTest {
        config(
            """
                fileSync:
                  programs:
                    - name: program
                      parse:
                        regex: file (?<date>\d+-\d+-\d+ \d+:\d+:\d+)
                        dates:
                          date: MM-dd-yy H:m:s
                      output:
                        filename: "{date:yyyy-MM-dd H:s.m}"
                 """.trimIndent()
        )

        val item = MemoryItem("file 03-02-23 22:32:12")

        addSource("program", item)

        assert { results ->
            results shouldMatch listOf(TestOutputItem("program/2023-03-02 22:12.32.mp3"))
        }
    }

    @Test
    fun `should be able to parse a time`() = fileSyncTest {
        config(
            """
                fileSync:
                  programs:
                    - name: program
                      parse:
                        regex: file (?<date>\d+:\d+:\d+)
                        dates:
                          date: H:m:s
                      output:
                        filename: "{date:H m}"
                 """.trimIndent()
        )

        val item = MemoryItem("file 22:32:12")

        addSource("program", item)

        assert { results ->
            results shouldMatch listOf(TestOutputItem("program/22 32.mp3"))
        }
    }

    @Test
    fun `should error if date math string is not right format`() {
        val ex = shouldThrow<IllegalStateException> {
            fileSyncTest {
                config(
                    """
                fileSync:
                  stopOnFailure: true
                  programs:
                    - name: program
                      parse:
                        regex: file (?<date>\d+-\d+-\d+)
                        dates:
                          date: MM-dd-yy
                      output:
                        filename: "{dateasdfasdf:yyyy-MM-dd}"
                 """.trimIndent()
                )

                val item = MemoryItem("file 03-02-23")

                addSource("program", item)
            }
        }

        ex.message shouldBe "Duration format 'sdfasdf' should be in a format like '1y 2m 3d 4h 5m 6s'. Can include/exclude any unit."
    }

    @Test
    fun `should error if operator is not known`() {
        val ex = shouldThrow<IllegalStateException> {
            fileSyncTest {
                config(
                    """
                fileSync:
                  stopOnFailure: true
                  programs:
                    - name: program
                      parse:
                        regex: file (?<date>\d+-\d+-\d+)
                        dates:
                          date: MM-dd-yy
                      output:
                        filename: "{datea7d:yyyy-MM-dd}"
                 """.trimIndent()
                )

                val item = MemoryItem("file 03-02-23")

                addSource("program", item)
            }
        }

        ex.message shouldBe "Operator 'a' must be '+' or '-'."
    }

    @Test
    fun `should be able to add days to a date`() = fileSyncTest {
        config(
            """
                fileSync:
                  programs:
                    - name: program
                      parse:
                        regex: file (?<date>\d+-\d+-\d+)
                        dates:
                          date: MM-dd-yy
                      output:
                        filename: "{date+7d:yyyy-MM-dd}"
                 """.trimIndent()
        )

        val item = MemoryItem("file 03-02-23")

        addSource("program", item)

        assert { results ->
            results shouldMatch listOf(TestOutputItem("program/2023-03-09.mp3"))
        }
    }

    @Test
    fun `should be able to add duration to a date`() = fileSyncTest {
        config(
            """
                fileSync:
                  programs:
                    - name: program
                      parse:
                        regex: file (?<date>\d+-\d+-\d+ \d+:\d+:\d+)
                        dates:
                          date: yyyy-MM-dd H:m:s
                      output:
                        filename: "{date+7d 2h 3m 4s:yyyy-MM-dd H:m:s}"
                 """.trimIndent()
        )

        val item = MemoryItem("file 2024-09-30 13:37:53")

        addSource("program", item)

        assert { results ->
            results shouldMatch listOf(TestOutputItem("program/2024-10-07 15:40:57.mp3"))
        }
    }

    @Test
    fun `should only download last 5 configured files given limit of 5`() {
        fileSyncTest {
            config(
                """
                fileSync:
                  programs:
                    - name: program
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
                MemoryItem("$otherText $j")
            }

            addSource("program", *items.toTypedArray())

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
                    - name: program
                      parse:
                        regex: item
                 """.trimIndent()
        )

        val item = MemoryItem("an item")

        addSource("program", item)

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
                    - name: program
                      parse:
                        regex: item
                        entireMatch: true
                 """.trimIndent()
        )

        val item = MemoryItem("an item")

        addSource("program", item)

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
                    - name: program
                      parse:
                        regex: item \d
                        strict: true
                 """.trimIndent()
                )

                val item = MemoryItem("an item")

                addSource("program", item)
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
                    - name: program
                 """.trimIndent()
        )

        val item1 = MemoryItem("file 1", Instant.now().minusSeconds(1000))
        val item2 = MemoryItem("file 2", Instant.now())

        addSource("program", item1, item2)

        assert { results ->
            results shouldMatch listOf(item2, item1)
        }
    }

    @Test
    fun `should ignore extensions not whitelisted`() = fileSyncTest {
        config(
            """
            fileSync:
              sources:
                - name: source
                  type: Empty
              programs:
                - name: program
                  source:
                    name: source
                    extensions:
                      - ogg
                      - wav
             """.trimIndent()
        )

        val item1 = MemoryItem("file 1.mp3")
        val item2 = MemoryItem("file 2.ogg")
        val item3 = MemoryItem("file 3.wav")
        val item4 = MemoryItem("file 4.flac")

        addSource("program", item1, item2, item3, item4)

        assert { results ->
            results shouldMatch listOf(item2, item3)
        }
    }
}
