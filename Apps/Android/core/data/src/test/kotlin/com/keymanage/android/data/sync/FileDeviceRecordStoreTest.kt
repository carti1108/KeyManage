package com.keymanage.android.data.sync

import com.keymanage.android.domain.crypto.EncryptedVaultPayload
import com.keymanage.android.domain.sync.DeviceAccount
import com.keymanage.android.domain.sync.DeviceRecord
import com.keymanage.android.domain.sync.DeviceStorageError
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FileDeviceRecordStoreTest {
    private val dir: File = Files.createTempDirectory("km-store").toFile()
    private val file = File(dir, "device-record.json")
    private val store = FileDeviceRecordStore(file)
    private val record = DeviceRecord(
        account = DeviceAccount("a@b.c", 600_000, "AAECAwQFBgcICQoLDA0ODw=="),
        payload = EncryptedVaultPayload(1, "AES-256-GCM", "aXY=", "Y3Q=", "dGFn"),
        baseRevision = 3,
        lastSeenRevision = 5,
        hasPending = true,
    )

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `쓴 기록을 새 저장소 인스턴스가 그대로 읽는다`() {
        // When
        store.write(record)

        // Then
        assertEquals(record, FileDeviceRecordStore(file).read())
    }

    @Test
    fun `기록이 없으면 null`() {
        assertNull(store.read())
    }

    @Test
    fun `다시 쓰면 한 계정 기록으로 교체하고 임시 파일을 남기지 않는다`() {
        // When
        store.write(record)
        store.write(record.copy(account = record.account.copy(email = "x@y.z"), hasPending = false))

        // Then
        assertEquals("x@y.z", store.read()?.account?.email)
        assertEquals(listOf(file.name), dir.list()!!.toList())
    }

    @Test
    fun `지우면 파일이 사라지고 없는 파일을 지워도 성공한다`() {
        store.write(record)
        store.clear()
        assertFalse(file.exists())
        store.clear()
    }

    @Test
    fun `깨진 기록은 DeviceStorageError`() {
        file.writeText("{not json")
        assertFailsWith<DeviceStorageError> { store.read() }
    }

    @Test
    fun `쓸 수 없는 위치면 DeviceStorageError`() {
        val blocked = FileDeviceRecordStore(File(File(dir, "missing-file-as-dir").apply { writeText("x") }, "r.json"))
        assertFailsWith<DeviceStorageError> { blocked.write(record) }
    }
}
