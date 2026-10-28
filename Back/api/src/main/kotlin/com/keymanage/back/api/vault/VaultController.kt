package com.keymanage.back.api.vault

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.keymanage.back.api.auth.AuthTokenFilter
import com.keymanage.back.api.error.ApiError
import com.keymanage.back.api.error.ApiException
import com.keymanage.back.domain.auth.Account
import com.keymanage.back.domain.vault.EncryptedVault
import com.keymanage.back.domain.vault.GetVaultUseCase
import com.keymanage.back.domain.vault.Revision
import com.keymanage.back.domain.vault.SaveVaultUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 로그인한 계정의 볼트 조회·저장 API (api-spec 2.3·2.4).
 *
 * 볼트는 [AuthTokenFilter]가 토큰으로 찾은 계정의 볼트로 정해지므로 다른 계정의 볼트는 지정할 수 없다 (ADR-0008).
 * 페이로드는 해석하지 않고 JSON 문자열로 바꿔 저장하고, 조회 때 그대로 JSON 객체로 돌려준다 (ADR-0001).
 */
@RestController
@RequestMapping("/api/v1/vault")
class VaultController(
    private val getVault: GetVaultUseCase,
    private val saveVault: SaveVaultUseCase,
    private val objectMapper: ObjectMapper,
) {

    @GetMapping
    fun get(@RequestAttribute(AuthTokenFilter.ACCOUNT_ATTRIBUTE) account: Account): VaultResponse {
        val stored = getVault.execute(account.vaultId) ?: throw ApiException(ApiError.VAULT_NOT_FOUND)
        return VaultResponse(stored.revision.value, objectMapper.readTree(stored.vault.payload))
    }

    @PostMapping
    fun save(
        @RequestAttribute(AuthTokenFilter.ACCOUNT_ATTRIBUTE) account: Account,
        @RequestBody request: SaveVaultRequest,
    ): SaveVaultResponse {
        val expectedRevision = request.expectedRevision
        if (expectedRevision == null || expectedRevision < 0 || !request.payload.isObject) {
            throw ApiException(ApiError.INVALID_PARAMETER)
        }
        val revision = saveVault.execute(
            account.vaultId,
            Revision(expectedRevision),
            EncryptedVault(objectMapper.writeValueAsString(request.payload)),
        )
        return SaveVaultResponse(revision.value)
    }
}

/** 조회 응답 (api-spec 2.3) */
data class VaultResponse(val revision: Long, val payload: JsonNode)

/**
 * 저장 요청 (api-spec 2.4).
 * `expectedRevision`을 Long으로 받으면 빠졌을 때 0으로 채워져 "첫 저장"으로 처리되므로, null로 받아 빠진 것을 거절한다
 */
data class SaveVaultRequest(val expectedRevision: Long?, val payload: JsonNode)

/** 저장 응답 (api-spec 2.4) */
data class SaveVaultResponse(val revision: Long)
