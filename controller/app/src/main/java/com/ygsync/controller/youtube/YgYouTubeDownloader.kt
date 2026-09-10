package com.ygsync.controller.youtube

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class YgYouTubeDownloader(
    private val context: Context
) : Downloader() {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    override fun execute(
        request: NewPipeRequest
    ): Response {

        val builder = Request.Builder()
            .url(request.url())

        request.httpMethod()
            ?.let { builder.method(it, null) }

        request.headers()
            .forEach { (name, values) ->
                values.forEach { value ->
                    builder.addHeader(name, value)
                }
            }

        val response = client.newCall(builder.build()).execute()

        val responseBody = response.body?.string() ?: ""

        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBody,
            response.request.url.toString()
        )
    }
}
