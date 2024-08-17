package org.klrf.filesync

import com.uchuhimo.konf.source.LoadException
import com.uchuhimo.konf.source.yaml
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.regex.PatternSyntaxException
import kotlin.test.Test
import org.intellij.lang.annotations.Language
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.klrf.filesync.domain.FileSync
import org.klrf.filesync.gateways.FTPConnector
import org.klrf.filesync.domain.Item
import org.klrf.filesync.domain.OutputGateway
import org.klrf.filesync.domain.OutputItem
import org.klrf.filesync.gateways.ConfigInput
import org.klrf.filesync.gateways.FTPConnection
import org.klrf.filesync.gateways.FileSyncTable
import org.klrf.filesync.gateways.FileSyncTable.hash
import org.klrf.filesync.gateways.FileSyncTable.name
import org.klrf.filesync.gateways.FileSyncTable.program

//class FileOutput : Output {
//    override suspend fun save(items: List<OutputItem>) {
//        // write data to file
//        // file convert
//        // write ID3 tags
//        // audio normalization
//    }
//}

data class MemoryItem(
    override val program: String,
    override val name: String,
    override val createdAt: Instant = defaultTime,
    val data: ByteArray = ByteArray(0),
) : Item {
    override fun data(): ByteArray = data

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MemoryItem

        if (program != other.program) return false
        if (name != other.name) return false
        if (createdAt != other.createdAt) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = program.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }

    companion object {
        private val defaultTime = Instant.now()
    }
}

class FTPClientStub(
    override val connection: FTPConnection,
    private val items: List<Item>,
) : FTPConnector {
    constructor(connection: FTPConnection, vararg items: Item) : this(connection, items.toList())

    override fun listFiles(): Sequence<Pair<String, Instant>> {
        return items.map { it.name to it.createdAt }.asSequence()
    }
}

infix fun Item.shouldMatch(item: MemoryItem) {
    program shouldBe item.program
    name shouldBe item.name
    data() shouldBe item.data
}

infix fun Collection<OutputItem>.shouldMatchItems(items: Collection<MemoryItem>) {
    this shouldHaveSize items.size
    zip(items).forEach { (outputItem, item) ->
        outputItem.item shouldMatch item
    }
}

infix fun Collection<OutputItem>.shouldMatch(items: Collection<OutputItem>) {
    map { it.copy(item = MemoryItem(it.item.program, it.item.name, it.item.createdAt, data = it.item.data())) } shouldBe items
}

class TestHarness {
    private var yaml: String = ""
    private val ftpConnectors = mutableListOf<FTPClientStub>()
    private var assertBlock: (List<OutputItem>) -> Unit = { }
    private var history = mutableListOf<Item>()

    fun config(@Language("YAML") yaml: String) {
        this.yaml = yaml
    }

    fun history(vararg items: Item) {
        history.addAll(items)
    }

    fun ftpConnector(url: String, vararg items: Item) {
        val ftpClient = FTPClientStub(FTPConnection(url), *items)

        ftpConnectors.add(ftpClient)
    }

    fun ftpConnector(vararg connectors: FTPClientStub) {
        ftpConnectors.addAll(connectors)
    }

    fun assert(block: (List<OutputItem>) -> Unit) {
        assertBlock = block
    }

    private fun findConnector(connection: FTPConnection): FTPConnector =
        ftpConnectors.find { stub -> stub.connection == connection }
            ?: error("An FTP connector was requested that was not defined in the test.")

    fun execute() {
        var outputItems: List<OutputItem>? = null
        val input = ConfigInput(::findConnector) {
            from.yaml.string(yaml)
        }

        if (input.db != null && history.isNotEmpty()) {
            transaction(input.db) {
                FileSyncTable.batchInsert(history) { item ->
                    this[program] = item.program
                    this[hash] = item.hash()
                    this[name] = item.name
                }
            }
        }

        val outputGateway = OutputGateway { outputItems = it }

        FileSync(input, outputGateway).sync()

        if (input.db != null && history.isNotEmpty()) {
            transaction(input.db) {
                FileSyncTable.deleteAll()
            }
        }

        val result = outputItems
        result.shouldNotBeNull()
        assertBlock(result)
    }
}

fun fileSyncTest(block: TestHarness.() -> Unit) {
    val harness = TestHarness()
    harness.block()
    harness.execute()
}

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
            result shouldMatchItems listOf(item)
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
            result shouldMatchItems listOf(item1, item2)
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
            result shouldMatchItems listOf(item1, item2)
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
            result shouldMatchItems listOf(item1)
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
                OutputItem(item1, item1.name),
                OutputItem(item2, item2.name),
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
                OutputItem(item, item.name, "mp3"),
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
                OutputItem(item, "testfile", "flac"),
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
                OutputItem(item, "testfile", "flac"),
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
                OutputItem(item, "new file name", "wav"),
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
                OutputItem(item, "testfile", "wav", tags),
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
                    OutputItem(item, "testfile", "mp3", tags),
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
                    OutputItem(item, "OLD testfile"),
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
                    OutputItem(item, "testfile", "mp3", tags),
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
                    OutputItem(item, "the real title", format = "flac"),
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
                results shouldMatch listOf(OutputItem(item, "2023-03-02"))
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
                results shouldMatch listOf(OutputItem(item, "2023-03-02 23-03-02"))
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
                    .map { OutputItem(it, it.name) }
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
                results shouldMatch listOf(OutputItem(item, item.name))
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
    fun `should create table if it does not exist`() {
        fileSyncTest {
            val dbUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
            config(
                """
                fileSync:
                  history:
                    db:
                      url: $dbUrl
                      user: ""
                      password: ""
                  programs:
                    program:
                      source:
                        type: Empty
                 """.trimIndent()
            )

            assert {
                val db = Database.connect(dbUrl)
                val results = transaction(db) {
                    FileSyncTable.selectAll().toList()
                }
                results.shouldBeEmpty()
            }
        }
    }

    @Test
    fun `should not download already downloaded files`() {
        fileSyncTest {
            val dbUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
            config(
                """
                fileSync:
                  history:
                    db:
                      url: $dbUrl
                      user: ""
                      password: ""
                  programs:
                    program:
                      source:
                        type: FTP
                        url: fake.url
                 """.trimIndent()
            )

            val item1 = MemoryItem("program", "file 1")
            val item2 = MemoryItem("program", "file 2", data = "file data".toByteArray())
            val item3 = MemoryItem("program", "file 3")
            val item4 = MemoryItem("program", "file 4", data = "needs different data".toByteArray())

            ftpConnector("fake.url", item1, item2, item3, item4)
            history(item2, item4)

            assert { results ->
                results shouldMatch listOf(
                    OutputItem(item1, item1.name),
                    OutputItem(item3, item3.name),
                )
            }
        }
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
            results shouldMatch listOf(
                OutputItem(item2, item2.name),
                OutputItem(item1, item1.name),
            )
        }
    }
}
