package com.ygsync.controller.youtube

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class YgYouTubeEngine(
    private val context: Context
) {

    private var initialized = false

    private fun initialize() {
        if (initialized) return

        val downloader = YgYouTubeDownloader(context)

        NewPipe.init(downloader)

        initialized = true
    }

    suspend fun search(
        query: String,
        maxResults: Int = 20
    ): List<YgYouTubeResult> {

        return withContext(Dispatchers.IO) {

            initialize()

            val cleanQuery = query.trim()

            if (cleanQuery.isEmpty()) {
                return@withContext emptyList()
            }

            try {

                val service = ServiceList.YouTube

                val searchInfo = SearchInfo.getInfo(
                    service,
                    service.getSearchQHFactory()
                        .fromQuery(cleanQuery)
                )

                searchInfo.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .take(maxResults)
                    .map { item ->

                        YgYouTubeResult(
                            videoId = extractVideoId(item.url),

                            title = item.name,

                            channelName =
                                item.uploaderName ?: "",

                            thumbnailUrl =
                                item.thumbnails
                                    .firstOrNull()
                                    ?.url
                                    ?: "",

                            duration =
                                if (item.duration > 0) {
                                    formatDuration(item.duration)
                                } else {
                                    ""
                                },

                            viewCount =
                                if (item.viewCount >= 0) {
                                    item.viewCount.toString()
                                } else {
                                    ""
                                },

                            publishedTime =
                                item.textualUploadDate ?: "",

                            description = ""
                        )
                    }

            } catch (e: Exception) {

                throw Exception(
                    e.message
                        ?: "No se pudo realizar la búsqueda de YouTube",
                    e
                )
            }
        }
    }

    private fun extractVideoId(url: String): String {

        return try {

            val queryPart =
                url.substringAfter("watch?v=", "")

            if (queryPart.isNotEmpty()) {
                queryPart.substringBefore("&")
            } else {
                url.substringAfterLast("/")
                    .substringBefore("?")
                    .substringBefore("&")
            }

        } catch (_: Exception) {
            ""
        }
    }

    private fun formatDuration(
        seconds: Long
    ): String {

        if (seconds <= 0) {
            return ""
        }

        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60

        return if (hours > 0) {

            String.format(
                "%d:%02d:%02d",
                hours,
                minutes,
                remainingSeconds
            )

        } else {

            String.format(
                "%d:%02d",
                minutes,
                remainingSeconds
            )
        }
    }
}
