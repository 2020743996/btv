package com.example.iptvplayer

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class NetworkCallTest {
    @Test
    fun cancellationClosesCallDuringSlowBody() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("slow body").setBodyDelay(3, TimeUnit.SECONDS))
            val call = OkHttpClient().newCall(Request.Builder().url(server.url("/")).build())
            val job = launch { call.readCancellable { it.body!!.string() } }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            withTimeout(1_000) { job.cancelAndJoin() }
            assertTrue(call.isCanceled())
        }
    }

    @Test
    fun totalTimeoutStopsSlowResponse() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("slow body").setBodyDelay(2, TimeUnit.SECONDS))
            val call = OkHttpClient.Builder().callTimeout(150, TimeUnit.MILLISECONDS).build()
                .newCall(Request.Builder().url(server.url("/")).build())
            val result = runCatching { withTimeout(1_500) { call.readCancellable { it.body!!.string() } } }
            assertTrue(result.exceptionOrNull() is IOException)
            assertTrue(call.isCanceled())
        }
    }
}
