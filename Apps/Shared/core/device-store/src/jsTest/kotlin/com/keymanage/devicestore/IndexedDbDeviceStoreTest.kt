package com.keymanage.devicestore

import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IndexedDbDeviceStoreTest {
    private val record = """{"version":1,"email":"a@example.com","revision":3,"lastSeenRevision":3,"pendingSync":false,""" +
        """"payload":{"iv":"AAAAAAAAAAAAAAAA","ciphertext":"Y2lwaGVy"}}"""

    @AfterTest
    fun cleanUp() = runTest { IndexedDbDeviceStore().clear() }

    @Test
    fun load_returnsNullWhenEmpty() = runTest {
        assertNull(IndexedDbDeviceStore().load())
    }

    @Test
    fun save_thenLoad_returnsSameRecord() = runTest {
        IndexedDbDeviceStore().save(record)
        assertEquals(JSON.stringify(JSON.parse(record)), IndexedDbDeviceStore().load())
    }

    @Test
    fun save_overwritesPreviousRecord() = runTest {
        val store = IndexedDbDeviceStore()
        store.save(record)
        store.save(record.replace("\"revision\":3", "\"revision\":4"))
        assertEquals(4, JSON.parse<dynamic>(store.load()!!).revision as Int)
    }

    @Test
    fun clear_removesRecord() = runTest {
        val store = IndexedDbDeviceStore()
        store.save(record)
        store.clear()
        assertNull(store.load())
    }

    // 지금 웹 앱(indexedDbDeviceStore.ts)이 쓴 기록을 그대로 읽는다: 같은 DB·저장소·키, 객체로 저장
    @Test
    fun load_readsRecordWrittenByExistingWebApp() = runTest {
        writeLikeExistingWebApp(JSON.parse(record))
        assertEquals("a@example.com", JSON.parse<dynamic>(IndexedDbDeviceStore().load()!!).email as String)
    }

    // 반대로 Kotlin이 쓴 기록은 웹 앱처럼 객체로 읽혀야 한다(문자열로 저장하지 않음)
    @Test
    fun save_storesStructuredObjectLikeExistingWebApp() = runTest {
        IndexedDbDeviceStore().save(record)
        val raw = readLikeExistingWebApp()
        assertEquals("object", jsTypeOf(raw))
        assertEquals(3, raw.revision as Int)
    }

    private suspend fun writeLikeExistingWebApp(value: dynamic) = rawTransaction("readwrite") { it.put(value, "account") }
    private suspend fun readLikeExistingWebApp(): dynamic = rawTransaction("readonly") { it.get("account") }

    private suspend fun rawTransaction(mode: String, action: (dynamic) -> dynamic): dynamic = Promise<dynamic> { resolve, reject ->
        val open = js("indexedDB").open("keymanage", 1)
        open.onupgradeneeded = { open.result.createObjectStore("device") }
        open.onerror = { reject(Error("open")) }
        open.onsuccess = {
            val db = open.result
            val request = action(db.transaction("device", mode).objectStore("device"))
            request.onsuccess = { db.close(); resolve(request.result) }
            request.onerror = { db.close(); reject(Error("request")) }
        }
    }.await()
}
