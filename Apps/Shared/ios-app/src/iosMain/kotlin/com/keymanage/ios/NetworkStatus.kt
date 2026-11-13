package com.keymanage.ios

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_t
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

// 네트워크 끊김 상태(AppEnvironment.isNetworkOffline). 첫 경로 정보가 오기 전에는 연결된 것으로 본다(웹 navigator.onLine과 같다).
// 상태가 바뀔 때만 알린다. 메인 스레드에서만 쓴다
class NetworkStatus {
    private var offline = false
    private var listeners = emptyList<() -> Unit>()

    fun isOffline(): Boolean = offline

    fun subscribe(listener: () -> Unit): () -> Unit {
        listeners = listeners + listener
        return { listeners = listeners - listener }
    }

    fun update(reachable: Boolean) {
        if (offline == !reachable) return
        offline = !reachable
        listeners.forEach { it() }
    }
}

/** NWPathMonitor로 [NetworkStatus]를 갱신한다. 경로 알림은 메인 큐로 받는다 */
@OptIn(ExperimentalForeignApi::class)
class NetworkPathMonitor(private val status: NetworkStatus) : NetworkMonitoring {
    private var monitor: nw_path_monitor_t = null

    override fun start() {
        if (monitor != null) return
        val created = nw_path_monitor_create()
        nw_path_monitor_set_queue(created, dispatch_get_main_queue())
        nw_path_monitor_set_update_handler(created) { path ->
            status.update(reachable = nw_path_get_status(path) == nw_path_status_satisfied)
        }
        nw_path_monitor_start(created)
        monitor = created
    }

    override fun stop() {
        monitor?.let(::nw_path_monitor_cancel)
        monitor = null
    }
}
