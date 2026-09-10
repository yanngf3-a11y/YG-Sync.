package com.ygsync.controller.youtube

class YgYouTubeRepository(
    private val engine: YgYouTubeEngine
) {

    suspend fun search(
        query: String,
        maxResults: Int = 20
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
}
