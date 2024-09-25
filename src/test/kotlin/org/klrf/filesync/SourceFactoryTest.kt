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
                    port = 21,
                )
            )
        }

    @Test
    fun `should resolve nextcloud source`() =
        inputTest(
            """
            fileSync:
              programs:
                - name: nextCloud
                  source:
                    type: NextCloud
                    url: my.nexcloud.instance
                    username: user
                    password: pass
                    path: /the/nextcloud/path
                    depth: 10
            """.trimIndent()
        ) {
            programs().first().source shouldBe NextCloudSource(
                url = "my.nexcloud.instance",
                path = "/the/nextcloud/path",
                username = "user",
                password = "pass",
                depth = 10,
                program = "nextCloud",
            )
        }

    @Test
    fun `url is required for nextcloud`() {
        val ex = shouldThrow<IllegalStateException> {
            inputTest(
                """
            fileSync:
              programs:
                - name: nextCloud
                  source:
                    type: NextCloud
                    username: user
                    password: pass
                    path: /the/nextcloud/path
                    depth: 10
            """.trimIndent()
            ) {
                programs().first().source
            }
        }

        ex.message shouldBe "The 'url' field is required for a NextCloud source."
    }

    @Test
    fun `path is required for NextCloud`() {
        val ex = shouldThrow<IllegalStateException> {
            inputTest(
                """
            fileSync:
              programs:
                - name: nextCloud
                  source:
                    type: NextCloud
                    url: my.nexcloud.instance
                    username: user
                    password: pass
                    depth: 10
            """.trimIndent()
            ) {
                programs().first().source
            }
        }

        ex.message shouldBe "The 'path' field is required for a NextCloud source."
    }

    @Test
    fun `username is required for NextCloud`() {
        val ex = shouldThrow<IllegalStateException> {
            inputTest(
                """
            fileSync:
              programs:
                - name: nextCloud
                  source:
                    type: NextCloud
                    url: my.nexcloud.instance
                    password: pass
                    path: /the/nextcloud/path
                    depth: 10
            """.trimIndent()
            ) {
                programs().first().source
            }
        }

        ex.message shouldBe "The 'username' field is required for a NextCloud source."
    }

    @Test
    fun `custom sources are resolved`() =
        inputTest(
            """
            fileSync:
              programs:
                - name: my special program
                  source:
                    type: Custom
                    class: org.klrf.filesync.CustomSource
            """.trimIndent()
        ) {
            programs().first().source shouldBe CustomSource(
                "my special program",
                SourceSpec(
                    type = SourceType.Custom,
                    `class` = "org.klrf.filesync.CustomSource",
                )
            )
        }

    @Test
    fun `class field is required for custom source`() {
        val ex = shouldThrow<IllegalStateException> {
            inputTest(
                """
                fileSync:
                  programs:
                    - name: nextCloud
                      source:
                        type: Custom
                """.trimIndent()
            ) {
                programs().first().source
            }
        }

        ex.message shouldBe "The 'class' field is required for a Custom source."
    }
}
