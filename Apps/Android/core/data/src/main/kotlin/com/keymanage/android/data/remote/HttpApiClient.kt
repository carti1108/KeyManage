package com.keymanage.android.data.remote

import com.keymanage.android.domain.crypto.EncryptedVaultPayload
import com.keymanage.android.domain.remote.ApiError
import com.keymanage.android.domain.remote.AuthApi
import com.keymanage.android.domain.remote.ChangePasswordRequest
import com.keymanage.android.domain.remote.ChangePasswordResult
import com.keymanage.android.domain.remote.PreloginInfo
import com.keymanage.android.domain.remote.RemoteVault
import com.keymanage.android.domain.remote.ServerErrorCode
import com.keymanage.android.domain.remote.Session
import com.keymanage.android.domain.remote.VaultApi
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Base64

/**
 * api-spec.md의 HTTP 어댑터. 외부 라이브러리 없이 HttpURLConnection과 org.json을 쓴다.
 * localhost·127.0.0.1이 아닌 서버는 HTTPS만 허용한다(SEC-002). 제한 시간은 30초다(VLT-001).
 *
 * @param baseUrl 예: `https://example.com` (경로 `/api/v1`은 엔드포인트가 붙인다)
 */
class HttpApiClient(
    baseUrl: String,
    private val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) : VaultApi, AuthApi {
    private val base: String = baseUrl.trimEnd('/')
    private val encoder = Base64.getEncoder()

    init {
        val uri = URI(base)
        val local = uri.host == "localhost" || uri.host == "127.0.0.1"
        if (uri.scheme != "https" && !(uri.scheme == "http" && local)) throw ApiError.InsecureServer(base)
    }

    override fun fetchVault(accessToken: String): RemoteVault? {
        val response = try {
            call(ApiEndpoints.GET_VAULT, accessToken, body = null)
        } catch (error: ApiError.Server) {
            if (error.code == ServerErrorCode.VAULT_NOT_FOUND) return null
            throw error
        }
        return RemoteVault(response.requireInt("revision"), payloadOf(response.requireObject("payload")))
    }

    override fun saveVault(accessToken: String, expectedRevision: Int, payload: EncryptedVaultPayload): Int {
        val body = """{"expectedRevision":$expectedRevision,"payload":${payload.canonicalJson()}}"""
        return call(ApiEndpoints.SAVE_VAULT, accessToken, body).requireInt("revision")
    }

    override fun prelogin(email: String): PreloginInfo {
        val response = call(ApiEndpoints.PRELOGIN, null, JSONObject().put("email", email).toString())
        return PreloginInfo(response.requireString("kdf"), response.requireInt("iterations"), decode(response.requireString("salt")))
    }

    override fun register(email: String, authHash: ByteArray, kdfSalt: ByteArray, kdfIterations: Int): Session {
        val body = JSONObject()
            .put("email", email)
            .put("authHash", encoder.encodeToString(authHash))
            .put("kdfSalt", encoder.encodeToString(kdfSalt))
            .put("kdfIterations", kdfIterations)
        return sessionOf(call(ApiEndpoints.REGISTER, null, body.toString()))
    }

    override fun login(email: String, authHash: ByteArray): Session {
        val body = JSONObject().put("email", email).put("authHash", encoder.encodeToString(authHash))
        return sessionOf(call(ApiEndpoints.LOGIN, null, body.toString()))
    }

    override fun logout(accessToken: String) {
        call(ApiEndpoints.LOGOUT, accessToken, body = null)
    }

    override fun changePassword(request: ChangePasswordRequest): ChangePasswordResult {
        val body = """{"currentAuthHash":"${encoder.encodeToString(request.currentAuthHash)}",""" +
            """"newAuthHash":"${encoder.encodeToString(request.newAuthHash)}",""" +
            """"kdf":{"iterations":${request.kdfIterations},"salt":"${encoder.encodeToString(request.kdfSalt)}"},""" +
            """"vault":{"expectedRevision":${request.expectedRevision},"payload":${request.payload.canonicalJson()}}}"""
        val response = call(ApiEndpoints.CHANGE_PASSWORD, request.accessToken, body)
        return ChangePasswordResult(sessionOf(response), response.requireInt("revision"))
    }

    override fun deleteAccount(accessToken: String, authHash: ByteArray) {
        val body = JSONObject().put("authHash", encoder.encodeToString(authHash))
        call(ApiEndpoints.DELETE_ACCOUNT, accessToken, body.toString())
    }

    /** 요청을 보내고 2xx 본문을 객체로 돌려준다. 본문이 없으면 빈 객체. 4xx·5xx는 [ApiError.Server]. */
    private fun call(endpoint: Endpoint, accessToken: String?, body: String?): JSONObject {
        val connection = try {
            URI(base + endpoint.path).toURL().openConnection() as HttpURLConnection
        } catch (error: IOException) {
            throw ApiError.Network(error)
        }
        try {
            connection.requestMethod = endpoint.method
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.setRequestProperty("Accept", JSON)
            if (endpoint.requiresToken) {
                connection.setRequestProperty("Authorization", "Bearer ${requireNotNull(accessToken)}")
            }
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "$JSON; charset=UTF-8")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val text = (if (status >= 400) connection.errorStream else connection.inputStream)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                .orEmpty()
            if (status >= 400) throw serverError(status, text, connection.getHeaderField("Retry-After"))
            return if (text.isBlank()) JSONObject() else parseObject(text)
        } catch (error: IOException) {
            throw ApiError.Network(error)
        } finally {
            connection.disconnect()
        }
    }

    private fun serverError(status: Int, text: String, retryAfter: String?): ApiError.Server {
        val json = runCatching { parseObject(text) }.getOrNull()
        return ApiError.Server(
            status = status,
            code = ServerErrorCode.of(json?.opt("code") as? String),
            serverMessage = json?.opt("message") as? String,
            retryAfterSeconds = retryAfter?.trim()?.toLongOrNull(),
        )
    }

    private fun parseObject(text: String): JSONObject = try {
        JSONTokener(text).nextValue() as? JSONObject ?: throw ApiError.InvalidResponse("응답이 JSON 객체가 아니다")
    } catch (error: JSONException) {
        throw ApiError.InvalidResponse("응답 JSON을 읽을 수 없다")
    }

    private fun sessionOf(json: JSONObject): Session {
        val expiresAt = json.requireString("expiresAt")
        val instant: Instant = try {
            OffsetDateTime.parse(expiresAt).toInstant()
        } catch (error: DateTimeParseException) {
            throw ApiError.InvalidResponse("expiresAt 형식 오류")
        }
        return Session(json.requireString("accessToken"), instant)
    }

    private fun payloadOf(json: JSONObject) = EncryptedVaultPayload(
        version = json.requireInt("version"),
        cipher = json.requireString("cipher"),
        iv = json.requireString("iv"),
        ciphertext = json.requireString("ciphertext"),
        tag = json.requireString("tag"),
    )

    private fun decode(base64: String): ByteArray = try {
        Base64.getDecoder().decode(base64)
    } catch (error: IllegalArgumentException) {
        throw ApiError.InvalidResponse("Base64 형식 오류")
    }

    private fun JSONObject.requireInt(key: String): Int = opt(key) as? Int ?: throw ApiError.InvalidResponse("$key 정수 아님")
    private fun JSONObject.requireString(key: String): String = opt(key) as? String ?: throw ApiError.InvalidResponse("$key 문자열 아님")
    private fun JSONObject.requireObject(key: String): JSONObject = opt(key) as? JSONObject ?: throw ApiError.InvalidResponse("$key 객체 아님")

    private companion object {
        const val JSON = "application/json"
        const val DEFAULT_TIMEOUT_MILLIS = 30_000
    }
}
