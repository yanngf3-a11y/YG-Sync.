package com.ygsync.controller.youtube

data class YgYouTubeSearch(
    val query: String,
    val results: List<YgYouTubeResult> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
