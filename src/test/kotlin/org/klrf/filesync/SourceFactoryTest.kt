package org.klrf.filesync

import com.uchuhimo.konf.source.yaml
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import org.intellij.lang.annotations.Language
import org.klrf.filesync.gateways.*

class SourceFactoryTest {
    private fun inputTest(@Language("YAML") yaml: String, block: ConfigInput.() -> Unit = {}) {
        val input = ConfigInput(DefaultSourceFactory, { EmptyOutputGateway }) {
            from.yaml.string(yaml)
        }

        input.block()
    }

    @Test
    fun `should have empty source as default`() =
        inputTest(
            """
            fileSync:
              programs:
                - name: ail
            """.trimIndent()
        ) {
            programs().first().source shouldBe EmptySource
        }

    @Test
    fun `should have empty source if it is specified`() =
        inputTest(
            """
            fileSync:
              programs:
                - name: ail
                  source:
                    type: Empty
            """.trimIndent()
        ) {
            programs().first().source shouldBe EmptySource
        }

    @Test
    fun `should error given ftp server without url`() {
        val ex = shouldThrow<IllegalStateException> {
            inputTest(
                """
            fileSync:
              programs:
                - name: programName
                  source:
                    type: FTP
            """.trimIndent()
            ) {
                programs().first().source
            }
        }

        ex.message shouldContain "The 'url' field is required for a FTP source."
    }

    @Test
    fun `should find ftp source given all the properties`() =
        inputTest(
            """
            fileSync:
              programs:
                - name: programName
                  source:
                    type: FTP
                    url: test.ftp.url
                    username: user
                    password: pass
                    path: /the/ftp/path
            """.trimIndent()
        ) {
            programs().first().source shouldBe FTPSource(
                "programName",
                FTPConnection(
                    url = "test.ftp.url",
                    username = "user",
                    password = "pass",
                    path = "/the/ftp/path",
                )
            )
        }

//    @Test
//    fun `should be able to download programs hosted on nextcloud`() = fileSyncTest {
//        config(
//            """
//                fileSync:
//                  programs:
//                    program:
//                      source:
//                        type: NextCloud
//                        url: fake.url
//                 """.trimIndent()
//        )
//
//        val item1 = MemoryItem("program", "file 1")
//        nextCloudConnector("fake.url", item1)
//        assert { results ->
//            results shouldMatch listOf(item1)
//        }
//    }
}