package com.keymanage.domain.session

import com.keymanage.domain.auth.AuthApi
import com.keymanage.domain.auth.AuthService
import com.keymanage.domain.auth.ChangePasswordRequest
import com.keymanage.domain.auth.ChangedPassword
import com.keymanage.domain.auth.DeleteAccountRequest
import com.keymanage.domain.auth.KdfParams
import com.keymanage.domain.auth.LoginRequest
import com.keymanage.domain.auth.PBKDF2_SHA256
import com.keymanage.domain.auth.RegisterRequest
import com.keymanage.domain.auth.Session
import com.keymanage.domain.crypto.EncryptedVaultPayload
import com.keymanage.domain.crypto.Vectors
import com.keymanage.domain.crypto.text
import com.keymanage.domain.device.DeviceRecord
import com.keymanage.domain.device.DeviceRecordStore
import com.keymanage.domain.device.DeviceStorageException
import com.keymanage.domain.device.TabMemory
import com.keymanage.domain.device.parseDeviceRecord
import com.keymanage.domain.server.NetworkError
import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode
import com.keymanage.domain.tabs.SingleTab
import com.keymanage.domain.tabs.Tabs
import com.keymanage.domain.vault.ItemFields
import com.keymanage.domain.vault.RemoteVault
import com.keymanage.domain.vault.RevisionMemory
import com.keymanage.domain.vault.VaultApi
import com.keymanage.domain.vault.VaultData
import com.keymanage.domain.vault.VaultSync
import com.keymanage.domain.vault.activeItems
import com.keymanage.domain.vault.addItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// 세션 테스트가 함께 쓰는 가짜 서버와 준비 함수. 원본: Apps/Web/src/test/sessionFixtures.ts
// 원본 가짜들의 async 함수는 늘 한 번 이상 기다린다(마이크로태스크). 같은 끼어들기 순서를 내려고 가짜의 모든 suspend 함수는
// 먼저 yield()한다. 그렇지 않으면 UnconfinedTestDispatcher에서 부르는 즉시 끝나, 잠금·로그아웃이 끼어들 틈이 없다.
// 원본 테스트가 메서드를 바꿔 끼우던 곳(`vaultApi.get = async () => ...`)은 *Hook 변수로 바꾼다

val NOW: Instant = Instant.parse("2026-10-31T00:00:00Z")
private val vector = Vectors.keyCases[0]
val PASSWORD: String = vector.text("password")
val fields = ItemFields(serviceName = "GitHub", username = "octocat", password = "pw-1")
val clockAtNow = object : Clock {
    override fun now(): Instant = NOW
}

/**
 * PBKDF2 60만 회를 여러 번 하므로 시간 한도를 넉넉히 둔다. 기본(Standard) 테스트 디스패처라 가짜의 yield()가
 * 테스트 본문에 차례를 넘긴다. 원본에서 await 사이에 잠금·로그아웃이 끼어들던 순서와 같다
 */
fun sessionTest(body: suspend TestScope.() -> Unit) = runTest(timeout = 10.minutes) { body() }

/**
 * 원본의 기다리지 않은 호출(`const p = session.unlock(...)`)처럼 첫 중단점까지 바로 실행하고, 결과·오류는 await로 받는다.
 * 실패가 테스트 전체를 취소하지 않도록 부모와 끊는다
 */
fun <T> TestScope.begin(block: suspend () -> T): Deferred<T> = async(SupervisorJob(), start = CoroutineStart.UNDISPATCHED) { block() }

/**
 * 조건이 참이 될 때까지 실제 시간으로 기다린다. 원본의 `await new Promise((r) => setTimeout(r, 0))`처럼 진행 중인 작업이
 * 게이트에 닿기를 기다릴 때 쓴다. JS 대상의 암호 연산은 실제 이벤트 루프에서 끝나므로 가상 시간으로는 기다릴 수 없다
 */
suspend fun waitFor(condition: () -> Boolean) {
    repeat(6000) {
        if (condition()) return
        yield()
        withContext(Dispatchers.Default) { delay(10) }
    }
    error("조건이 1분 안에 참이 되지 않음")
}

/** 풀릴 때까지 기다리게 하는 문. 몇 개가 기다리는지 센다 */
class Gate {
    private val opened = CompletableDeferred<Unit>()
    var waiting = 0
        private set

    suspend fun pass() {
        waiting += 1
        try {
            opened.await()
        } finally {
            waiting -= 1
        }
    }

    fun release() {
        opened.complete(Unit)
    }
}

open class FakeAuthApi : AuthApi {
    val registered = mutableListOf<RegisterRequest>()
    val loggedOut = mutableListOf<String>()
    var kdf = KdfParams(PBKDF2_SHA256, Vectors.iterations, vector.text("salt"))
    val logins = mutableListOf<LoginRequest>()
    var offline = false

    /** 비밀번호 변경이 함께 교체할 같은 서버의 볼트. setup이 넣는다 */
    var vaultApi: FakeVaultApi? = null

    /** 서버가 아는 인증 해시. 가입·로그인 요청의 값을 따르고, 비밀번호 변경은 이 값과 비교한다 */
    var authHash: String? = null
    val changes = mutableListOf<Pair<String, ChangePasswordRequest>>()

    /** 비밀번호 변경으로 폐기된 토큰 */
    val revoked = mutableSetOf<String>()

    /** 설정하면 비밀번호 변경을 이 오류로 거절한다 */
    var changeError: Throwable? = null

    /** 설정하면 비밀번호 변경 요청이 이 문이 열릴 때까지 기다린다 */
    var changeGate: Gate? = null
    val deletions = mutableListOf<Pair<String, DeleteAccountRequest>>()

    /** 설정하면 계정 삭제를 이 오류로 거절한다 */
    var deleteError: Throwable? = null

    /** 설정하면 계정 삭제 요청이 이 문이 열릴 때까지 기다린다 */
    var deleteGate: Gate? = null

    /** 계정을 지웠음. 지운 계정으로는 로그인할 수 없고 prelogin은 가짜 salt를 준다 */
    var deleted = false

    /** 설정하면 해당 요청을 기본 동작 대신 이 함수로 처리한다(원본 테스트의 메서드 바꿔 끼우기) */
    var preloginHook: (suspend (String) -> KdfParams)? = null
    var loginHook: (suspend (LoginRequest) -> Session)? = null
    var logoutHook: (suspend (String) -> Unit)? = null
    private var issued = 1

    private fun token() = "token-$issued"

    override suspend fun prelogin(email: String): KdfParams {
        yield()
        preloginHook?.let { return it(email) }
        if (offline) throw NetworkError()
        return kdf
    }

    override suspend fun register(request: RegisterRequest): Session {
        yield()
        registered += request
        deleted = false
        kdf = KdfParams(PBKDF2_SHA256, request.kdfIterations, request.kdfSalt)
        authHash = request.authHash
        return Session(token(), "2026-11-30T00:00:00Z")
    }

    override suspend fun login(request: LoginRequest): Session {
        yield()
        loginHook?.let { return it(request) }
        if (offline) throw NetworkError()
        logins += request
        if (deleted) throw ServerError(401, ServerErrorCode.AUTH_FAILED)
        authHash = request.authHash
        return Session(token(), "2026-11-30T00:00:00Z")
    }

    override suspend fun logout(accessToken: String) {
        yield()
        logoutHook?.let { return it(accessToken) }
        loggedOut += accessToken
    }

    /** 실제 서버처럼 현재 인증 해시와 revision을 확인한 뒤 KDF 값·인증 해시·볼트를 함께 바꾸고 이전 토큰을 모두 폐기한다 */
    override suspend fun changePassword(accessToken: String, request: ChangePasswordRequest): ChangedPassword {
        yield()
        if (offline) throw NetworkError()
        changes += accessToken to request
        changeGate?.pass()
        changeError?.let { throw it }
        if (accessToken in revoked) throw ServerError(401, ServerErrorCode.UNAUTHORIZED)
        if (authHash != null && request.currentAuthHash != authHash) throw ServerError(401, ServerErrorCode.AUTH_FAILED)
        val vault = vaultApi ?: error("비밀번호 변경에 쓸 볼트 API가 없음")
        if (request.vault.expectedRevision != vault.revision) throw ServerError(409, ServerErrorCode.VAULT_CONFLICT)
        vault.payload = request.vault.payload
        vault.revision += 1
        kdf = KdfParams(PBKDF2_SHA256, request.kdf.iterations, request.kdf.salt)
        authHash = request.newAuthHash
        for (n in 1..issued) revoked += "token-$n"
        issued += 1
        return ChangedPassword(token(), "2026-12-02T00:00:00Z", vault.revision)
    }

    /** 실제 서버처럼 인증 해시를 확인한 뒤 계정·볼트를 지우고 이 계정의 토큰을 모두 폐기한다 (api-spec 2.5.6) */
    override suspend fun deleteAccount(accessToken: String, request: DeleteAccountRequest) {
        yield()
        if (offline) throw NetworkError()
        deletions += accessToken to request
        deleteGate?.pass()
        deleteError?.let { throw it }
        if (deleted || accessToken in revoked) throw ServerError(401, ServerErrorCode.UNAUTHORIZED)
        if (authHash != null && request.authHash != authHash) throw ServerError(401, ServerErrorCode.AUTH_FAILED)
        deleted = true
        authHash = null
        // 없는 이메일의 prelogin은 서버 비밀키로 만든 가짜 salt를 준다 (api-spec 2.5.1)
        kdf = kdf.copy(salt = "ZmFrZS1zYWx0LTE2Ynl0ZQ==")
        for (n in 1..issued) revoked += "token-$n"
        // 다시 가입하면 폐기되지 않은 새 토큰을 받는다
        issued += 1
        vaultApi?.let {
            it.payload = null
            it.revision = 0
        }
    }
}

class FakeVaultApi : VaultApi {
    var revision = 0
    var payload: EncryptedVaultPayload? = null
    var saves = 0
    var offline = false

    /** 설정하면 저장 요청이 이 문이 열릴 때까지 기다린다 */
    var gate: Gate? = null

    /** 설정하면 불러오기·저장을 기본 동작 대신 이 함수로 처리한다(원본 테스트의 메서드 바꿔 끼우기) */
    var getHook: (suspend () -> RemoteVault?)? = null
    var saveHook: (suspend (Int, EncryptedVaultPayload) -> Int)? = null

    override suspend fun get(): RemoteVault? {
        yield()
        getHook?.let { return it() }
        if (offline) throw NetworkError()
        return payload?.let { RemoteVault(revision, it) }
    }

    override suspend fun save(expectedRevision: Int, payload: EncryptedVaultPayload): Int {
        yield()
        saveHook?.let { return it(expectedRevision, payload) }
        if (offline) throw NetworkError()
        gate?.pass()
        saves += 1
        if (expectedRevision != revision) throw ServerError(409, ServerErrorCode.VAULT_CONFLICT)
        this.payload = payload
        revision += 1
        return revision
    }
}

/** 문을 세워 저장 요청을 기다리게 한다. @return 문을 열고 치우는 함수 */
fun gateSaves(vaultApi: FakeVaultApi): () -> Unit {
    val gate = Gate()
    vaultApi.gate = gate
    return {
        vaultApi.gate = null
        gate.release()
    }
}

/** 메모리에 한 계정의 기록을 두는 기기 저장소. 원본처럼 실패는 DeviceStorageException으로 낸다 */
class FakeDeviceStore : DeviceRecordStore {
    /** 저장된 기록 JSON. 원본의 structuredClone처럼 읽을 때마다 새 객체로 해석된다 */
    var stored: String? = null
    var failing = false

    /** 남은 횟수만큼 읽기만 실패한다 (열기 시간 초과 등) */
    var failLoads = 0
    var writes = 0

    override suspend fun load(): String? {
        yield()
        if (failing || failLoads > 0) {
            failLoads = maxOf(0, failLoads - 1)
            throw DeviceStorageException(IllegalStateException("저장소 오류"))
        }
        return stored
    }

    override suspend fun save(recordJson: String) {
        yield()
        if (failing) throw DeviceStorageException(IllegalStateException("저장소 오류"))
        writes += 1
        stored = recordJson
    }

    override suspend fun clear() {
        yield()
        if (failing) throw DeviceStorageException(IllegalStateException("저장소 오류"))
        stored = null
    }

    val record: DeviceRecord?
        get() = parseDeviceRecord(stored)
}

/** 탭 하나의 기억(sessionStorage). 새로고침해도 남는다 */
class FakeTabMemory : TabMemory {
    var value: com.keymanage.domain.device.RememberedKdf? = null

    override fun get() = value

    override fun set(value: com.keymanage.domain.device.RememberedKdf) {
        this.value = value
    }

    override fun clear() {
        value = null
    }
}

class SessionSetup(
    val session: VaultSession,
    val authApi: FakeAuthApi,
    val vaultApi: FakeVaultApi,
    val deviceStore: FakeDeviceStore,
    val revisionMemory: RevisionMemory,
    private val lockCount: () -> Int,
    private val unlockCount: () -> Int,
) {
    fun locks() = lockCount()

    fun unlocks() = unlockCount()

    /** 잠금 해제 상태의 볼트. 아니면 테스트를 실패시킨다 */
    fun unlockedState(): SessionState.Unlocked =
        session.state() as? SessionState.Unlocked ?: error("잠금 해제 상태가 아님: ${session.state()}")
}

fun vaultSyncFor(vaultApi: VaultApi): (String, com.keymanage.domain.crypto.EncryptionKey, RevisionMemory) -> VaultSync =
    { _, encryptionKey, memory -> VaultSync(vaultApi, encryptionKey, clockAtNow, memory) }

/**
 * @param tabs 같은 브라우저의 다른 탭과 공유하는 잠금·알림. 없으면 탭 하나
 * @param tabMemory 이 탭만의 기억(새로고침해도 남음). 없으면 기억하지 않는다
 */
fun TestScope.setup(
    authApi: FakeAuthApi = FakeAuthApi(),
    vaultApi: FakeVaultApi = FakeVaultApi(),
    deviceStore: FakeDeviceStore = FakeDeviceStore(),
    tabs: Tabs = SingleTab(),
    tabMemory: TabMemory? = null,
    scope: CoroutineScope = backgroundScope,
    syncFor: (String, com.keymanage.domain.crypto.EncryptionKey, RevisionMemory) -> VaultSync = vaultSyncFor(vaultApi),
): SessionSetup {
    if (authApi.vaultApi == null) authApi.vaultApi = vaultApi
    val revisionMemory = RevisionMemory()
    var locks = 0
    var unlocks = 0
    val session = VaultSession(
        VaultSessionDeps(
            auth = AuthService(authApi),
            vaultSyncFor = syncFor,
            revisionMemory = revisionMemory,
            deviceStore = deviceStore,
            tabs = tabs,
            scope = scope,
            tabMemory = tabMemory,
        ),
    )
    session.onLock { locks++ }
    session.onUnlock { unlocks++ }
    return SessionSetup(session, authApi, vaultApi, deviceStore, revisionMemory, { locks }, { unlocks })
}

/** 같은 서버와 기기 저장소로 앱을 새로 연다(새로고침·재시작). 메모리 상태는 모두 사라진다 */
suspend fun TestScope.restart(previous: SessionSetup): SessionSetup {
    val next = setup(authApi = previous.authApi, vaultApi = previous.vaultApi, deviceStore = previous.deviceStore)
    next.session.restore()
    return next
}

suspend fun TestScope.unlockedWithItem(): SessionSetup {
    val ctx = setup()
    ctx.session.login("user@example.com", PASSWORD)
    ctx.session.save(addItem(ctx.unlockedState().vault.data, fields, NOW, "a"))
    return ctx
}

/** 다른 기기(자기 되돌림 기준을 가진 별도 세션)로 같은 서버 볼트를 연다 */
private suspend fun TestScope.openOnOtherDevice(vaultApi: FakeVaultApi): Pair<VaultSession, VaultData> {
    val other = setup(authApi = FakeAuthApi(), vaultApi = vaultApi)
    other.session.login("device-2@example.com", PASSWORD)
    return other.session to other.unlockedState().vault.data
}

/** 서버 볼트의 살아 있는 항목 id (정렬) */
suspend fun TestScope.serverItemIds(vaultApi: FakeVaultApi): List<String> =
    activeItems(openOnOtherDevice(vaultApi).second).map { it.id }.sorted()

/** 다른 기기에서 항목을 하나 더해 저장한다 */
suspend fun TestScope.saveFromOtherDevice(vaultApi: FakeVaultApi, id: String) {
    val (other, data) = openOnOtherDevice(vaultApi)
    other.save(addItem(data, fields.copy(serviceName = id), NOW, id))
}

fun activeIds(state: SessionState): List<String>? = (state as? SessionState.Unlocked)?.let { s -> activeItems(s.vault.data).map { it.id } }
