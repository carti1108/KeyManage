package com.keymanage.websession

import com.keymanage.domain.backup.BackupFile
import com.keymanage.domain.backup.BackupFileSource
import com.keymanage.domain.backup.LocalCalendar
import com.keymanage.domain.lock.AutoLockSettingStore
import com.keymanage.domain.lock.DEFAULT_AUTO_LOCK
import com.keymanage.domain.lock.InvalidAutoLockTimeoutException
import com.keymanage.domain.lock.isAutoLockOption
import com.keymanage.domain.lock.parseAutoLockSetting
import com.keymanage.feature.AppEnvironment
import com.keymanage.feature.BackupFilesPort
import com.keymanage.feature.ClipboardPort
import kotlinx.coroutines.await
import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.Promise
import kotlin.time.Clock
import kotlin.time.Duration

// 공유 RIB(feature)이 쓰는 바깥 기능을 TypeScript 객체로 받는 포트와 어댑터 (domain-migration-plan 11.2)
// 실패는 Promise reject(또는 throw)로 알린다. 시간 값은 밀리초 숫자다

/** 클립보드. 복사하고 정해진 시간 뒤 비우는 일(COM-001)까지 맡는다. 실패하면 reject */
@JsExport
external interface JsClipboard {
    fun copy(text: String): Promise<Unit>
}

/** 사용자가 고른 백업 파일. 크기(바이트)를 먼저 보고 내용을 읽는다 */
@JsExport
external interface JsPickedFile {
    val size: Int
    fun text(): Promise<String>
}

/** 백업 파일 저장(다운로드)과 선택(VLT-004). pick은 사용자가 취소하면 null */
@JsExport
external interface JsBackupFiles {
    fun save(name: String, text: String): Promise<Unit>
    fun pick(): Promise<JsPickedFile?>
}

/** 앱 쪽 상태. 자동 잠금 시간은 기기 저장소(localStorage)가 정하고 밀리초로 주고받는다 */
@JsExport
external interface JsEnvironment {
    /** 서버 주소 표시용. 같은 출처면 빈 문자열 */
    val serverAddress: String

    fun isNetworkOffline(): Boolean

    /** 돌려주는 함수가 해제다 */
    fun subscribeNetwork(listener: () -> Unit): () -> Unit

    /** 저장값(ms). 없으면 TypeScript가 기본값을 돌려준다. 선택지가 아니면 Kotlin이 기본 5분으로 본다 */
    fun readAutoLock(): Int

    fun writeAutoLock(ms: Int)

    /** 다른 탭이 바꾼 값(ms)을 받는다. 이 탭의 write는 알리지 않는다 */
    fun subscribeAutoLock(listener: (Int) -> Unit): () -> Unit
}

/**
 * JS가 던지는 Error는 Kotlin Exception이 아니라 화면 RIB의 catch (Exception)을 지나친다.
 * 경계에서 Exception으로 바꿔 준다. 취소는 그대로 둔다
 */
internal class JsPortException(cause: Throwable) : Exception("바깥 기능 호출 실패", cause)

private suspend fun <T> portCall(call: suspend () -> T): T = try {
    call()
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    throw error
} catch (error: Throwable) {
    throw JsPortException(error)
}

internal class JsClipboardAdapter(private val js: JsClipboard) : ClipboardPort {
    override suspend fun copy(text: String) {
        portCall { js.copy(text).await() }
    }
}

internal class JsBackupFilesAdapter(private val js: JsBackupFiles) : BackupFilesPort {
    override suspend fun save(file: BackupFile) {
        portCall { js.save(file.name, file.text).await() }
    }

    override suspend fun pick(): BackupFileSource? {
        val picked = portCall { js.pick().await() } ?: return null
        return BackupFileSource(picked.size) { portCall { picked.text().await() } }
    }
}

/** 읽지 못하거나 선택지가 아닌 값은 기본 5분. 저장하지 못해도 던지지 않는다 (AutoLockSettingStore 계약) */
internal class JsAutoLockSettingStore(private val js: JsEnvironment) : AutoLockSettingStore {
    // 저장에 실패하면 이번 실행에만 쓴다
    private var sessionValue: Duration? = null

    override fun read(): Duration = sessionValue ?: try {
        parseAutoLockSetting(js.readAutoLock().toString())
    } catch (_: Throwable) {
        DEFAULT_AUTO_LOCK
    }

    override fun write(timeout: Duration) {
        if (!isAutoLockOption(timeout)) throw InvalidAutoLockTimeoutException(timeout)
        try {
            js.writeAutoLock(timeout.inWholeMilliseconds.toInt())
            sessionValue = null
        } catch (_: Throwable) {
            sessionValue = timeout
        }
    }

    override fun subscribe(listener: (Duration) -> Unit): () -> Unit = try {
        js.subscribeAutoLock { ms -> listener(parseAutoLockSetting(ms.toString())) }
    } catch (_: Throwable) {
        {}
    }
}

internal class JsAppEnvironment(
    private val js: JsEnvironment,
    override val clock: Clock = Clock.System,
    override val calendar: LocalCalendar = jsCalendar,
) : AppEnvironment {
    override val autoLockSetting: AutoLockSettingStore = JsAutoLockSettingStore(js)
    override val serverAddress: String get() = js.serverAddress

    override fun isNetworkOffline(): Boolean = try {
        js.isNetworkOffline()
    } catch (_: Throwable) {
        false
    }

    override fun subscribeNetwork(listener: () -> Unit): () -> Unit = js.subscribeNetwork(listener)
}

