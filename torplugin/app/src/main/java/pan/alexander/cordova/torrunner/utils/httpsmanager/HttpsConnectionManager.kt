package pan.alexander.cordova.torrunner.utils.httpsmanager

import android.os.Build
import android.webkit.WebResourceResponse
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import pan.alexander.cordova.torrunner.domain.core.CoreState
import pan.alexander.cordova.torrunner.domain.core.CoreStatus
import pan.alexander.cordova.torrunner.framework.webview.WebViewRequest
import pan.alexander.cordova.torrunner.utils.Constants.CHROME_BROWSER_USER_AGENT
import pan.alexander.cordova.torrunner.utils.Constants.LOOPBACK_ADDRESS
import pan.alexander.cordova.torrunner.utils.logger.Logger.loge
import pan.alexander.cordova.torrunner.utils.logger.Logger.logi
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class HttpsConnectionManager constructor(
    private val coreStatus: CoreStatus
) {

    private val client by lazy {
        OkHttpClient.Builder()
        .build()
    }

    fun fetchViaSocks(
        request: WebViewRequest
    ): WebResourceResponse? {
        return try {

            val connection = getHttpUrlConnection(request.url.toString())

            connection.requestMethod = request.method
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", CHROME_BROWSER_USER_AGENT)

            // Set request headers
            for ((key, value) in request.headers) {
                connection.setRequestProperty(key, value)
            }

            // If method has a body (POST, PUT), write it
            loge("METHOD: ${request.method} URL: ${request.url}")
            if (request.method.equals("POST", true)
                || request.method.equals("PUT", true)) {
                connection.doOutput = true
                val body = request.body.toByteArray(Charsets.UTF_8)
                connection.setRequestProperty("Content-Length", body.size.toString())
                connection.outputStream.use { it.write(body) }
            } else {
                connection.connect()
            }

            val contentType = if (request.url.endsWith(".mp4")) {
                "video/mp4"
            } else {
                connection.contentType ?: "application/octet-stream"
            }
            //val encoding = connection.contentEncoding ?: "utf-8"
            val encoding = if (request.url.endsWith(".mp4")) {
                "utf-8"
            } else {
                connection.contentEncoding
            }
            val mimeType = contentType.substringBefore(";").trim()

            val headersMap = mutableMapOf<String, String>()
            for ((key, value) in connection.headerFields) {
                if (key != null && value != null && value.isNotEmpty()) {
                    headersMap[key] = value.joinToString("; ")
                }
            }

            val bodyBytes = connection.inputStream.readBytes()// read all bytes into memory
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bodyBytes).joinToString("") { "%02x".format(it) }

            val bodyStream = ByteArrayInputStream(bodyBytes)
            loge(bodyBytes.size.toString())
            loge(hash)
            loge("head: ${bodyBytes.toHexSlice(0, 160)}")
            loge("tail: ${bodyBytes.toHexSlice(bodyBytes.size - 160, bodyBytes.size)}")

            WebResourceResponse(
                mimeType,
                encoding,
                connection.responseCode,
                connection.responseMessage,
                headersMap,
                bodyStream
            )
        } catch (e: IOException) {
            loge("HttpsConnectionManager fetchViaSocks", e)
            null
        }
    }

    fun getHttpUrlConnection(url: String): HttpURLConnection {

        val proxy = if ((coreStatus.torState == CoreState.RUNNING) && !url.endsWith(".mp4")) {
            //Use http proxy as older android versions use Socks4 proxy which leaks DNS
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                logi("Using tor http proxy for $url")
                Proxy(
                    Proxy.Type.HTTP,
                    InetSocketAddress(
                        LOOPBACK_ADDRESS, 8118
                    )
                )
            } else {
                logi("Using tor socks proxy for $url")
                Proxy(
                    Proxy.Type.SOCKS,
                    InetSocketAddress(
                        LOOPBACK_ADDRESS, 9051
                    )
                )
            }
        } else {
            logi("Using direct $url")
            null
        }

        val urlConnection = URL(url)

        val httpURLConnection = if (proxy == null) {
            urlConnection.openConnection() as HttpsURLConnection
        } else {
            urlConnection.openConnection(proxy) as HttpsURLConnection
        }

        if (true) {
            httpURLConnection.sslSocketFactory = trustAllSslSocketFactory()
            httpURLConnection.hostnameVerifier =
                HostnameVerifier { hostname: String, session: SSLSession ->
                    hostname == session.peerHost
                }
        }

        return httpURLConnection
    }

    fun trustAllSslSocketFactory(): SSLSocketFactory {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        return sslContext.socketFactory
    }

    fun fetchViaOkHttp(
        request: WebViewRequest
    ): WebResourceResponse? {
        return try {
            // SOCKS5 proxy (with remote DNS via hostname)
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 9051))

            val method = request.method.uppercase()
            val url = request.url.toString()

            // Convert request headers
            val headersBuilder = Headers.Builder()
            for ((k, v) in request.headers) {
                headersBuilder.add(k, v)
            }
            if (request.url.endsWith(".mp4")) {
                headersBuilder["Accept-Encoding"] = "identity"
                headersBuilder["priority"] = "u=1, i"
                headersBuilder["sec-fetch-dest"] = "empty"
                headersBuilder["sec-fetch-mode"] = "cors"
                headersBuilder["sec-fetch-site"] = "cross-site"
                headersBuilder["x-requested-with"] = "pocketnet.app"
            }

            val requestBody: RequestBody? = when (method) {
                "POST", "PUT", "PATCH" -> {
                    val contentType = request.headers["Content-Type"] ?: "application/json; charset=utf-8"
                    //request.body.toRequestBody(contentType.toMediaTypeOrNull())
                    RequestBody.create(contentType.toMediaType(), request.body)
                }
                else -> null
            }

            val okRequest = Request.Builder()
                .url(url)
                .method(method, requestBody)
                .headers(headersBuilder.build())
                .build()

            val response = client.newCall(okRequest).execute()

            loge("Request:$request")

            val mediaType = response.body?.contentType()
            val contentTypeHeader = response.header("Content-Type")
            val mimeType = when {
                mediaType != null -> "${mediaType.type}/${mediaType.subtype}"
                contentTypeHeader != null -> contentTypeHeader.substringBefore(";").trim()
                else -> "application/octet-stream"
            }

            val encoding = when {
                mediaType?.charset() != null -> mediaType.charset()?.name()
                contentTypeHeader?.contains("charset=", ignoreCase = true) == true ->
                    Regex("charset=([^;]+)", RegexOption.IGNORE_CASE)
                        .find(contentTypeHeader)
                        ?.groupValues?.get(1)
                        ?.trim()
                mimeType.startsWith("text/") || mimeType.contains("json") -> "utf-8"
                else -> null
            }

            val statusCode = response.code
            val reason = response.message.takeIf { it.isNotBlank() } ?: if (statusCode == 206) "Partial Content" else "OK"

//            var responseHeaders = mutableMapOf<String, String>()
//            for ((k, v) in response.headers) {
//                responseHeaders[k] = v
//            }

            val responseHeaders = getCapitalizedHeaders(response)
            //responseHeaders["Content-Type"] = "application/octet-stream"
            //responseHeaders["Content-Encoding"] = "utf-8"
            responseHeaders["status"] = statusCode.toString()


            val bodyBytes = response.body?.bytes()
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = bodyBytes?.let {
                digest.digest(bodyBytes).joinToString("") { "%02x".format(it) }
            } ?: 0

            val bodyStream = if (url.endsWith(".mp4")) {
                val size = bodyBytes?.size ?: 0
                //ByteArrayInputStream(ByteArray(1000) { 10.toByte() })
                ByteArrayInputStream("Hello\n".repeat(1000).toByteArray())
            } else {
                ByteArrayInputStream(bodyBytes)
            }
            //responseHeaders["Content-Length"] = bodyBytes?.size?.toString() ?: "0"


            WebResourceResponse(mimeType, encoding, statusCode, reason, responseHeaders, bodyStream).also {
                loge("Response:$response")
                loge(it.mimeType)
                loge(it.encoding ?: "null")
                loge(it.statusCode.toString())
                loge(it.reasonPhrase)
                loge(it.responseHeaders.toString())
                loge(bodyBytes?.size.toString())
                loge(hash.toString())
                loge("head: ${bodyBytes?.toHexSlice(0, 160)}")
                loge("tail: ${bodyBytes?.toHexSlice(bodyBytes.size - 160, bodyBytes.size)}")
            }
        } catch (e: IOException) {
            loge("HttpsConnectionManager fetchViaOkHttp", e)
            null
        }
    }

    fun getCapitalizedHeaders(response: okhttp3.Response): MutableMap<String, String> {
        val result = mutableMapOf<String, String>()
        for ((key, value) in response.headers.toMultimap()) {
            if (key == "content-length"
                || key == "content-type"
                || key == "content-encoding"
                || key == "content-range") continue
            val capitalizedKey = capitalizeHttpHeader(key)
            result[capitalizedKey] = value.firstOrNull() ?: ""
        }
        return result
    }

    fun capitalizeHttpHeader(name: String): String {
        return name.split("-")
            .joinToString("-") { it.replaceFirstChar { c -> c.uppercaseChar() } }
    }

    fun ByteArray.toHexSlice(from: Int, to: Int): String {
        val safeFrom = from.coerceAtLeast(0)
        val safeTo = to.coerceAtMost(this.size)
        if (safeFrom >= safeTo || this.isEmpty()) return "(empty)"
        return this.sliceArray(safeFrom until safeTo).joinToString(" ") { "%02x".format(it) }
    }

    private fun mapToQuery(data: Map<String, String>) = data.entries.joinToString("&") {
        "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
    }
}
