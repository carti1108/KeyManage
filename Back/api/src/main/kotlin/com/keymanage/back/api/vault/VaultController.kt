package com.keymanage.back.api.vault

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.keymanage.back.api.error.ApiError
import com.keymanage.back.api.error.ApiException
import com.keymanage.back.domain.vault.EncryptedVault
import com.keymanage.back.domain.vault.GetVaultUseCase
import com.keymanage.back.domain.vault.Revision
import com.keymanage.back.domain.vault.SaveVaultUseCase
import com.keymanage.back.domain.vault.VaultId
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 볼트 조회·저장 API (api-spec 2.3·2.4).
 *
 * 페이로드는 해석하지 않고 JSON 문자열로 바꿔 저장하고, 조회 때 그대로 JSON 객체로 돌려준다 (ADR-0001).
 * 인증(#10) 도입 전까지는 X-Vault-ID만으로 접근하므로 로컬 개발 전용이다.
 */
@RestController
@RequestMapping("/api/v1/vault")
class VaultController(
    private val getVault: GetVaultUseCase,
    private val saveVault: SaveVaultUseCase,
    private val objectMapper: ObjectMapper,
) {

    @GetMapping
    fun get(@RequestHeader("X-Vault-ID") vaultId: String): VaultResponse {
        val stored = getVault.execute(VaultId.of(vaultId)) ?: throw ApiException(ApiError.VAULT_NOT_FOUND)
        return VaultResponse(stored.id.value, stored.revision.value, objectMapper.readTree(stored.vault.payload))
    }

    @PostMapping
    fun save(@RequestBody request: SaveVaultRequest): SaveVaultResponse {
        val expectedRevision = request.expectedRevision
        if (expectedRevision == null || expectedRevision < 0 || !request.payload.isObject) {
            throw ApiException(ApiError.INVALID_PARAMETER)
        }
        val revision = saveVault.execute(
            VaultId.of(request.vaultId),
            Revision(expectedRevision),
            EncryptedVault(objectMapper.writeValueAsString(request.payload)),
        )
        return SaveVaultResponse(revision.value)
    }
}

/** 조회 응답 (api-spec 2.3) */
data class VaultResponse(val vaultId: String, val revision: Long, val payload: JsonNode)

/**
 * 저장 요청 (api-spec 2.4).
 * `expectedRevision`을 Long으로 받으면 빠졌을 때 0으로 채워져 "첫 저장"으로 처리되므로, null로 받아 빠진 것을 거절한다
 */
data class SaveVaultRequest(val vaultId: String, val expectedRevision: Long?, val payload: JsonNode)

/** 저장 응답 (api-spec 2.4) */
data class SaveVaultResponse(val revision: Long)
