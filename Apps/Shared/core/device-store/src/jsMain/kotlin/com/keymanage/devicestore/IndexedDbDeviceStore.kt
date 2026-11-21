package com.keymanage.devicestore

import kotlinx.coroutines.await
import kotlin.js.Promise

// 지금 웹 앱(Apps/Web/src/adapters/browser/indexedDbDeviceStore.ts)과 같은 DB·저장소·키를 쓴다.
// 기록은 문자열이 아니라 객체로 저장해 기존 웹 앱이 쓴 기록과 서로 읽을 수 있게 한다
private const val DATABASE_NAME = "keymanage"
private const val DATABASE_VERSION = 1
private const val STORE_NAME = "device"

// 기기에는 한 계정만 두므로 키는 하나다
private const val RECORD_KEY = "account"

/** 열기가 끝나지 않으면(일부 브라우저의 멈춤, 다른 탭의 차단) 실패로 본다 */
const val OPEN_TIMEOUT_MS = 3_000

class IndexedDbDeviceStore(
    private val factory: dynamic = js("globalThis.indexedDB"),
) : DeviceStore {
    private var database: Promise<dynamic>? = null

    override suspend fun load(): String? {
        val value = run { db ->
            request(db.transaction(STORE_NAME, "readonly").objectStore(STORE_NAME).get(RECORD_KEY))
        }
        return if (value == null || value == undefined) null else JSON.stringify(value)
    }

    override suspend fun save(recordJson: String) {
        val record = try {
            JSON.parse<dynamic>(recordJson)
        } catch (error: Throwable) {
            throw IllegalArgumentException("기기 기록이 JSON이 아님", error)
        }
        write { store -> store.put(record, RECORD_KEY) }
    }

    override suspend fun clear() = write { store -> store.delete(RECORD_KEY) }

    private suspend fun connection(): dynamic {
        val opening = database ?: openDatabase().also { database = it }
        return try {
            opening.await()
        } catch (error: Throwable) {
            // 열기에 실패했으면 다음 호출에서 다시 연다
            database = null
            throw DeviceStorageException("기기 저장소를 열지 못함", error)
        }
    }

    private suspend fun <T> run(work: (dynamic) -> Promise<T>): T {
        val db = connection()
        return try {
            work(db).await()
        } catch (error: Throwable) {
            // 트랜잭션 실패(저장 공간 부족 등)는 연결을 버리지 않는다
            throw DeviceStorageException("기기 저장소를 쓰지 못함", error)
        }
    }

    /** 쓰기는 트랜잭션이 끝나야(디스크에 반영돼야) 성공으로 본다 */
    private suspend fun write(change: (dynamic) -> Unit) {
        run { db ->
            val transaction = db.transaction(STORE_NAME, "readwrite")
            change(transaction.objectStore(STORE_NAME))
            completed(transaction)
        }
    }

    private fun openDatabase(): Promise<dynamic> = Promise { resolve, reject ->
        var settled = false
        var timer: dynamic = null
        fun finish(outcome: () -> Unit) {
            if (settled) return
            settled = true
            js("clearTimeout")(timer)
            outcome()
        }
        timer = js("setTimeout")({ finish { reject(Error("기기 저장소를 여는 데 시간이 너무 걸림")) } }, OPEN_TIMEOUT_MS)
        val request = factory.open(DATABASE_NAME, DATABASE_VERSION)
        request.onupgradeneeded = {
            val db = request.result
            if (!(db.objectStoreNames.contains(STORE_NAME) as Boolean)) db.createObjectStore(STORE_NAME)
        }
        request.onblocked = { finish { reject(Error("다른 탭이 기기 저장소의 이전 버전을 쓰고 있음")) } }
        request.onerror = { finish { reject(Error("기기 저장소 열기 실패: ${request.error}")) } }
        request.onsuccess = {
            val db = request.result
            if (settled) {
                // 제한 시간이 지난 뒤에 열렸으면 쓰지 않으니 바로 닫는다
                db.close()
            } else {
                // 다른 탭이 새 버전으로 올리려 하면 막지 않도록 닫고, 다음 호출 때 다시 연다
                db.onversionchange = {
                    db.close()
                    database = null
                }
                finish { resolve(db) }
            }
        }
    }
}

private fun request(request: dynamic): Promise<dynamic> = Promise { resolve, reject ->
    request.onsuccess = { resolve(request.result) }
    request.onerror = { reject(Error("요청 실패: ${request.error}")) }
}

private fun completed(transaction: dynamic): Promise<Unit> = Promise { resolve, reject ->
    transaction.oncomplete = { resolve(Unit) }
    transaction.onerror = { reject(Error("트랜잭션 실패: ${transaction.error}")) }
    transaction.onabort = { reject(Error("트랜잭션 중단: ${transaction.error}")) }
}
