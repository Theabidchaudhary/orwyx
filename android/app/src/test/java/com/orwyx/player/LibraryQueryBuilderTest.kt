package com.orwyx.player

import com.google.common.truth.Truth.assertThat
import com.orwyx.player.data.db.LibraryQueryBuilder
import com.orwyx.player.domain.model.LibraryQuery
import com.orwyx.player.domain.model.SortBy
import com.orwyx.player.domain.model.SortDirection
import com.orwyx.player.domain.model.VideoFilter
import org.junit.Test

class LibraryQueryBuilderTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `default query excludes private and stale rows`() {
        val sql = LibraryQueryBuilder.build(LibraryQuery(), emptySet(), now).sql

        assertThat(sql).contains("presentInLastScan = 1")
        assertThat(sql).contains("isPrivate = 0")
        assertThat(sql).contains("ORDER BY dateAddedMs DESC")
    }

    @Test
    fun `search binds a like argument instead of concatenating`() {
        val query = LibraryQueryBuilder.build(
            LibraryQuery(search = "50% off_deal"),
            emptySet(),
            now,
        )

        assertThat(query.sql).contains("title LIKE ? ESCAPE")
        // Wildcards in user input must arrive escaped.
        assertThat(query.sql).doesNotContain("50%")
    }

    @Test
    fun `hidden folders are excluded outside folder view`() {
        val sql = LibraryQueryBuilder
            .build(LibraryQuery(), setOf("/dcim/a", "/dcim/b"), now)
            .sql

        assertThat(sql).contains("folderPath NOT IN (?,?)")
    }

    @Test
    fun `folder query pins to the folder and ignores hidden set`() {
        val sql = LibraryQueryBuilder
            .build(LibraryQuery(folderPath = "/movies"), setOf("/movies"), now)
            .sql

        assertThat(sql).contains("folderPath = ?")
        assertThat(sql).doesNotContain("NOT IN")
    }

    @Test
    fun `filters map to indexed predicates`() {
        val hdr = LibraryQueryBuilder.build(LibraryQuery(filter = VideoFilter.HDR), emptySet(), now).sql
        val fav = LibraryQueryBuilder.build(LibraryQuery(filter = VideoFilter.FAVORITES), emptySet(), now).sql

        assertThat(hdr).contains("hdrType != 'NONE'")
        assertThat(fav).contains("isFavorite = 1")
    }

    @Test
    fun `every sort key produces an order by clause`() {
        SortBy.entries.forEach { sort ->
            val sql = LibraryQueryBuilder
                .build(LibraryQuery(sortBy = sort, direction = SortDirection.ASCENDING), emptySet(), now)
                .sql
            assertThat(sql).contains("ORDER BY")
            assertThat(sql).contains("ASC")
        }
    }
}
