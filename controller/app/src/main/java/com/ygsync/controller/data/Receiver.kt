package com.ygsync.controller.data

data class Receiver(
    val id: String,
    val name: String,
    val address: String,
    val port: Int,
    val connected: Boolean = false,
    val latency: Long = 0L,
    val playbackPosition: Long = 0L
)
