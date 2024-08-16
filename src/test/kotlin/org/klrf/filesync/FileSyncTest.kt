package org.klrf.filesync

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import com.uchuhimo.konf.Feature
import com.uchuhimo.konf.source.LoadException
import com.uchuhimo.konf.source.yaml
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.regex.PatternSyntaxException
import kotlin.test.Test
import org.intellij.lang.annotations.Language

//class FileOutput : Output {
//    override suspend fun save(items: List<OutputItem>) {
//        // write data to file
//        // file convert
//        // write ID3 tags
//        // audio normalization
//    }
//}

interface Item {
    val program: String

    val name: String

    fun data(): ByteArray

    fun computeFormatFromName(): String = if ('.' in name) {
        name.substringAfterLast('.')
    } else "mp3"

    fun nameAndExtension(): Pair<String, String> {
        val name = name.substringBeforeLast('.')
        val extension = computeFormatFromName()
        return name to extension
    }
}

data class ParsedItem(
    val item: Item,
    val captureGroups: Map<String, String> = emptyMap(),
    val dates: Map<String, LocalDate> = emptyMap(),
) : Item by item {
    private val nameAndExtension = nameAndExtension()

    val replacements = mapOf(
        "old_filename" to nameAndExtension.first,
        "old_extension" to nameAndExtension.second,
        "raw_filename" to item.name,
    ) + captureGroups

    private val dateFormatSignatures = dates.keys.map { "{$it:" }

    private fun String.replaceDate(found: Pair<Int, String>): String {
        val (index, foundStr) = found
        val foundName = foundStr.drop(1).dropLast(1)
        val full = substring(index).substringBefore('}')
        val format = full.substringAfter(':')
        val date = dates[foundName]!!
        val pattern = DateTimeFormatter.ofPattern(format)

        return replace("{$foundName:$format}", date.format(pattern))
    }

    fun interpolate(str: String): String {
        var dated = str
        var found: Pair<Int, String>?
        while (true) {
            found = dated.findAnyOf(dateFormatSignatures)
            if (found == null) break
            dated = dated.replaceDate(found)
        }

        return replacements.entries.fold(dated) { acc, (key, value) ->
            acc.replace("{$key}", value)
        }
    }
}

data class OutputItem(
    val item: Item,
    val fileName: String,
    val format: String = "mp3",
    val tags: Map<String, String> = emptyMap(),
)

fun interface OutputGateway {
    fun save(items: List<OutputItem>)
}

// Gateway that maps any kind of source to items
fun interface Source {
    fun listItems(): Sequence<Item>
}

object EmptySource : Source {
    override fun listItems(): Sequence<Item> = emptySequence()
}

interface FTPConnector {
    val connection: FTPConnection

    fun listFiles(): Sequence<String>
//    fun downloadFile(file: String): ByteArray
}

data class FTPConnection(
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val path: String? = null
)

class FTPSource(
    private val program: String,
    private val connector: FTPConnector,
) : Source {
    inner class FTPItem(
        override val name: String,
    ) : Item {
        override val program: String = this@FTPSource.program
        override fun data(): ByteArray = ByteArray(0)
    }

    override fun listItems(): Sequence<Item> {
        return connector.listFiles().map(::FTPItem)
    }
}

data class Parse(
    val regex: Regex,
    val dateWithParseFormat: Map<String, DateTimeFormatter> = emptyMap(),
) {
    val captureGroups by lazy {
        val captureGroupRegex = """\(\?<(\w+)>""".toRegex()
        captureGroupRegex.findAll(regex.toString()).map { it.groupValues[1] }.toList()
    }

    fun parse(item: Item): ParsedItem? {
        val result =
            regex.matchEntire(item.name) ?: return null

        val captureGroups = captureGroups.associateWith { captureGroupName ->
            result.groups[captureGroupName]?.value
                ?: error("Capture group '$captureGroupName' did not exist. Regex: '$regex' INPUT: ${item.name}.")
        }

        val dateGroups = dateWithParseFormat.mapValues { (name, format) ->
            val dateString = captureGroups[name]
                ?: error("Capture group '$name' does not exist in '$regex' for program '${item.program}'")

            LocalDate.parse(dateString, format)
        }

        return ParsedItem(item, captureGroups, dateGroups)
    }
}

data class Program(
    val name: String,
    val source: Source,
    val parse: Parse?,
    val output: Output?,
)

fun interface InputGateway {
    fun programs(): List<Program>
}

class FileSync(
    private val input: InputGateway,
    private val output: OutputGateway,
) {
    fun sync() {
        val items = input.programs().flatMap { program ->
            program.source.listItems()
                .mapNotNull { item ->
                    if (program.parse == null) ParsedItem(item)
                    else program.parse.parse(item)
                }
                .map { item ->
                    val (name, extension) = item.nameAndExtension()
                    val format = program.output?.format ?: extension
                    val filename = program.output?.filename ?: name

                    val tags = program.output?.tags ?: emptyMap()

                    val replacedTags = tags.mapValues { (_, value) -> item.interpolate(value) }
                    val replacedFileName = item.interpolate(filename)

                    OutputItem(item, replacedFileName, format, replacedTags)
                }
        }

        output.save(items)
    }
}

enum class SourceType {
    Empty,
    FTP,
//    NextCloud,
//    Custom,
}

data class SourceSpec(
    val type: SourceType,

    val url: String? = null,
    val username: String? = null,
    val password: String? = null,
    val path: String? = null,
//    val customSource: String? = null,
) {
    fun toFTPConnection() = FTPConnection(
        url ?: error("url is required for ftp source"),
        username,
        password,
        path,
    )
}

data class Output(
    val format: String? = null,
    val filename: String? = null,
    val tags: Map<String, String> = emptyMap(),
)

data class ParseSpec(
    val regex: String,
    val dates: Map<String, String> = emptyMap(),
) {
    fun toParse() =
        Parse(regex.toRegex(), dates.mapValues { (_, v) -> DateTimeFormatter.ofPattern(v) })
}

data class ProgramSpec(
    val source: SourceSpec,
    val parse: ParseSpec? = null,
    val output: Output? = null,
)

object FileSyncSpec : ConfigSpec() {
    val programs by optional<Map<String, ProgramSpec>>(emptyMap())
}

class ConfigInput(
    private val ftpConnectorFactory: (FTPConnection) -> FTPConnector,
    sourceConfig: Config.() -> Config,
) : InputGateway {
    val config = Config {
        addSpec(FileSyncSpec)
        enable(Feature.OPTIONAL_SOURCE_BY_DEFAULT)
    }
        .run(sourceConfig)
        .from.env()
        .from.systemProperties()

    override fun programs(): List<Program> {
        return config[FileSyncSpec.programs].map { (name, programSpec) ->
            val sourceConfig = programSpec.source
            val type = sourceConfig.type

            val source = when (type) {
                SourceType.Empty -> EmptySource
                SourceType.FTP -> {
                    val ftpConnection = sourceConfig.toFTPConnection()
                    FTPSource(name, ftpConnectorFactory(ftpConnection))
                }
            }

            val parse = programSpec.parse?.toParse()

            Program(name, source, parse, programSpec.output)
        }
    }
}

data class MemoryItem(
    override val program: String,
    override val name: String,
    val data: ByteArray = ByteArray(0),
) : Item {
    override fun data(): ByteArray = data

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MemoryItem

        if (program != other.program) return false
        if (name != other.name) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = program.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

class FTPClientStub(
    override val connection: FTPConnection,
    private val items: List<Item>,
) : FTPConnector {
    constructor(connection: FTPConnection, vararg items: Item) : this(connection, items.toList())

    override fun listFiles(): Sequence<String> {
        return items.asSequence().map(Item::name)
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
    map { it.copy(item = MemoryItem(it.item.program, it.item.name, it.item.data())) } shouldBe items
}

class TestHarness {
    private var yaml: String = ""
    private val ftpConnectors = mutableListOf<FTPClientStub>()
    private var assertBlock: (List<OutputItem>) -> Unit = { }

    fun config(@Language("YAML") yaml: String) {
        this.yaml = yaml
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

        val outputGateway = OutputGateway { outputItems = it }

        FileSync(input, outputGateway).sync()

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
}
