package com.ygsync.controller.youtube

data class YgYouTubeResult(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val duration: String = "",
    val viewCount: String = "",
    val publishedTime: String = "",
    val description: String = ""
)
