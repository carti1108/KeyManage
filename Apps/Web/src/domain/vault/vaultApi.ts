// 볼트 서버 포트 (api-spec 2.3·2.4). 구현은 adapters가 맡고, 실패는 serverErrors의 오류로 알린다
import type { EncryptedVaultPayload } from '../crypto/vaultCipher'

export interface RemoteVault {
  readonly revision: number
  readonly payload: EncryptedVaultPayload
}

export interface VaultApi {
  /** @returns 저장된 볼트가 없으면 null */
  get(): Promise<RemoteVault | null>
  /**
   * @returns 저장된 볼트의 새 revision
   * @throws ServerError 409 VAULT_CONFLICT: expectedRevision이 서버 값과 다를 때
   */
  save(expectedRevision: number, payload: EncryptedVaultPayload): Promise<number>
}
