package com.filemanager.app.data.model

enum class SortField { NAME, SIZE, DATE_MODIFIED, TYPE }
enum class SortDirection { ASCENDING, DESCENDING }

data class SortOption(
    val field: SortField = SortField.NAME,
    val direction: SortDirection = SortDirection.ASCENDING
)

enum class ViewMode { LIST, GRID }

enum class CategoryFilter {
    ALL, IMAGES, VIDEOS, AUDIO, DOCUMENTS, ARCHIVES, APK, OTHER
}
