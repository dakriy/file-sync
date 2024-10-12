package com.persignum.filesync

import com.persignum.filesync.gateways.*
import com.uchuhimo.konf.source.yaml
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import org.intellij.lang.annotations.Language

class SourceFactoryTest {
    private fun inputTest(@Language("YAML") yaml: String, block: ConfigInput.() -> Unit = {}) {
        val input = ConfigInput(DefaultSourceFactory, { _, _ -> EmptyOutputGateway }) {
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
            programs().first().source shouldBe EmptySource("")
        }

    @Test
    fun `should have empty source if it is specified`() =
        inputTest(
            """
            fileSync:
              sources:
                - name: "empty"
                  type: Empty
              programs:
                - name: ail
                  source:
                    name: "empty"
            """.trimIndent()
        ) {
            programs().first().source shouldBe EmptySource("empty")
        }

    @Test
    fun `should error given ftp server without url`() {
        val ex = shouldThrow<IllegalStateException> {
            inputTest(
                """
            fileSync:
              sources:
                - name: src
                  type: FTP
              programs:
                - name: programName
                  source:
                    name: src
            """.trimIndent()
            ) {
                programs().first().source
            }
        }

        ex.message shouldContain "The 'url' field is required for the FTP source 'src'."
    }

    @Test
    fun `should find ftp source given all the properties`() =
        inputTest(
            """
            fileSync:
              sources:
                - name: src
                  type: FTP
                  url: test.ftp.url
                  username: user
                  password: pass
              programs:
                - name: programName
                  source:
                    name: src
                    path: /the/ftp/path
            """.trimIndent()
        ) {
            programs().first().source shouldBe FTPSource(
                "src",
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
              sources:
                - name: bruh
                  type: NextCloud
                  url: my.nexcloud.instance
                  username: user
                  password: pass
              programs:
                - name: nextCloud
                  source:
                    name: bruh
                    path: /the/nextcloud/path
                    depth: 10
            """.trimIndent()
        ) {
            programs().first().source shouldBe NextCloudSource(
                name = "bruh",
                url = "my.nexcloud.instance",
                path = "/the/nextcloud/path",
                username = "user",
                password = "pass",
                depth = 10,
            )
        }

    @Test
    fun `url is required for nextcloud`() {
        val ex = shouldThrow<IllegalStateException> {
            inputTest(
                """
            fileSync:
              sources:
                - name: nextCloud
                  type: NextCloud
                  username: user
                  password: pass
              programs:
                - name: nextCloud
                  source:
                    name: nextCloud
                    path: /the/nextcloud/path
                    depth: 10
            """.trimIndent()
            ) {
                programs().first().source
            }
        }

        ex.message shouldBe "The 'url' field is required for the NextCloud source 'nextCloud'."
    }

    @Test
    fun `path is required for NextCloud`() {
        val ex = shouldThrow<IllegalStateException> {
            inputTest(
                """
            fileSync:
              sources:
                - name: next
                  type: NextCloud
                  url: my.nexcloud.instance
                  username: user
                  password: pass
              programs:
                - name: nextCloud
                  source:
                    name: next
            """.trimIndent()
            ) {
                programs().first().source
            }
        }

        ex.message shouldBe "The 'path' field is required for the NextCloud source on program 'nextCloud'."
    }

    @Test
    fun `username is required for NextCloud`() {
        val ex = shouldThrow<IllegalStateException> {
            inputTest(
                """
            fileSync:
              sources:
                - name: src
                  type: NextCloud
                  url: my.nexcloud.instance
                  password: pass
              programs:
                - name: nextCloud
                  source:
                    name: src
                    path: /the/nextcloud/path
            """.trimIndent()
            ) {
                programs().first().source
            }
        }

        ex.message shouldBe "The 'username' field is required for the NextCloud source 'src'."
    }

    @Test
    fun `custom sources are resolved`() =
        inputTest(
            """
            fileSync:
              sources:
                - name: custom
                  type: Custom
                  class: ${CustomSource::class.java.name}
              programs:
                - name: my special program
                  source:
                    name: custom
            """.trimIndent()
        ) {
            programs().first().source shouldBe CustomSource(
                "custom",
                SourceSpec(
                    name = "custom",
                    type = SourceType.Custom,
                    `class` = CustomSource::class.java.name,
                ),
                SourceImplSpec(name = "custom")
            )
        }

    @Test
    fun `clazz field is required for custom source`() {
        val ex = shouldThrow<IllegalStateException> {
            inputTest(
                """
                fileSync:
                  sources:
                    - name: special
                      type: Custom
                  programs:
                    - name: idk
                      source:
                        name: special
                """.trimIndent()
            ) {
                programs().first().source
            }
        }

        ex.message shouldBe "The 'class' field is required for the Custom source 'special'."
    }
}
