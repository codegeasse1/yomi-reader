package eu.kanade.tachiyomi.ui.browse.novel.source.browse

import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class BrowseNovelSourceListingTest {

    @Test
    fun `valueOf uses popular listing for null query`() {
        BrowseNovelSourceScreenModel.Listing.valueOf(null)
            .shouldBeInstanceOf<BrowseNovelSourceScreenModel.Listing.Popular>()
    }

    @Test
    fun `applying filters from popular listing switches to blank search listing`() {
        val filters = NovelFilterList(object : NovelFilter.CheckBox("Completed", true) {})

        val listing = BrowseNovelSourceScreenModel.Listing.Popular.withAppliedNovelFilters(filters)

        listing.query shouldBe null
        listing.filters.hashCode() shouldBe filters.hashCode()
    }

    @Test
    fun `applying filters from latest listing switches to blank search listing`() {
        val filters = NovelFilterList(object : NovelFilter.Text("Keyword", "hero") {})

        val listing = BrowseNovelSourceScreenModel.Listing.Latest.withAppliedNovelFilters(filters)

        listing.query shouldBe null
        listing.filters.hashCode() shouldBe filters.hashCode()
    }

    @Test
    fun `copy novel filter state copies nested mutable state without sharing list instances`() {
        val sourceChild = object : NovelFilter.CheckBox("Completed", true) {}
        val source = NovelFilterList(
            object : NovelFilter.Text("Keyword", "hero") {},
            object : NovelFilter.Group<NovelFilter<*>>("Advanced", listOf(sourceChild)) {},
        )
        val destinationChild = object : NovelFilter.CheckBox("Completed", false) {}
        val destination = NovelFilterList(
            object : NovelFilter.Text("Keyword", "") {},
            object : NovelFilter.Group<NovelFilter<*>>("Advanced", listOf(destinationChild)) {},
        )

        copyNovelFilterState(source, destination)

        (destination[0] as NovelFilter.Text).state shouldBe "hero"
        destinationChild.state shouldBe true
        sourceChild.state = false
        destinationChild.state shouldBe true
    }

    @Test
    fun `copy novel filter state merges latest visible filters into full filter list`() {
        val visible = NovelFilterList(object : NovelFilter.Text("Keyword", "hero") {})
        val sort = object : NovelFilter.Sort("Sort", arrayOf("Popular", "Latest"), null) {}
        val destinationText = object : NovelFilter.Text("Keyword", "") {}
        val destination = NovelFilterList(sort, destinationText)

        copyNovelFilterState(visible, destination)

        destinationText.state shouldBe "hero"
        destination.any { it is NovelFilter.Sort } shouldBe true
    }

    @Test
    fun `copy novel filter state copies state correctly even if filters are out of order`() {
        val source = NovelFilterList(
            object : NovelFilter.CheckBox("Checkbox B", true) {},
            object : NovelFilter.CheckBox("Checkbox A", true) {},
        )
        val cbA = object : NovelFilter.CheckBox("Checkbox A", false) {}
        val cbB = object : NovelFilter.CheckBox("Checkbox B", false) {}
        val destination = NovelFilterList(cbA, cbB)

        copyNovelFilterState(source, destination)

        cbA.state shouldBe true
        cbB.state shouldBe true
    }

    @Test
    fun `visible filters hide sort in latest listing`() {
        val sort = object : NovelFilter.Sort(
            name = "Sort",
            values = arrayOf("Popular", "Latest"),
            state = NovelFilter.Sort.Selection(index = 0, ascending = true),
        ) {}
        val text = object : NovelFilter.Text("Keyword", "") {}
        val filters = NovelFilterList(sort, text)

        val visible = visibleNovelFiltersForListing(
            listing = BrowseNovelSourceScreenModel.Listing.Latest,
            filters = filters,
        )

        visible.any { it is NovelFilter.Sort } shouldBe false
        visible.any { it is NovelFilter.Text } shouldBe true
    }

    @Test
    fun `visible filters keep sort outside latest listing`() {
        val sort = object : NovelFilter.Sort(
            name = "Sort",
            values = arrayOf("Popular", "Latest"),
            state = NovelFilter.Sort.Selection(index = 0, ascending = true),
        ) {}
        val filters = NovelFilterList(sort)

        val visible = visibleNovelFiltersForListing(
            listing = BrowseNovelSourceScreenModel.Listing.Popular,
            filters = filters,
        )

        visible.any { it is NovelFilter.Sort } shouldBe true
    }

    @Test
    fun `visible filters hide nested sort in latest listing`() {
        val nestedSort = object : NovelFilter.Sort(
            name = "Sort",
            values = arrayOf("Popular", "Latest"),
            state = NovelFilter.Sort.Selection(index = 0, ascending = true),
        ) {}
        val nestedText = object : NovelFilter.Text("Keyword", "") {}
        val group = object : NovelFilter.Group<NovelFilter<*>>(
            name = "Advanced",
            state = listOf(nestedSort, nestedText),
        ) {}
        val filters = NovelFilterList(group)

        val visible = visibleNovelFiltersForListing(
            listing = BrowseNovelSourceScreenModel.Listing.Latest,
            filters = filters,
        )

        val visibleGroup = visible.first() as NovelFilter.Group<*>
        visibleGroup.state.any { it is NovelFilter.Sort } shouldBe false
        visibleGroup.state.any { it is NovelFilter.Text } shouldBe true
    }
}
