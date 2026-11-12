package com.keymanage.domain.crypto

import com.keymanage.domain.encoding.fromBase64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// 테스트 전용: 플랫폼 간 테스트 벡터와, 60만 회 PBKDF2를 케이스마다 한 번만 하도록 캐시한 키 유도
// 원본: Apps/Web/src/domain/crypto/testVectors.ts
internal object Vectors {
    val root: JsonObject = Json.parseToJsonElement(TEST_VECTORS_JSON).jsonObject
    val iterations: Int = root.getValue("kdf").jsonObject.getValue("iterations").jsonPrimitive.int
    val keyCases: List<JsonObject> = root.getValue("keyDerivationCases").jsonArray.map { it.jsonObject }
    val encryptionCases: List<JsonObject> = root.getValue("encryptionCases").jsonArray.map { it.jsonObject }

    private val cache = mutableMapOf<String, DerivedKeys>()
    private val lock = Mutex()

    suspend fun keysOf(name: String): DerivedKeys = lock.withLock {
        cache.getOrPut(name) {
            val case = keyCases.firstOrNull { it.text("name") == name } ?: error("테스트 벡터 케이스 없음: $name")
            deriveKeys(case.text("password"), fromBase64(case.text("salt")), iterations)
        }.let { DerivedKeys(it.encryptionKey, it.authHash.copyOf()) }
    }
}

internal fun JsonObject.text(field: String): String = getValue(field).jsonPrimitive.content
