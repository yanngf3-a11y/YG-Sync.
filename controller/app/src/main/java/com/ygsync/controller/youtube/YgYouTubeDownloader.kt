package com.ygsync.controller.youtube

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.downloader.Downloader
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
        request: org.schabi.newpipe.extractor.downloader.Request
    ): Response {

        val builder = Request.Builder()
            .url(request.url())

        request.headers().forEach { (name, values) ->
            values.forEach { value ->
                builder.addHeader(name, value)
            }
        }

        val method = request.httpMethod()

        if (method.equals("POST", ignoreCase = true)) {
            builder.post(
                okhttp3.RequestBody.create(
                    null,
                    request.dataToSend() ?: ByteArray(0)
                )
            )
        } else {
            builder.get()
        }

        val response = client
            .newCall(builder.build())
            .execute()

        val body = response.body?.string() ?: ""

        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            body,
            response.request.url.toString()
        )
    }
}
