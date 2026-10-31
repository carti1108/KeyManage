import { describe, expect, it } from 'vitest'
import { InvalidEmailError } from '../../domain/auth/authService'
import { InvalidPasswordError } from '../../domain/crypto/keys'
import { NetworkError, ServerError } from '../../domain/server/serverErrors'
import { InvalidVaultDataError } from '../../domain/vault/vaultData'
import { VaultSyncConflictError } from '../../domain/vault/vaultSync'
import { SessionExpiredError, VaultSetupError, WrongMasterPasswordError } from '../../domain/session/vaultSession'
import { describeError } from './errorMessage'

describe('describeError', () => {
  it.each([
    [new ServerError(401, 'AUTH_FAILED'), '이메일 또는 마스터 비밀번호가 올바르지 않습니다.'],
    [new ServerError(401, 'UNAUTHORIZED'), '로그인이 만료되었습니다. 다시 로그인해 주세요.'],
    [new SessionExpiredError(), '로그인이 만료되었습니다. 다시 로그인해 주세요.'],
    [new ServerError(429, 'TOO_MANY_ATTEMPTS'), '로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.'],
    [new ServerError(409, 'EMAIL_ALREADY_REGISTERED'), '이미 가입된 이메일입니다.'],
    [new ServerError(403, 'REGISTRATION_DISABLED'), '현재 새 계정을 만들 수 없습니다.'],
    [new InvalidEmailError(), '올바른 이메일 주소를 입력해 주세요.'],
    [new InvalidPasswordError('깨진 문자'), '마스터 비밀번호에 쓸 수 없는 문자가 있습니다.'],
    [new NetworkError(), '서버에 연결할 수 없습니다. 네트워크 연결을 확인해 주세요.'],
    [new WrongMasterPasswordError(), '마스터 비밀번호가 올바르지 않습니다.'],
    [new VaultSetupError(new NetworkError()), '계정은 만들었지만 볼트를 준비하지 못했습니다. 다시 로그인해 주세요.'],
  ])('%s → 정책 문구', (error, body) => {
    expect(describeError(error).body).toBe(body)
  })

  it('볼트 형식 오류와 동기화 충돌은 VLT-001 문구를 쓴다', () => {
    expect(describeError(new InvalidVaultDataError('items 없음'))).toEqual({
      title: '볼트를 열 수 없음',
      body: '볼트 데이터 형식이 올바르지 않습니다.',
    })
    expect(describeError(new VaultSyncConflictError())).toEqual({
      title: '동기화 충돌',
      body: '다른 기기의 변경 사항과 충돌했습니다. 잠시 후 다시 시도해 주세요.',
    })
  })

  it('로그인 실패에는 제목을 붙인다 (SEC-002)', () => {
    expect(describeError(new ServerError(401, 'AUTH_FAILED')).title).toBe('로그인 실패')
  })

  it('예상하지 못한 오류는 내부 메시지를 드러내지 않고 공통 문구를 쓴다 (COM-001)', () => {
    const message = describeError(new Error('stack trace with secret')).body

    expect(message).toBe('알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.')
    expect(describeError(new ServerError(500, 'INTERNAL_ERROR')).body).toBe(message)
    expect(describeError('문자열 오류').body).toBe(message)
  })
})
