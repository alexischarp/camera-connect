package app.cameraconnect.network

import android.net.Network
import app.cameraconnect.discovery.DiscoveryLogger
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/** Supplies the camera [Network] currently connected, or null when the phone is not on the camera Wi-Fi. */
fun interface CameraNetworkProvider {
    fun current(): Network?
}

/**
 * The single OkHttp client dedicated to the camera. Every socket is created through
 * [Network.getSocketFactory] of the camera network, so the rest of the app keeps using the
 * default (mobile data) network untouched. The client lives for the whole process and follows
 * reconnections because the network is resolved at socket-creation time.
 */
class CameraHttpClient(provider: CameraNetworkProvider, logger: DiscoveryLogger) {

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .socketFactory(BoundSocketFactory(provider))
        .dns(NetworkDns(provider))
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(LocalOnlyInterceptor)
        .addInterceptor(HttpLogInterceptor(logger))
        .build()

    companion object {
        const val USER_AGENT = "CameraConnect/0.1 (Android) UPnP/1.1"
    }
}

/** Creates sockets on whatever camera network is connected when the socket is needed. */
private class BoundSocketFactory(private val provider: CameraNetworkProvider) : SocketFactory() {

    private fun delegate(): SocketFactory =
        provider.current()?.socketFactory ?: throw IOException("Camera network is not connected")

    override fun createSocket(): Socket = delegate().createSocket()

    override fun createSocket(host: String, port: Int): Socket = delegate().createSocket(host, port)

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        delegate().createSocket(host, port, localHost, localPort)

    override fun createSocket(host: InetAddress, port: Int): Socket = delegate().createSocket(host, port)

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        delegate().createSocket(address, port, localAddress, localPort)
}

/** Resolves names on the camera network and refuses anything that is not a local address. */
private class NetworkDns(private val provider: CameraNetworkProvider) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val resolved = LocalAddressPolicy.parseLiteral(hostname)?.let { listOf(it) }
            ?: (provider.current() ?: throw UnknownHostException("Camera network is not connected"))
                .getAllByName(hostname).toList()
        val allowed = resolved.filter(LocalAddressPolicy::isAllowed)
        if (allowed.isEmpty()) throw UnknownHostException("Refusing non-local host $hostname")
        return allowed
    }
}

private object LocalOnlyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val literal = LocalAddressPolicy.parseLiteral(request.url.host)
        if (literal != null && !LocalAddressPolicy.isAllowed(literal)) {
            throw IOException("Refusing non-local host ${request.url.host}")
        }
        return chain.proceed(request)
    }
}

private class HttpLogInterceptor(private val logger: DiscoveryLogger) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val started = System.nanoTime()
        logger.d(TAG, "--> ${request.method} ${request.url}")
        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            logger.w(TAG, "<-- failed ${request.url}: $e")
            throw e
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        logger.d(
            TAG,
            "<-- ${response.code} ${request.url} (${elapsedMs} ms, " +
                "type=${response.header("Content-Type") ?: "?"}, length=${response.header("Content-Length") ?: "?"})",
        )
        return response
    }

    private companion object {
        const val TAG = "Http"
    }
}
