package com.keymanage.websession

import com.keymanage.domain.device.DeviceRecordStore
import com.keymanage.domain.device.DeviceStorageException
import com.keymanage.domain.device.RememberedKdf
import com.keymanage.domain.device.TabMemory
import com.keymanage.domain.tabs.TabChannel
import com.keymanage.domain.tabs.TabLock
import com.keymanage.domain.tabs.TabLockName
import com.keymanage.domain.tabs.TabMessage
import com.keymanage.domain.tabs.Tabs
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

// TypeScript 객체(JsPorts.kt)를 domain 포트로 감싼다. Promise는 await()로 기다린다
// JS가 던지는 Error는 Kotlin Exception이 아니라서 도메인의 catch (Exception)을 지나친다. 경계에서 Exception으로 바꿔 준다

/** 기기 저장소 호출이 던진 값은 모두 DeviceStorageException으로 바꾼다. 취소는 그대로 둔다 */
private suspend fun <T> deviceCall(call: suspend () -> T): T = try {
    call()
} catch (error: CancellationException) {
    throw error
} catch (error: DeviceStorageException) {
    throw error
} catch (error: Throwable) {
    throw DeviceStorageException(error)
}

internal class JsDeviceRecordStore(private val js: JsDeviceStore) : DeviceRecordStore {
    override suspend fun load(): String? = deviceCall { js.load().await() }

    override suspend fun save(recordJson: String) {
        deviceCall { js.save(recordJson).await() }
    }

    override suspend fun clear() {
        deviceCall { js.clear().await() }
    }
}

/** 탭 잠금을 쓰지 못함(navigator.locks 거절 등). block이 던진 예외와 구분한다 */
internal class TabLockException(cause: Throwable) : Exception("탭 잠금을 쓰지 못함", cause)

internal class JsTabLock(private val js: JsTabs) : TabLock {
    override suspend fun <T> run(name: TabLockName, ifAvailable: Boolean, block: suspend () -> T): T? = supervisorScope {
        // 잠금을 얻어 fn이 끝났는지는 JS가 돌려주는 값으로 알 수 없다(못 얻으면 null, 얻어도 block이 null일 수 있다)
        var ran = false
        var value: T? = null
        var blockError: Throwable? = null
        try {
            // block의 오류는 fn의 Promise reject로 JS를 거쳐 await에서 그대로 다시 던져진다
            js.lockRun(name.name.lowercase(), ifAvailable) {
                promise {
                    try {
                        value = block()
                        ran = true
                    } catch (error: Throwable) {
                        blockError = error
                        throw error
                    }
                    null
                }
            }.await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            // block이 던진 값과 Kotlin 예외는 그대로 두고, JS가 낸 오류(TypeError 등)만 Exception으로 바꾼다
            if (error === blockError || error is Exception) throw error
            throw TabLockException(error)
        }
        if (ran) value else null
    }
}

internal class JsTabChannel(private val js: JsTabs) : TabChannel {
    override fun post(message: TabMessage) = js.post(message.wire)

    override fun subscribe(listener: (TabMessage) -> Unit): () -> Unit {
        val unsubscribe = js.subscribe { kind -> TabMessage.fromWire(kind)?.let(listener) }
        return { unsubscribe() }
    }
}

internal class JsTabsAdapter(js: JsTabs) : Tabs {
    override val lock: TabLock = JsTabLock(js)
    override val channel: TabChannel = JsTabChannel(js)
}

/** 저장소 오류는 밖으로 내지 않는다. 읽지 못하면 기억이 없는 것으로 본다 (TabMemory 계약) */
internal class JsTabMemoryAdapter(private val js: JsTabMemory) : TabMemory {
    override fun get(): RememberedKdf? = try {
        js.get()?.let(::parseRemembered)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }

    override fun set(value: RememberedKdf) {
        try {
            js.set(encodeRemembered(value))
        } catch (_: Throwable) {
            // 저장하지 못해도 세션은 계속된다
        }
    }

    override fun clear() {
        try {
            js.clear()
        } catch (_: Throwable) {
            // 지우지 못해도 세션은 계속된다
        }
    }
}

internal fun encodeRemembered(value: RememberedKdf): String = JsonObject(
    linkedMapOf(
        "email" to JsonPrimitive(value.email),
        "salt" to JsonPrimitive(value.salt),
        "iterations" to JsonPrimitive(value.iterations),
    ),
).toString()

/** 모양이 틀리면 null */
internal fun parseRemembered(text: String): RememberedKdf? {
    val value = try {
        Json.parseToJsonElement(text) as? JsonObject
    } catch (_: Exception) {
        null
    } ?: return null
    val email = (value["email"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
    val salt = (value["salt"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
    val iterations = (value["iterations"] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull() ?: return null
    return RememberedKdf(email, salt, iterations)
}
