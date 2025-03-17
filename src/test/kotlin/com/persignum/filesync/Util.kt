package com.persignum.filesync

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import com.persignum.filesync.domain.Item
import com.persignum.filesync.domain.OutputItem
import java.io.ByteArrayOutputStream

suspend fun Item.data(): ByteArray {
    val buffer = ByteArrayOutputStream()
    data(buffer)
    return buffer.toByteArray()
}

infix fun Item.shouldMatch(item: Item) {
    name shouldBe item.name
    runBlocking {
        data() shouldBe item.data()
    }
    createdAt shouldBe item.createdAt
}

class TestOutputItem(
    val path: String,
    val data: ByteArray = ByteArray(0),
    val tags: Map<String, String> = emptyMap(),
)

infix fun OutputItem.shouldMatch(o: TestOutputItem) {
    "$this.$format" shouldBe o.path
    tags shouldBe o.tags
    runBlocking {
        item.data() shouldBe o.data
    }
}

infix fun Collection<OutputItem>.shouldMatch(items: Collection<TestOutputItem>) {
    this.size shouldBe items.size
    zip(items).forEach { (actual, expected) ->
        actual shouldMatch expected
    }
}

@JvmName("shouldMatchItems")
infix fun Collection<OutputItem>.shouldMatch(items: Collection<Item>) {
    size shouldBe items.size
    zip(items).forEach { (actual, expected) ->
        actual shouldMatch expected
    }
}

fun fileSyncTest(block: TestHarness.() -> Unit) {
    val harness = TestHarness()
    harness.block()
    harness.execute()
}
