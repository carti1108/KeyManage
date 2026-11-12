package com.keymanage.domain.device

import com.keymanage.domain.auth.UnlockKdf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

// 기기 저장소 쓰기를 한 번에 하나씩 순서대로 하고, 실패는 알린다 (ADR-0019, VLT-003)
// 원본: Apps/Web/src/domain/device/devicePersistence.ts
// 쓰기 실패로 사용 중인 볼트를 막지 않는다. 메모리의 값은 그대로 쓰고, 화면에 경고하고, 떠날 때 확인을 받는다
class DevicePersistence(
    private val store: DeviceRecordStore,
    private val onError: (Throwable) -> Unit,
) {
    private val mutex = Mutex()
    private var requested = 0

    // 마지막으로 요청한 쓰기가 아직 끝나지 않았거나 실패함
    private var behind = false

    // 마지막으로 끝난 쓰기가 실패함. 다음 쓰기가 성공하면 풀린다
    private var failed = false

    /** 쓰지 않기로 했지만(기기 기록을 읽지 못함 등) 기기가 메모리보다 오래됐음을 알린다 */
    fun markFailed(error: Throwable) {
        failed = true
        behind = true
        onError(error)
    }

    /** @return 이 쓰기가 성공했는지. 실패해도 던지지 않고 오류는 onError로 알린다 */
    suspend fun save(record: DeviceRecord): Boolean = enqueue { store.save(encodeDeviceRecord(record)) }

    suspend fun clear(): Boolean = enqueue { store.clear() }

    /** 마지막으로 요청한 기록이 아직 기기에 반영되지 않음 (떠남 확인용) */
    fun isBehind(): Boolean = behind

    /** 마지막 쓰기가 실패해 기기의 기록이 메모리보다 오래됨 (화면 경고용) */
    fun hasFailed(): Boolean = failed

    private suspend fun enqueue(write: suspend () -> Unit): Boolean {
        val id = ++requested
        behind = true
        return mutex.withLock {
            try {
                write()
                failed = false
                if (id == requested) behind = false
                true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failed = true
                onError(error)
                false
            }
        }
    }
}

/**
 * 이 탭이 마지막으로 연 계정의 KDF 값 (SEC-003 규칙 11, ADR-WEB-001). 원본: device/tabMemory.ts
 * 비밀이 아닌 값(이메일·salt·반복 횟수)만 둔다. 키·비밀번호·인증 해시는 두지 않는다
 */
data class RememberedKdf(val email: String, val salt: String, val iterations: Int)

/** 탭에만 남는 기억(sessionStorage). 모바일은 앱 메모리. 구현은 저장소 오류를 밖으로 내지 않고, 읽지 못하면 null로 본다 */
interface TabMemory {
    fun get(): RememberedKdf?
    fun set(value: RememberedKdf)
    fun clear()
}

fun rememberedKdf(email: String, kdf: UnlockKdf): RememberedKdf = RememberedKdf(email, kdf.salt, kdf.iterations)
