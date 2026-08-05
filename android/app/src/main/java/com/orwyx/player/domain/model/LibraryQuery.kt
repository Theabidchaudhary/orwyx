package com.orwyx.player.domain.model

/**
 * Sort key for library listings, matching the fields exposed in the sort/view
 * sheet. Folder listings only honor the subset that makes sense for a folder
 * (title, date, size, path); the rest apply to videos within a folder.
 */
enum class SortBy(val label: String) {
    TITLE("Title"),
    DATE("Date"),
    PLAYED_TIME("Played time"),
    STATUS("Status"),
    LENGTH("Length"),
    SIZE("Size"),
    RESOLUTION("Resolution"),
    PATH("Path"),
    FRAME_RATE("Frame rate"),
    TYPE("Type"),
    ;

    /** Whether this key produces a meaningful order for a folder (rather than video) listing. */
    val appliesToFolders: Boolean
        get() = this == TITLE || this == DATE || this == SIZE || this == PATH
}

enum class SortDirection { ASCENDING, DESCENDING }

/** List vs. grid presentation, shared by folder and video listings. */
enum class LibraryLayout { LIST, GRID }

/** Metadata fields a video card may display; persisted as a set of [key]s. */
enum class VideoCardField(val key: String, val label: String) {
    THUMBNAIL("thumbnail", "Thumbnail"),
    LENGTH("length", "Length"),
    FILE_EXTENSION("fileExtension", "File extension"),
    PLAYED_TIME("playedTime", "Played time"),
    RESOLUTION("resolution", "Resolution"),
    FRAME_RATE("frameRate", "Frame rate"),
    PATH("path", "Path"),
    SIZE("size", "Size"),
    DATE("date", "Date"),
    ;

    companion object {
        val DEFAULT_ENABLED: Set<String> = setOf(
            THUMBNAIL.key, LENGTH.key, RESOLUTION.key, FRAME_RATE.key, SIZE.key, DATE.key,
        )
    }
}

/** Mutually-exclusive quick filters for videos within a folder. */
enum class VideoFilter(val label: String) {
    ALL("All"),
    HDR("HDR"),
    SDR("SDR"),
    FAVORITES("Favorites"),
    RECENTLY_ADDED("Recently added"),
    RECENTLY_PLAYED("Recently played"),
}

/**
 * Complete description of a library listing. The repository translates this into an
 * indexed SQL query, so sorting/filtering scales to very large libraries.
 *
 * [sortBy] and [direction] are sourced from the global, persisted display
 * settings so every folder shares the same sort until the user changes it.
 */
data class LibraryQuery(
    val search: String = "",
    val folderPath: String? = null,
    val filter: VideoFilter = VideoFilter.ALL,
    val sortBy: SortBy = SortBy.DATE,
    val direction: SortDirection = SortDirection.DESCENDING,
    val includePrivate: Boolean = false,
)
