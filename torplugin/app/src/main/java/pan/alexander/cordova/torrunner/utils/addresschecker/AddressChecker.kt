package pan.alexander.cordova.torrunner.utils.addresschecker

import android.annotation.SuppressLint
import pan.alexander.cordova.torrunner.utils.logger.Logger.logw
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class AddressChecker @Inject constructor() {

    private val trustAllCerts by lazy {
        arrayOf<TrustManager>(
            @SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                @SuppressLint("TrustAllX509TrustManager")
                override fun checkClientTrusted(
                    chain: Array<java.security.cert.X509Certificate>,
                    authType: String
                ) {
                }

                @SuppressLint("TrustAllX509TrustManager")
                override fun checkServerTrusted(
                    chain: Array<java.security.cert.X509Certificate>,
                    authType: String
                ) {
                }

                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> =
                    arrayOf()
            }
        )
    }

    private val sslSocketFactory by lazy {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())
        sslContext.socketFactory
    }

    fun isHttpsAddressReachable(domain: String, port: Int = 443, timeoutMs: Int = 3000): Boolean =
        try {

            val factory: SSLSocketFactory = sslSocketFactory

            factory.createSocket().use { plainSocket ->
                val socket = plainSocket as SSLSocket
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(domain, port), timeoutMs)
                socket.enabledProtocols = socket.supportedProtocols
                socket.startHandshake()

                if (!validateCertificateDomain(socket, domain)) {
                    return@use false
                }

                val writer = socket.outputStream.bufferedWriter()
                writer.write("HEAD / HTTP/1.1\r\nHost: $domain\r\nConnection: close\r\n\r\n")
                writer.flush()

                val reader = socket.inputStream.bufferedReader()
                val responseLine = reader.readLine()

                if (!responseLine.startsWith("HTTP")) {
                    logw("Address ${domain}:${port} respond with $responseLine")
                }

                responseLine != null && responseLine.startsWith("HTTP")
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
