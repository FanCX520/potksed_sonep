package com.example.data.remote

import android.os.Build
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Android 5.0.2 (API 21) compatible OkHttp factory.
 * Explicitly enables TLS 1.2 on API 21 devices where system SSLSocketFactory defaults to TLS 1.0,
 * preventing silent HTTPS handshake failures on Chinese ICP-filed APIs.
 */
object Api21NetworkFactory {

    fun createClient(
        connectTimeoutSec: Long = 10L,
        readTimeoutSec: Long = 15L
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .writeTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                tmf.init(null as KeyStore?)
                val trustManager = tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
                if (trustManager != null) {
                    val sc = SSLContext.getInstance("TLSv1.2")
                    sc.init(null, arrayOf(trustManager), null)
                    builder.sslSocketFactory(Tls12SocketFactory(sc.socketFactory), trustManager)
                }
            } catch (_: Exception) {
                // Keep default socket factory if device already patched
            }
        }

        val modernTls = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2, TlsVersion.TLS_1_1)
            .build()

        builder.connectionSpecs(
            listOf(
                modernTls,
                ConnectionSpec.COMPATIBLE_TLS,
                ConnectionSpec.CLEARTEXT
            )
        )
        return builder.build()
    }

    private class Tls12SocketFactory(private val delegate: SSLSocketFactory) : SSLSocketFactory() {
        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

        override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket =
            patch(delegate.createSocket(s, host, port, autoClose))

        override fun createSocket(host: String?, port: Int): Socket =
            patch(delegate.createSocket(host, port))

        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
            patch(delegate.createSocket(host, port, localHost, localPort))

        override fun createSocket(host: InetAddress?, port: Int): Socket =
            patch(delegate.createSocket(host, port))

        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
            patch(delegate.createSocket(address, port, localAddress, localPort))

        private fun patch(socket: Socket): Socket {
            if (socket is SSLSocket) {
                val supported = socket.supportedProtocols.toSet()
                val desired = listOf("TLSv1.3", "TLSv1.2", "TLSv1.1").filter { supported.contains(it) }
                if (desired.isNotEmpty()) {
                    socket.enabledProtocols = desired.toTypedArray()
                }
            }
            return socket
        }
    }
}
