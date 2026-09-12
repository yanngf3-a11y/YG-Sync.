package com.ygsync.controller.youtube

class YgYouTubeRepository(
    private val engine: YgYouTubeEngine
) {

    suspend fun search(
        query: String,
        maxResults: Int = 60
    ): Result<List<YgYouTubeResult>> {

        return try {

            val cleanQuery = query.trim()

            if (cleanQuery.isEmpty()) {
                return Result.success(emptyList())
            }

            val results = engine.search(
                query = cleanQuery,
                maxResults = maxResults
            )

            Result.success(results)

        } catch (e: Exception) {

            Result.failure(e)
        }
    }

    suspend fun getVideoInfo(
        videoId: String
    ): YgYouTubeResult? {

        return try {
            engine.getVideoInfo(videoId)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun suggestQueries(
        query: String
    ): List<String> {

        return try {
            engine.suggestQueries(query)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
