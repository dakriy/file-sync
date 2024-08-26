package org.klrf.filesync

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.klrf.filesync.domain.Item
import org.klrf.filesync.domain.OutputItem


infix fun Item.shouldMatch(item: Item) {
    program shouldBe item.program
    name shouldBe item.name
    data() shouldBe item.data()
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
    this.item.data() shouldBe o.data
}

infix fun Collection<OutputItem>.shouldMatch(items: Collection<TestOutputItem>) {
    this.size shouldBe items.size
    zip(items).forEach { (actual, expected) ->
        actual shouldMatch expected
    }
}

@JvmName("shouldMatchItems")
infix fun Collection<OutputItem>.shouldMatch(items: Collection<Item>) {
    zip(items).forEach { (actual, expected) ->
        actual shouldMatch expected
    }
}

fun fileSyncTest(block: TestHarness.() -> Unit) {
    val harness = TestHarness()
    harness.block()
    harness.execute()
}
