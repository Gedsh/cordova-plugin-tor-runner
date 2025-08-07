package pan.alexander.cordova.torrunner.utils.addresschecker

import android.annotation.SuppressLint
import pan.alexander.cordova.torrunner.utils.Constants.CHROME_BROWSER_USER_AGENT
import pan.alexander.cordova.torrunner.utils.Constants.LOOPBACK_ADDRESS
import pan.alexander.cordova.torrunner.utils.logger.Logger.logw
import java.io.BufferedWriter
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class AddressChecker @Inject constructor() {

    private val trustAllCerts by lazy {
        arrayOf<TrustManager>(
            @SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                @SuppressLint("TrustAllX509TrustManager")
                override fun checkClientTrusted(
                    chain: Array<X509Certificate>,
                    authType: String
                ) {
                }

                @SuppressLint("TrustAllX509TrustManager")
                override fun checkServerTrusted(
                    chain: Array<X509Certificate>,
                    authType: String
                ) {
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> =
                    arrayOf()
            }
        )
    }

    private val sslSocketFactory by lazy {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        sslContext.socketFactory
    }

    fun isHttpsAddressReachable(
        domain: String,
        port: Int = 443,
        timeoutMs: Int = 3000,
        socksPort: Int = 0
    ): Boolean = try {

            val proxy = if (socksPort != 0) {
                Proxy(Proxy.Type.SOCKS, InetSocketAddress(LOOPBACK_ADDRESS, socksPort))
            } else {
                Proxy.NO_PROXY
            }

            Socket(proxy).use { plainSocket ->
                plainSocket.connect(InetSocketAddress(domain, port), timeoutMs)
                plainSocket.soTimeout = timeoutMs

                val sslSocket = (sslSocketFactory.createSocket(
                    plainSocket,
                    domain,
                    port,
                    true
                ) as SSLSocket)

                sslSocket.enabledProtocols = sslSocket.supportedProtocols.filter {
                    it.startsWith("TLS")
                }.toTypedArray()
                sslSocket.startHandshake()

                if (!validateCertificateDomain(sslSocket, domain)) {
                    return false
                }

                val writer = sslSocket.outputStream.bufferedWriter()
                writeHttpRequestNoCache(
                    writer = writer,
                    domain = domain,
                    method = "GET",
                    headers = mapOf("User-Agent" to CHROME_BROWSER_USER_AGENT)
                )

                val reader = sslSocket.inputStream.bufferedReader()
                val responseLine = reader.readLine()

                if (responseLine == null || !responseLine.startsWith("HTTP/")) {
                    logw("Unexpected response from $domain:$port -> $responseLine")
                    return false
                }

                val statusCode = responseLine.split(" ")[1].toIntOrNull() ?: return false
                if (statusCode in 200..404) {
                    return true
                } else {
                    logw("Received non-OK status code $statusCode from $domain")
                    return false
                }
            }
        } catch (_: SocketTimeoutException) {
            logw("Address ${domain}:${port} timeout")
            false
        } catch (e: Exception) {
            if (e.message != null) {
                if (e.cause != null) {
                    logw("Address ${domain}:${port} unreachable. Reason: ${e.javaClass.name} Details: ${e.message} ${e.cause}")
                } else {
                    logw("Address ${domain}:${port} unreachable. Reason: ${e.javaClass.name} Details: ${e.message}")
                }
            } else {
                logw("Address ${domain}:${port} unreachable. Reason: ${e.javaClass.name} ")
            }

            false
        }

    fun writeHttpRequestNoCache(
        writer: BufferedWriter,
        domain: String,
        path: String = "/",
        method: String = "HEAD",
        headers: Map<String, String> = emptyMap()
    ) = with(writer) {

        val nonce = System.currentTimeMillis()
        val requestPath = if ("?" in path) "$path&nocache=$nonce" else "$path?nocache=$nonce"

        write("$method $requestPath HTTP/1.1\r\n")
        write("Host: $domain\r\n")
        write("Connection: close\r\n")
        write("Cache-Control: no-cache, no-store, must-revalidate\r\n")
        write("Pragma: no-cache\r\n")
        write("Expires: 0\r\n")

        for ((key, value) in headers) {
            write("$key: $value\r\n")
        }

        write("\r\n")
        flush()
    }

    private fun validateCertificateDomain(socket: SSLSocket, domain: String): Boolean {
        val session = socket.session
        val hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
        if (!hostnameVerifier.verify(domain, session)) {
            val cert = session.peerCertificates?.firstOrNull() as? X509Certificate
            logw("Address $domain certificate failure, mismatch domain in SAN or CN: ${cert?.subjectX500Principal?.name}")
            return false
        }
        return true
    }

}
