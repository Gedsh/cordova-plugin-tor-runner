package pan.alexander.cordova.torrunner.utils.web

import android.os.Build
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationRepository
import pan.alexander.cordova.torrunner.utils.Constants.CHROME_BROWSER_USER_AGENT
import pan.alexander.cordova.torrunner.utils.Constants.LOOPBACK_ADDRESS
import pan.alexander.cordova.torrunner.utils.logger.Logger.logi
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection.HTTP_OK
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.cancellation.CancellationException

private const val CONNECT_TIMEOUT_SEC = 180
private const val READ_TIMEOUT_SEC = 180

class HttpsConnectionManager @Inject constructor(
    private val configuration: ConfigurationRepository,
    private val dispatcherIo: CoroutineDispatcher
) {

    @Throws(IOException::class)
    suspend fun get(
        url: String,
        useTor: Boolean,
        block: suspend (inputStream: InputStream) -> Unit
    ) {

        val httpsURLConnection = getHttpsUrlConnection(url, useTor)

        try {
            httpsURLConnection.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", CHROME_BROWSER_USER_AGENT)
                connectTimeout = 1000 * CONNECT_TIMEOUT_SEC
                readTimeout = 1000 * READ_TIMEOUT_SEC
            }.connect()

            val response = httpsURLConnection.responseCode
            if (response == HTTP_OK) {
                block(httpsURLConnection.inputStream)
            } else {
                throw IOException("HttpsConnectionManager $url response code $response")
            }
        } finally {
            httpsURLConnection.disconnect()
        }
    }

    @Throws(IOException::class)
    suspend fun get(url: String, data: Map<String, String>, useTor: Boolean): List<String> =
        withContext(dispatcherIo) {

            val query = mapToQuery(data)

            val httpsURLConnection = getHttpsUrlConnection("$url?$query", useTor)

            try {
                httpsURLConnection.apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", CHROME_BROWSER_USER_AGENT)
                    connectTimeout = 1000 * CONNECT_TIMEOUT_SEC
                    readTimeout = 1000 * READ_TIMEOUT_SEC
                }.connect()

                val response = httpsURLConnection.responseCode
                if (response == HTTP_OK) {
                    mutableListOf<String>().also { lines ->
                        httpsURLConnection.inputStream.bufferedReader().useLines {
                            it.forEach { line ->
                                if (!isActive) {
                                    return@forEach
                                }
                                lines.add(line)
                            }
                        }
                    }
                } else {
                    throw IOException("HttpsConnectionManager $url response code $response")
                }
            } finally {
                httpsURLConnection.disconnect()
            }
        }

    @Throws(IOException::class)
    suspend fun post(
        url: String,
        data: Map<String, String>,
        useTor: Boolean,
        block: suspend (inputStream: InputStream) -> Unit
    ) {

        val httpsURLConnection = getHttpsUrlConnection(url, useTor)

        try {
            val query = mapToQuery(data)

            httpsURLConnection.apply {
                requestMethod = "POST"
                setRequestProperty("User-Agent", CHROME_BROWSER_USER_AGENT)
                setRequestProperty(
                    "Content-Length",
                    query.toByteArray().size.toString()
                )
                doOutput = true
                connectTimeout = 1000 * CONNECT_TIMEOUT_SEC
                readTimeout = 1000 * READ_TIMEOUT_SEC
            }.connect()

            httpsURLConnection.outputStream.bufferedWriter().use {
                it.write(query)
                it.flush()
            }

            val response = httpsURLConnection.responseCode
            if (response == HTTP_OK) {
                block(httpsURLConnection.inputStream)
            } else {
                throw IOException("HttpsConnectionManager $url response code $response")
            }
        } finally {
            httpsURLConnection.disconnect()
        }

    }

    @Throws(IOException::class)
    fun post(url: String, data: Map<String, String>, useTor: Boolean): List<String> {

        val httpsURLConnection = getHttpsUrlConnection(url, useTor)

        val lines = try {
            val query = mapToQuery(data)

            httpsURLConnection.apply {
                requestMethod = "POST"
                setRequestProperty("User-Agent", CHROME_BROWSER_USER_AGENT)
                setRequestProperty(
                    "Content-Length",
                    query.toByteArray().size.toString()
                )
                doOutput = true
                connectTimeout = 1000 * CONNECT_TIMEOUT_SEC
                readTimeout = 1000 * READ_TIMEOUT_SEC
            }.connect()

            httpsURLConnection.outputStream.bufferedWriter().use {
                it.write(query)
                it.flush()
            }

            val response = httpsURLConnection.responseCode
            if (response == HTTP_OK) {
                mutableListOf<String>().also { lines ->
                    httpsURLConnection.inputStream.bufferedReader().useLines {
                        it.forEach { line ->
                            if (!Thread.currentThread().isInterrupted) {
                                lines.add(line)
                            } else {
                                throw CancellationException(
                                    "HttpsConnectionManager post $url is cancelled"
                                )
                            }
                        }
                    }
                }
            } else {
                throw IOException("HttpsConnectionManager $url response code $response")
            }

        } finally {
            httpsURLConnection.disconnect()
        }

        return lines
    }

    fun getHttpsUrlConnection(url: String, useTor: Boolean): HttpsURLConnection {

        val urlConnection = URL(url)

        val httpsURLConnection = if (useTor) {

            val proxy = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                logi("Using tor http proxy for url connection")
                Proxy(
                    Proxy.Type.HTTP,
                    InetSocketAddress(
                        LOOPBACK_ADDRESS, configuration.getTorHttpPort()
                    )
                )
            } else {
                logi("Using tor socks proxy for url connection")
                Proxy(
                    Proxy.Type.SOCKS,
                    InetSocketAddress(
                        LOOPBACK_ADDRESS, configuration.getTorSocksPort()
                    )
                )
            }

            urlConnection.openConnection(proxy) as HttpsURLConnection
        } else {
            urlConnection.openConnection() as HttpsURLConnection
        }

        return httpsURLConnection
    }

    private fun mapToQuery(data: Map<String, String>) = data.entries.joinToString("&") {
        "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
    }
}
