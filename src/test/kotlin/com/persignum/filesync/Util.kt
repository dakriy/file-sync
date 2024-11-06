package com.persignum.filesync

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import com.persignum.filesync.domain.Item
import com.persignum.filesync.domain.OutputItem


infix fun Item.shouldMatch(item: Item) {
    name shouldBe item.name
    runBlocking {
        data().readAllBytes() shouldBe item.data().readAllBytes()
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
        item.data().readAllBytes() shouldBe o.data
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
