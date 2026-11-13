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

    suspend fun discoverAndVerifyRegionalRelay(
        targetRegionCode: String
    ): Pair<ProxyConfig?, NetworkExitReport?> = withContext(Dispatchers.IO) {
        val code = targetRegionCode.uppercase()
        val candidates = mutableListOf<ProxyConfig>()

        // Source 1: ProxyScrape regional HTTP relays
        runCatching {
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
            val url = "https://api.proxyscrape.com/v2/?request=displayproxies&protocol=http&country=$code&timeout=4500&ssl=yes"
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    resp.body?.string()?.lines().orEmpty()
                        .map { it.trim() }
                        .filter { it.contains(":") }
                        .take(5)
                        .forEach { line ->
                            val parts = line.split(":")
                            val host = parts.getOrNull(0).orEmpty()
                            val port = parts.getOrNull(1)?.toIntOrNull() ?: 0
                            if (host.isNotBlank() && port in 1..65535) {
                                candidates += ProxyConfig(ProxyType.HTTP, host, port)
                            }
                        }
                }
            }
        }

        // Source 2: GeoNode JSON regional relay list
        if (candidates.size < 4) {
            runCatching {
                val client = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
                val url = "https://proxylist.geonode.com/api/proxy-list?country=$code&protocols=http%2Csocks5&limit=5&page=1&sort_by=lastChecked&sort_type=desc"
                val req = Request.Builder().url(url).header("Accept", "application/json").build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val data = JSONObject(body).optJSONArray("data")
                        if (data != null) {
                            for (i in 0 until data.length().coerceAtMost(5)) {
                                val item = data.optJSONObject(i) ?: continue
                                val ip = item.optString("ip", "")
                                val port = item.optString("port", "0").toIntOrNull() ?: 0
                                val proto = item.optJSONArray("protocols")?.optString(0)?.uppercase().orEmpty()
                                val type = if (proto == "SOCKS5") ProxyType.SOCKS5 else ProxyType.HTTP
                                if (ip.isNotBlank() && port in 1..65535) {
                                    candidates += ProxyConfig(type, ip, port)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Verify candidate relays against real Exit-IP endpoints
        for (candidate in candidates.distinct().take(4)) {
            val report = probeExitIp(candidate, code)
            if (report.errorMessage == null && report.matchesTargetRegion && report.ipAddress.isNotBlank()) {
                return@withContext candidate to report
            }
        }

        null to null
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
