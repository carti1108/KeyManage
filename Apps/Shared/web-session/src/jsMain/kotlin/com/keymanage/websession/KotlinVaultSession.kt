package com.keymanage.websession

import com.keymanage.domain.auth.AuthService
import com.keymanage.domain.backup.LocalCalendar
import com.keymanage.domain.backup.LocalDay
import com.keymanage.domain.backup.parseBackup
import com.keymanage.domain.device.DeviceRecordStore
import com.keymanage.domain.device.TabMemory
import com.keymanage.domain.session.LockScope
import com.keymanage.domain.session.SessionState
import com.keymanage.domain.session.VaultSession
import com.keymanage.domain.session.VaultSessionDeps
import com.keymanage.domain.tabs.Tabs
import com.keymanage.domain.vault.RevisionMemory
import com.keymanage.domain.vault.VaultSync
import com.keymanage.domain.vault.parseVaultData
import com.keymanage.serverhttp.ApiClient
import com.keymanage.serverhttp.HttpAuthApi
import com.keymanage.serverhttp.HttpVaultApi
import com.keymanage.serverhttp.keyManageHttpClient
import com.keymanage.serverhttp.resolveBaseUrl
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.promise
import kotlin.js.Date
import kotlin.js.Promise
import kotlin.time.Clock
import kotlin.time.Instant

// 웹이 부르는 세션 퍼사드 (domain-migration-plan.md 10장). VaultSession과 server-http를 묶고,
// suspend 함수는 Promise로, 상태는 JS 객체로, 볼트는 encodeVaultData JSON 문자열로 넘긴다. Long은 내보내지 않는다
// Promise가 reject할 때 값은 Kotlin 예외 그대로다. TypeScript는 errorInfo(e)로 읽는다

/** 기기의 현지 날짜(JS Date 기준). 백업 파일 이름에 쓴다 */
internal val jsCalendar = LocalCalendar { time ->
    val date = Date(time.toEpochMilliseconds().toDouble())
    LocalDay(date.getFullYear(), date.getMonth() + 1, date.getDate())
}

private fun instantOf(epochMillis: Double): Instant = Instant.fromEpochMilliseconds(epochMillis.toLong())

@JsExport
class KotlinVaultSession internal constructor(
    internal val session: VaultSession,
    private val scope: CoroutineScope,
) {
    // 같은 상태면 볼트를 다시 JSON으로 만들지 않는다. SessionState는 값 비교, 볼트는 객체 동일성으로 같다
    private var cachedState: SessionState? = null
    internal var cachedJs: JsSessionState? = null
        private set

    init {
        // 잠긴 뒤(이 탭·다른 탭의 잠금, 로그아웃)에는 평문 볼트 JSON을 든 캐시를 들고 있지 않는다
        session.onLock { clearStateCache() }
    }

    private fun clearStateCache() {
        cachedState = null
        cachedJs = null
    }

    /**
     * 첫 중단점까지 호출 안에서 바로 실행한다. 원본 TS의 async 함수처럼, 호출 직후 읽는 상태(epoch, 로그인 정보)가
     * 뒤따르는 lock()·state()와 같은 순서로 보이게 한다. 늦게 시작하면 바로 뒤의 lock이 새어 나간다
     */
    private fun <T> launchPromise(block: suspend () -> T): Promise<T> = scope.promise(start = CoroutineStart.UNDISPATCHED) { block() }

    fun state(): JsSessionState {
        val current = session.state()
        val cached = cachedJs
        if (cached != null && current == cachedState) return cached
        return current.toJs().also {
            cachedState = current
            cachedJs = it
        }
    }

    fun register(email: String, password: String): Promise<Unit> = launchPromise { session.register(email, password) }

    fun login(email: String, password: String, acceptRollback: Boolean): Promise<Unit> =
        launchPromise { session.login(email, password, acceptRollback) }

    /** @param scope 'allTabs' | 'thisTab' */
    fun lock(scope: String) {
        session.lock(
            when (scope) {
                "allTabs" -> LockScope.ALL_TABS
                "thisTab" -> LockScope.THIS_TAB
                else -> throw IllegalArgumentException("알 수 없는 잠금 범위: $scope")
            },
        )
    }

    /** @return 서버와 맞추지 못하고 저장해 둔 볼트로 열었는지(offline) */
    fun unlock(password: String, acceptRollback: Boolean): Promise<Boolean> =
        launchPromise { session.unlock(password, acceptRollback).offline }

    fun reconnect(password: String, acceptRollback: Boolean): Promise<Unit> =
        launchPromise { session.reconnect(password, acceptRollback) }

    /** @param vaultJson encodeVaultData 형식(웹 encodeVaultData의 결과) */
    fun save(vaultJson: String): Promise<Unit> = launchPromise { session.save(parseVaultData(vaultJson)) }

    /** @return 백업 파일 내용(JSON 텍스트). 파일 이름은 웹이 정한다 */
    fun exportBackup(nowEpochMs: Double): Promise<String> =
        launchPromise { session.exportBackup(instantOf(nowEpochMs), jsCalendar).text }

    /**
     * @param backupJson 웹이 고른 백업 파일 내용 그대로. 형식 오류는 NotABackupFileException 등으로 reject한다
     * @return 복원한 항목 수
     */
    fun restoreBackup(backupJson: String, password: String, nowEpochMs: Double): Promise<Int> =
        launchPromise { session.restoreBackup(parseBackup(backupJson), password, instantOf(nowEpochMs)) }

    fun changeMasterPassword(currentPassword: String, newPassword: String): Promise<Unit> =
        launchPromise { session.changeMasterPassword(currentPassword, newPassword) }

    fun deleteAccount(password: String): Promise<Unit> = launchPromise { session.deleteAccount(password) }

    fun hasUnsavedChanges(): Boolean = session.hasUnsavedChanges()

    fun restore(): Promise<Unit> = launchPromise { session.restore() }

    fun retryPendingSync(): Promise<Unit> = launchPromise { session.retryPendingSync() }

    fun logout(): Promise<Unit> = launchPromise { session.logout() }

    fun pendingOnDevice(): Promise<Boolean> = launchPromise { session.pendingOnDevice() }

    /** @return 해제 함수 */
    fun onRemoteChange(listener: () -> Unit): () -> Unit = session.onRemoteChange(listener)

    fun onLock(listener: () -> Unit): () -> Unit = session.onLock(listener)

    fun onUnlock(listener: () -> Unit): () -> Unit = session.onUnlock(listener)

    /** 대기 내용을 다시 보낸 결과. 모두 올렸으면 null */
    fun onSyncChange(listener: (JsErrorInfo?) -> Unit): () -> Unit =
        session.onSyncChange { error -> listener(error?.let(::toErrorInfo)) }
}

/**
 * @param apiBaseUrl 빌드할 때 정한 서버 주소. null이나 ""면 같은 출처. localhost가 아니면 HTTPS 출처만 받는다
 * @throws com.keymanage.serverhttp.InvalidBaseUrlException
 */
@JsExport
fun createKotlinSession(apiBaseUrl: String?, deviceStore: JsDeviceStore, tabs: JsTabs, tabMemory: JsTabMemory?): KotlinVaultSession =
    createKotlinSession(apiBaseUrl, deviceStore, tabs, tabMemory, keyManageHttpClient())

/** HTTP 엔진을 바꿀 수 있는 조립. 테스트가 MockEngine을 넣는다 */
internal fun createKotlinSession(
    apiBaseUrl: String?,
    deviceStore: JsDeviceStore,
    tabs: JsTabs,
    tabMemory: JsTabMemory?,
    http: HttpClient,
): KotlinVaultSession = buildKotlinSession(
    resolveBaseUrl(apiBaseUrl),
    http,
    JsDeviceRecordStore(deviceStore),
    JsTabsAdapter(tabs),
    tabMemory?.let(::JsTabMemoryAdapter),
)

/** Kotlin 포트로 조립한다. 세션이 띄우는 작업과 퍼사드의 Promise는 같은 범위에서 돈다 */
internal fun buildKotlinSession(
    baseUrl: String,
    http: HttpClient,
    deviceStore: DeviceRecordStore,
    tabs: Tabs,
    tabMemory: TabMemory?,
): KotlinVaultSession {
    val scope = CoroutineScope(SupervisorJob())
    val client = ApiClient(baseUrl, http)
    val session = VaultSession(
        VaultSessionDeps(
            auth = AuthService(HttpAuthApi(client)),
            vaultSyncFor = { token, key, memory -> VaultSync(HttpVaultApi(client, token), key, Clock.System, memory) },
            revisionMemory = RevisionMemory(),
            deviceStore = deviceStore,
            tabs = tabs,
            scope = scope,
            tabMemory = tabMemory,
        ),
    )
    return KotlinVaultSession(session, scope)
}
