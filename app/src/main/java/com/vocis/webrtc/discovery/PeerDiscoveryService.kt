package com.vocis.webrtc.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

/**
 * Representation of a discovered local peer on the LAN.
 */
data class VocisPeer(
    val peerName: String,
    val hostAddress: InetAddress,
    val port: Int,
    val lastSeenMs: Long = System.currentTimeMillis()
)

/**
 * Local peer discovery engine utilizing Android Network Service Discovery (mDNS).
 * Service Type: _vocis._tcp.
 * Default Port: 47821
 */
class PeerDiscoveryService(private val context: Context) {

    companion object {
        const val TAG = "PeerDiscoveryService"
        const val SERVICE_TYPE = "_vocis._tcp."
        const val DEFAULT_PORT = 47821
    }

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private val _discoveredPeers = MutableStateFlow<List<VocisPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<VocisPeer>> = _discoveredPeers.asStateFlow()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    /**
     * Advertises local device reachability over mDNS.
     */
    fun registerService(deviceName: String, port: Int = DEFAULT_PORT) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = deviceName
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service registered: ${NsdServiceInfo.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Service registration failed: $errorCode")
            }
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d(TAG, "Service unregistered")
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Service unregistration failed: $errorCode")
            }
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    /**
     * Starts continuous mDNS scanning for local VOCIS peers.
     */
    fun startDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "mDNS Service discovery started for $regType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType == SERVICE_TYPE) {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                        }

                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            val host = resolvedInfo.host ?: return
                            val peer = VocisPeer(
                                peerName = resolvedInfo.serviceName,
                                hostAddress = host,
                                port = resolvedInfo.port
                            )
                            val current = _discoveredPeers.value.toMutableList()
                            current.removeAll { it.peerName == peer.peerName }
                            current.add(peer)
                            _discoveredPeers.value = current
                        }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val current = _discoveredPeers.value.toMutableList()
                current.removeAll { it.peerName == serviceInfo.serviceName }
                _discoveredPeers.value = current
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "mDNS Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed: $errorCode")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: $errorCode")
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    /**
     * Stops discovery and tears down listeners.
     */
    fun stopDiscovery() {
        discoveryListener?.let {
            try { nsdManager.stopServiceDiscovery(it) } catch (_: Exception) {}
            discoveryListener = null
        }
    }

    /**
     * Unregisters the advertised service.
     */
    fun unregisterService() {
        registrationListener?.let {
            try { nsdManager.unregisterService(it) } catch (_: Exception) {}
            registrationListener = null
        }
    }
}
