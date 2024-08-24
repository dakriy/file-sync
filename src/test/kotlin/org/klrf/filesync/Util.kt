package org.klrf.filesync

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.klrf.filesync.domain.Item
import org.klrf.filesync.domain.OutputItem


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

fun fileSyncTest(block: TestHarness.() -> Unit) {
    val harness = TestHarness()
    harness.block()
    harness.execute()
}
