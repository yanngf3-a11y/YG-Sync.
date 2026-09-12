package com.ygsync.controller.youtube

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
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
        maxResults: Int = 60
    ): List<YgYouTubeResult> {

        return withContext(Dispatchers.IO) {

            initialize()

            val cleanQuery = query.trim()

            if (cleanQuery.isEmpty()) {
                return@withContext emptyList()
            }

            try {

                val service = ServiceList.YouTube

                val queryHandler =
                    service.getSearchQHFactory()
                        .fromQuery(cleanQuery)

                val searchInfo = SearchInfo.getInfo(
                    service,
                    queryHandler
                )

                val collected =
                    mutableListOf<StreamInfoItem>()

                collected.addAll(
                    searchInfo.relatedItems
                        .filterIsInstance<StreamInfoItem>()
                )

                var nextPage = searchInfo.nextPage
                var pagesFetched = 0

                /*
                 * YouTube devuelve de a ~20 resultados por
                 * página. Pedimos páginas extra hasta llegar
                 * a maxResults (o hasta 4 páginas de más, para
                 * no hacer esperar demasiado).
                 */
                while (
                    collected.size < maxResults &&
                    nextPage != null &&
                    pagesFetched < 4
                ) {

                    val morePage =
                        SearchInfo.getMoreItems(
                            service,
                            queryHandler,
                            nextPage
                        )

                    val moreItems =
                        morePage.items
                            .filterIsInstance<StreamInfoItem>()

                    if (moreItems.isEmpty()) {
                        break
                    }

                    collected.addAll(moreItems)
                    nextPage = morePage.nextPage
                    pagesFetched += 1
                }

                collected
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

    /**
     * Trae título, canal y miniatura de un videoId puntual.
     * Se usa cuando una pantalla cambió de video sola (por
     * autoplay/relacionados de SmartTube) y necesitamos
     * mostrar en el reproductor qué es lo que está sonando
     * en realidad, no lo último que se buscó.
     */
    suspend fun getVideoInfo(
        videoId: String
    ): YgYouTubeResult? {

        return withContext(Dispatchers.IO) {

            initialize()

            val cleanId = videoId.trim()

            if (cleanId.isEmpty()) {
                return@withContext null
            }

            try {

                val service = ServiceList.YouTube

                val streamInfo = StreamInfo.getInfo(
                    service,
                    "https://www.youtube.com/watch?v=$cleanId"
                )

                YgYouTubeResult(
                    videoId = cleanId,

                    title = streamInfo.name ?: "",

                    channelName =
                        streamInfo.uploaderName ?: "",

                    thumbnailUrl =
                        streamInfo.thumbnails
                            .firstOrNull()
                            ?.url
                            ?: "https://i.ytimg.com/vi/" +
                                "$cleanId/hqdefault.jpg",

                    duration =
                        if (streamInfo.duration > 0) {
                            formatDuration(streamInfo.duration)
                        } else {
                            ""
                        },

                    viewCount =
                        if (streamInfo.viewCount >= 0) {
                            streamInfo.viewCount.toString()
                        } else {
                            ""
                        },

                    publishedTime =
                        streamInfo.textualUploadDate ?: "",

                    description = ""
                )

            } catch (e: Exception) {

                /*
                 * Si no se puede traer la info (video
                 * privado/borrado, error de red, etc.) no
                 * cortamos nada: el reproductor simplemente
                 * se queda con lo que ya tenía.
                 */
                null
            }
        }
    }

    /**
     * Sugerencias de autocompletado (las mismas que muestra
     * YouTube mientras escribís, antes de buscar).
     */
    suspend fun suggestQueries(
        query: String
    ): List<String> {

        return withContext(Dispatchers.IO) {

            initialize()

            val cleanQuery = query.trim()

            if (cleanQuery.isEmpty()) {
                return@withContext emptyList()
            }

            try {

                val service = ServiceList.YouTube

                service.suggestionExtractor
                    ?.suggestionList(cleanQuery)
                    ?: emptyList()

            } catch (e: Exception) {

                /*
                 * Las sugerencias son un extra, no algo
                 * crítico: si fallan, simplemente no se
                 * muestran (no interrumpe la búsqueda normal).
                 */
                emptyList()
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
