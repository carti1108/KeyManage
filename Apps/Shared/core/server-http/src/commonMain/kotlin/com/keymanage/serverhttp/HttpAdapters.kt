package com.keymanage.serverhttp

import com.keymanage.domain.auth.AuthApi
import com.keymanage.domain.auth.ChangePasswordRequest
import com.keymanage.domain.auth.ChangedPassword
import com.keymanage.domain.auth.DeleteAccountRequest
import com.keymanage.domain.auth.KdfParams
import com.keymanage.domain.auth.LoginRequest
import com.keymanage.domain.auth.RegisterRequest
import com.keymanage.domain.auth.Session
import com.keymanage.domain.crypto.EncryptedVaultPayload
import com.keymanage.domain.crypto.parseEncryptedVaultPayload
import com.keymanage.domain.server.ServerError
import com.keymanage.domain.server.ServerErrorCode
import com.keymanage.domain.server.UnexpectedResponseError
import com.keymanage.domain.vault.RemoteVault
import com.keymanage.domain.vault.VaultApi
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs

// AuthApi·VaultApi 포트의 HTTP 구현 (api-spec 2.3·2.4·2.5). 원본: httpAuthApi.ts, httpVaultApi.ts, responseBody.ts
// 서버 응답도 믿지 않는다. 필요한 필드의 타입만 확인해 꺼내고, 값의 규격 검사는 domain이 한다

// JS Number.MAX_SAFE_INTEGER. 원본의 Number.isSafeInteger 검사 범위
private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991.0

class HttpAuthApi(private val client: ApiClient) : AuthApi {
    override suspend fun prelogin(email: String): KdfParams =
        toKdfParams(client.request(HttpMethod.Post, "/auth/prelogin", obj("email" to text(email))))

    override suspend fun register(request: RegisterRequest): Session {
        val body = obj(
            "email" to text(request.email),
            "authHash" to text(request.authHash),
            "kdfSalt" to text(request.kdfSalt),
            "kdfIterations" to JsonPrimitive(request.kdfIterations),
        )
        return toSession(client.request(HttpMethod.Post, "/auth/register", body))
    }

    override suspend fun login(request: LoginRequest): Session =
        toSession(client.request(HttpMethod.Post, "/auth/login", obj("email" to text(request.email), "authHash" to text(request.authHash))))

    override suspend fun logout(accessToken: String) {
        client.request(HttpMethod.Post, "/auth/logout", accessToken = accessToken)
    }

    override suspend fun changePassword(accessToken: String, request: ChangePasswordRequest): ChangedPassword {
        val body = obj(
            "currentAuthHash" to text(request.currentAuthHash),
            "newAuthHash" to text(request.newAuthHash),
            "kdf" to obj("salt" to text(request.kdf.salt), "iterations" to JsonPrimitive(request.kdf.iterations)),
            "vault" to vaultBody(request.vault.expectedRevision, request.vault.payload),
        )
        return toChangedPassword(client.request(HttpMethod.Post, "/auth/change-password", body, accessToken))
    }

    override suspend fun deleteAccount(accessToken: String, request: DeleteAccountRequest) {
        client.request(HttpMethod.Post, "/auth/delete-account", obj("authHash" to text(request.authHash)), accessToken)
    }
}

class HttpVaultApi(private val client: ApiClient, private val accessToken: String) : VaultApi {
    override suspend fun get(): RemoteVault? = try {
        toRemoteVault(client.request(HttpMethod.Get, "/vault", accessToken = accessToken))
    } catch (error: ServerError) {
        if (error.code == ServerErrorCode.VAULT_NOT_FOUND) null else throw error
    }

    override suspend fun save(expectedRevision: Int, payload: EncryptedVaultPayload): Int =
        toRevision(asRecord(client.request(HttpMethod.Post, "/vault", vaultBody(expectedRevision, payload), accessToken))["revision"])
}

private fun obj(vararg entries: Pair<String, JsonElement>) = JsonObject(linkedMapOf(*entries))

private fun text(value: String) = JsonPrimitive(value)

private fun vaultBody(expectedRevision: Int, payload: EncryptedVaultPayload) =
    obj("expectedRevision" to JsonPrimitive(expectedRevision), "payload" to payload.toJson())

private fun asRecord(body: JsonElement?): JsonObject = body as? JsonObject ?: throw UnexpectedResponseError("응답 본문이 객체가 아님")

private fun string(value: JsonElement?): String? = (value as? JsonPrimitive)?.takeIf { it.isString }?.content

/** JSON 숫자이고 안전한 정수면 그 값. 원본의 typeof number·Number.isSafeInteger 검사에 해당한다 */
private fun integer(value: JsonElement?): Long? {
    val primitive = (value as? JsonPrimitive)?.takeIf { !it.isString } ?: return null
    val number = primitive.content.toDoubleOrNull() ?: return null
    if (number % 1.0 != 0.0 || abs(number) > MAX_SAFE_INTEGER) return null
    return number.toLong()
}

private fun toKdfParams(body: JsonElement?): KdfParams {
    val record = asRecord(body)
    val kdf = string(record["kdf"])
    // 원본은 숫자인지만 봤다. Kotlin 포트는 Int만 담으므로 Int 범위의 정수가 아니면 형식 오류로 본다(규격 검사는 domain이 한다)
    val iterations = integer(record["iterations"])?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
    val salt = string(record["salt"])
    if (kdf == null || iterations == null || salt == null) throw UnexpectedResponseError("prelogin 응답 형식이 틀림")
    return KdfParams(kdf, iterations, salt)
}

private fun toSession(body: JsonElement?): Session {
    val record = asRecord(body)
    val accessToken = string(record["accessToken"])
    val expiresAt = string(record["expiresAt"])
    if (accessToken.isNullOrEmpty() || expiresAt == null) throw UnexpectedResponseError("세션 응답 형식이 틀림")
    return Session(accessToken, expiresAt)
}

private fun toChangedPassword(body: JsonElement?): ChangedPassword {
    val session = toSession(body)
    return ChangedPassword(session.accessToken, session.expiresAt, toRevision(asRecord(body)["revision"]))
}

/** 페이로드 필드 검사는 parseEncryptedVaultPayload가 한다(원본은 복호화할 때 했다). 여기서는 모양만 확인한다 */
private fun toRemoteVault(body: JsonElement?): RemoteVault {
    val record = asRecord(body)
    val revision = toRevision(record["revision"])
    val payload = record["payload"] as? JsonObject ?: throw UnexpectedResponseError("볼트 페이로드가 객체가 아님")
    return RemoteVault(revision, parseEncryptedVaultPayload(payload))
}

private fun toRevision(value: JsonElement?): Int =
    integer(value)?.takeIf { it in 0..Int.MAX_VALUE }?.toInt() ?: throw UnexpectedResponseError("revision이 0 이상의 정수가 아님")
