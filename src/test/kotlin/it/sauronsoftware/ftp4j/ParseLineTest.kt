package it.sauronsoftware.ftp4j

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ParseLineTest {
    @Test
    fun `given line with semicolon in name, the line is properly parsed`() {
        val line =
            "type=file;size=28757;modify=20251105200249;UNIX.mode=0644;UNIX.uid=1012;UNIX.gid=1014;unique=fd02g878d32; Promo Dec 1 - 5, 2025 Don’t Do Something; Just Stand There! D10066-10070.docx"

        parseLine(line) shouldBe listOf(
            "type=file",
            "size=28757",
            "modify=20251105200249",
            "UNIX.mode=0644",
            "UNIX.uid=1012",
            "UNIX.gid=1014",
            "unique=fd02g878d32",
            "Promo Dec 1 - 5, 2025 Don’t Do Something; Just Stand There! D10066-10070.docx",
        )
    }
}