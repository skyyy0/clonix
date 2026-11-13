package com.example.network

import com.example.data.model.NetworkExitReport
import com.example.data.model.ProxyConfig
import com.example.data.model.ProxyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.util.concurrent.TimeUnit

class NetworkRoutingEngine {

    suspend fun probeExitIp(
        proxyConfig: ProxyConfig?,
        targetRegionCode: String
    ): NetworkExitReport = withContext(Dispatchers.IO) {
        val startedNs = System.nanoTime()
        val useProxy = proxyConfig != null && proxyConfig.isConfigured
        var previousAuth: java.net.Authenticator? = null

        try {
            val builder = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(10, TimeUnit.SECONDS)

            if (useProxy && proxyConfig != null) {
                val javaProxyType = when (proxyConfig.type) {
                    ProxyType.HTTP -> Proxy.Type.HTTP
                    ProxyType.SOCKS5 -> Proxy.Type.SOCKS
                    ProxyType.DIRECT -> Proxy.Type.DIRECT
                }
                builder.proxy(Proxy(javaProxyType, InetSocketAddress(proxyConfig.host.trim(), proxyConfig.port)))

                if (proxyConfig.username.isNotBlank()) {
                    if (proxyConfig.type == ProxyType.HTTP) {
                        val creds = Credentials.basic(proxyConfig.username, proxyConfig.password)
                        builder.proxyAuthenticator { _, response ->
                            if (response.request.header("Proxy-Authorization") != null) {
                                null
                            } else {
                                response.request.newBuilder()
                                    .header("Proxy-Authorization", creds)
                                    .build()
                            }
                        }
                    } else if (proxyConfig.type == ProxyType.SOCKS5) {
                        java.net.Authenticator.setDefault(object : java.net.Authenticator() {
                            override fun getPasswordAuthentication(): PasswordAuthentication {
                                return PasswordAuthentication(
                                    proxyConfig.username,
                                    proxyConfig.password.toCharArray()
                                )
                            }
                        })
                    }
                }
            }

            val client = builder.build()
            val primaryResult = fetchIpJson(client, "https://ipwho.is/")
                ?: fetchIpJson(client, "https://ipapi.co/json/")
                ?: return@withContext NetworkExitReport(
                    isLoading = false,
                    viaProxy = useProxy,
                    errorMessage = "Tidak dapat menjangkau server verifikasi IP publik."
                )

            val elapsedMs = ((System.nanoTime() - startedNs) / 1_000_000L).coerceAtLeast(1L)
            val ip = primaryResult.optString("ip", "")
            val countryCode = primaryResult.optString("country_code", "").ifBlank {
                primaryResult.optString("countryCode", "")
            }.uppercase()
            val countryName = primaryResult.optString("country", "").ifBlank {
                primaryResult.optString("country_name", countryCode)
            }
            val city = primaryResult.optString("city", "")
            val isp = primaryResult.optJSONObject("connection")?.optString("isp", "").orEmpty()
                .ifBlank { primaryResult.optString("org", "ISP") }

            NetworkExitReport(
                isLoading = false,
                ipAddress = ip,
                countryCode = countryCode,
                countryName = countryName,
                city = city,
                isp = isp,
                latencyMs = elapsedMs,
                viaProxy = useProxy,
                matchesTargetRegion = countryCode.equals(targetRegionCode, ignoreCase = true),
                errorMessage = if (ip.isBlank()) "Respon IP kosong" else null
            )
        } catch (t: Throwable) {
            NetworkExitReport(
                isLoading = false,
                viaProxy = useProxy,
                errorMessage = t.localizedMessage ?: "Koneksi proxy/jaringan gagal"
            )
        } finally {
            if (useProxy && proxyConfig?.type == ProxyType.SOCKS5 && proxyConfig.username.isNotBlank()) {
                java.net.Authenticator.setDefault(previousAuth)
            }
        }
    }

    private fun fetchIpJson(client: OkHttpClient, url: String): JSONObject? {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val json = JSONObject(body)
                if (json.has("success") && !json.optBoolean("success", true)) return@use null
                json
            }
        }.getOrNull()
    }
}
