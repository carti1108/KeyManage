package com.keymanage.devicestore

import com.keymanage.domain.device.DeviceStorageException
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class FileDeviceRecordStoreTest {
    private val dir: File = Files.createTempDirectory("device-store").toFile()
    private val file = File(dir, "device-record.json")
    private val store = FileDeviceRecordStore(file)

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `기록이 없으면 null을 돌려준다`() = runTest {
        assertNull(store.load())
    }

    @Test
    fun `저장한 기록을 그대로 읽는다`() = runTest {
        val record = """{"version":1,"email":"사용자@example.com","pendingSync":false}"""

        store.save(record)

        assertEquals(record, store.load())
        assertEquals(record, FileDeviceRecordStore(file).load())
    }

    @Test
    fun `다시 저장하면 이전 기록을 덮어쓴다`() = runTest {
        store.save("""{"version":1,"revision":1}""")

        store.save("""{"version":1,"revision":2}""")

        assertEquals("""{"version":1,"revision":2}""", store.load())
    }

    @Test
    fun `저장한 뒤 임시 파일을 남기지 않는다`() = runTest {
        store.save("""{"version":1}""")

        assertEquals(listOf("device-record.json"), dir.list()!!.toList())
    }

    @Test
    fun `지우면 기록이 없어진다`() = runTest {
        store.save("""{"version":1}""")

        store.clear()

        assertNull(store.load())
        assertFalse(file.exists())
    }

    @Test
    fun `기록이 없을 때 지워도 실패하지 않는다`() = runTest {
        store.clear()

        assertNull(store.load())
    }

    @Test
    fun `쓸 수 없는 위치면 DeviceStorageException을 던진다`() = runTest {
        val notADirectory = File(dir, "plain-file").apply { writeText("x") }
        val broken = FileDeviceRecordStore(File(notADirectory, "device-record.json"))

        assertFailsWith<DeviceStorageException> { broken.save("""{"version":1}""") }
    }

    @Test
    fun `읽을 수 없으면 DeviceStorageException을 던진다`() = runTest {
        file.mkdirs()

        assertFailsWith<DeviceStorageException> { store.load() }
    }

    @Test
    fun `쓰기에 실패해도 이전 기록은 남는다`() = runTest {
        store.save("""{"version":1,"revision":1}""")
        // 임시 파일 자리에 비어 있지 않은 폴더를 두어 쓰기를 실패시킨다
        File(dir, "device-record.json.tmp").apply { mkdirs() }.resolve("blocker").writeText("x")

        assertFailsWith<DeviceStorageException> { store.save("""{"version":1,"revision":2}""") }

        assertEquals("""{"version":1,"revision":1}""", store.load())
    }
}
