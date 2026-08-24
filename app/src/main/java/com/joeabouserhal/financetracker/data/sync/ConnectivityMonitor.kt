package com.joeabouserhal.financetracker.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-scoped connectivity monitor. Mirrors the default network's internet
 * capability into a [StateFlow] so Settings/Dashboard can show the
 * online/offline pill and the scheduler can trigger a sync when the device
 * comes back online.
 */
class ConnectivityMonitor(context: Context) {
  private val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

  private val _isOnline = MutableStateFlow(currentlyOnline())
  val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

  private val callback =
    object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        _isOnline.value = hasInternet(network)
      }

      override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
        _isOnline.value = hasInternet(network)
      }

      override fun onLost(network: Network) {
        _isOnline.value = currentlyOnline()
      }
    }

  init {
    manager.registerDefaultNetworkCallback(callback)
  }

  private fun currentlyOnline(): Boolean =
    manager.activeNetwork?.let { hasInternet(it) } ?: false

  private fun hasInternet(network: Network): Boolean {
    val caps = manager.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
      caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
  }
}
