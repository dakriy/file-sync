package it.sauronsoftware.ftp4j

fun parseLine(line: String): List<String> {
    val (factString, name) = line.split(' ', limit = 2)
    val facts = factString.trim(';').split(";")

    return facts + name
}
