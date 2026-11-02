package com.keymanage.back.infrastructure.auth

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.keymanage.back.domain.auth.Email
import com.keymanage.back.domain.auth.Session
import com.keymanage.back.domain.vault.VaultId
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 세션 파일이 볼트 ID를 담고, 계정 세션 일괄 삭제가 파일 하나의 실패에 멈추지 않는다 (SEC-004, ADR-BE-001 결정 6) */
class FileSystemSessionVaultTests {

    @TempDir
    lateinit var root: Path

    private val email = Email.of("user@example.com")
    private val vaultId = VaultId.of("550e8400-e29b-41d4-a716-446655440000")
    private val expiresAt = Instant.parse("2026-11-27T00:00:00Z")

    private fun dir(): Path = root.resolve("sessions")

    @Test
    fun `세션의 볼트 ID를 저장하고 다시 읽는다`() {
        val session = Session("a".repeat(64), email, expiresAt, vaultId)
        FileSystemSessionRepository(dir()).save(session)

        assertEquals(vaultId, FileSystemSessionRepository(dir()).find(session.tokenHash)?.vaultId)
    }

    @Test
    fun `볼트 ID가 없는 예전 형식의 세션 파일은 무효로 본다`() {
        Files.createDirectories(dir())
        dir().resolve("b".repeat(64) + ".json").writeText("""{"email":"user@example.com","expiresAt":"2026-11-27T00:00:00Z"}""")

        assertNull(FileSystemSessionRepository(dir()).find("b".repeat(64)))
    }

    @Test
    fun `파일 하나를 지우지 못해도 나머지 계정 세션을 끝까지 지운다`() {
        val sessions = listOf("c", "d", "e").map { Session(it.repeat(64), email, expiresAt, vaultId) }
        val writer = FileSystemSessionRepository(dir())
        sessions.forEach(writer::save)
        val stuck = dir().resolve("d".repeat(64) + ".json")
        val repository = FileSystemSessionRepository(dir(), jacksonObjectMapper()) { file ->
            if (file == stuck) throw IOException("파일 잠김")
            Files.deleteIfExists(file)
        }

        repository.deleteAllOf(email)

        assertNull(writer.find("c".repeat(64)))
        assertNull(writer.find("e".repeat(64)))
        assertEquals(email, writer.find("d".repeat(64))?.email)
    }
}
