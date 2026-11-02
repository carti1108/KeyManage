// @vitest-environment node
// 백업 파일 형식 읽기·쓰기와 비밀번호로 열기 (VLT-004, vault-spec 6장, ADR-0022)
import { beforeAll, describe, expect, it } from 'vitest'
import { InvalidPasswordError, MAX_ITERATIONS, MIN_ITERATIONS } from '../crypto/keys'
import { keysOf, vectors } from '../crypto/testVectors'
import { sealVault, type EncryptedVaultPayload } from '../crypto/vaultCipher'
import { InvalidVaultDataError, addItem, emptyVault, type VaultData } from '../vault/vaultData'
import { MAX_PAYLOAD_BYTES } from '../vault/vaultSync'
import {
  BACKUP_FORMAT,
  BACKUP_FORMAT_VERSION,
  BackupDecryptError,
  BACKUP_FILE_ALLOWANCE_BYTES,
  MAX_BACKUP_FILE_BYTES,
  NewerBackupVersionError,
  NotABackupFileError,
  backupFileName,
  createBackupFile,
  openBackup,
  parseBackup,
  readBackupFile,
} from './backupFile'

const NOW = new Date('2026-11-01T12:00:00Z')
const vector = vectors.keyDerivationCases[0]
const PASSWORD = vector.password
const KDF = { salt: vector.salt, iterations: vectors.kdf.iterations }
const fields = { serviceName: 'GitHub', username: 'octocat', password: 'pw-1' }

let key: CryptoKey

beforeAll(async () => {
  key = (await keysOf(vector.name)).encryptionKey
})

async function backupOf(data: VaultData): Promise<string> {
  return createBackupFile(await sealVault(key, JSON.stringify(data)), KDF, NOW).text
}

/** 올바른 백업 파일 JSON을 고쳐 쓴다 */
async function editedBackup(edit: (raw: Record<string, unknown>) => void): Promise<string> {
  const raw = JSON.parse(await backupOf(emptyVault(NOW))) as Record<string, unknown>
  edit(raw)
  return JSON.stringify(raw)
}

function compactSize(payload: EncryptedVaultPayload): number {
  return new TextEncoder().encode(JSON.stringify(payload)).length
}

/** 압축 JSON 크기가 limit 이하인 가장 큰 페이로드. Base64 필드라 4바이트 단위로 늘어난다 */
function largestPayloadWithin(limit: number): EncryptedVaultPayload {
  const base: EncryptedVaultPayload = { version: 1, cipher: 'AES-256-GCM', iv: 'AAAAAAAAAAAAAAAA', ciphertext: '', tag: 'AAAAAAAAAAAAAAAAAAAAAA==' }
  const room = limit - compactSize(base)
  return { ...base, ciphertext: 'A'.repeat(room - (room % 4)) }
}

/** 실제 키로 암호화한 페이로드가 볼트 한도 바로 아래가 되도록 메모 길이를 맞춘다 */
async function sealedNearLimit(): Promise<{ data: VaultData; payload: EncryptedVaultPayload }> {
  const withNotes = (length: number) => addItem(emptyVault(NOW), { ...fields, notes: 'x'.repeat(length) }, NOW, 'big')
  let length = Math.floor((MAX_PAYLOAD_BYTES * 3) / 4) - 1_000
  for (;;) {
    const data = withNotes(length)
    const payload = await sealVault(key, JSON.stringify(data))
    const room = MAX_PAYLOAD_BYTES - compactSize(payload)
    if (room >= 0 && room < 8) return { data, payload }
    length += Math.floor((room * 3) / 4) || (room < 0 ? -1 : 1)
  }
}

function source(text: string, size = new TextEncoder().encode(text).length) {
  return { size, text: async () => text }
}

describe('파일 이름은 내보낸 기기의 현지 날짜', () => {
  it('keymanage-backup-YYYYMMDD.json', () => {
    expect(backupFileName(new Date(2026, 0, 5, 23, 59))).toBe('keymanage-backup-20260105.json')
    expect(backupFileName(new Date(2026, 10, 1, 0, 0))).toBe('keymanage-backup-20261101.json')
  })
})

describe('백업 파일 만들기', () => {
  it('vault-spec 6장 필드만 담고, 이메일·revision·평문이 없다', async () => {
    const data = addItem(emptyVault(NOW), fields, NOW, 'a')
    const payload = await sealVault(key, JSON.stringify(data))

    const file = createBackupFile(payload, KDF, NOW)
    const raw = JSON.parse(file.text) as Record<string, unknown>

    expect(file.name).toBe(backupFileName(NOW))
    expect(Object.keys(raw).sort()).toEqual(['createdAt', 'format', 'formatVersion', 'kdf', 'vault'])
    expect(raw).toEqual({
      format: 'keymanage-backup',
      formatVersion: 1,
      createdAt: '2026-11-01T12:00:00Z',
      kdf: { name: 'PBKDF2-HMAC-SHA256', iterations: KDF.iterations, salt: KDF.salt },
      vault: payload,
    })
    for (const plain of ['GitHub', 'octocat', 'pw-1', 'revision', '@']) expect(file.text).not.toContain(plain)
  })
})

describe('백업 파일 읽기: 형식 검사', () => {
  it('올바른 파일은 KDF 값과 암호화 볼트를 돌려준다', async () => {
    const text = await backupOf(emptyVault(NOW))

    const backup = await readBackupFile(source(text))

    expect(backup).toMatchObject({ formatVersion: BACKUP_FORMAT_VERSION, kdf: KDF, createdAt: '2026-11-01T12:00:00Z' })
    expect(backup.vault).toEqual((JSON.parse(text) as { vault: EncryptedVaultPayload }).vault)
  })

  it('파일 크기가 읽기 한도(볼트 한도 + 머리 필드 여유 64KB)를 넘으면 읽지 않고 거절한다. 같으면 읽는다', async () => {
    expect(BACKUP_FILE_ALLOWANCE_BYTES).toBe(65_536)
    expect(MAX_BACKUP_FILE_BYTES).toBe(MAX_PAYLOAD_BYTES + BACKUP_FILE_ALLOWANCE_BYTES)
    let read = false
    const huge = { size: MAX_BACKUP_FILE_BYTES + 1, text: async () => ((read = true), '') }

    await expect(readBackupFile(huge)).rejects.toBeInstanceOf(NotABackupFileError)
    expect(read).toBe(false)

    const text = await backupOf(emptyVault(NOW))
    await expect(readBackupFile(source(text, MAX_BACKUP_FILE_BYTES))).resolves.toBeDefined()
  })

  it('텍스트로 넘겨도 UTF-8로 읽기 한도를 넘으면 거절한다', () => {
    expect(() => parseBackup('가'.repeat(Math.floor(MAX_BACKUP_FILE_BYTES / 3) + 1))).toThrow(NotABackupFileError)
  })

  it('vault를 압축 JSON으로 다시 쓴 크기에 볼트 한도를 적용한다: 한도 이하는 받고 넘으면 거절한다', async () => {
    const atLimit = largestPayloadWithin(MAX_PAYLOAD_BYTES)
    const overLimit = { ...atLimit, ciphertext: `${atLimit.ciphertext}AAAA` }
    expect(compactSize(overLimit)).toBeGreaterThan(MAX_PAYLOAD_BYTES)

    const accepted = createBackupFile(atLimit, KDF, NOW).text
    // 들여쓰기·머리 필드 때문에 파일 자체는 볼트 한도보다 크다
    expect(new TextEncoder().encode(accepted).length).toBeGreaterThan(MAX_PAYLOAD_BYTES)
    expect(parseBackup(accepted).vault).toEqual(atLimit)
    expect(() => parseBackup(createBackupFile(overLimit, KDF, NOW).text)).toThrow(NotABackupFileError)
  })

  it('볼트 한도 직전 볼트를 내보낸 파일을 다시 복원할 수 있다(왕복)', async () => {
    const { data, payload } = await sealedNearLimit()
    expect(compactSize(payload)).toBeLessThanOrEqual(MAX_PAYLOAD_BYTES)
    expect(compactSize(payload)).toBeGreaterThan(MAX_PAYLOAD_BYTES - 8)
    const file = createBackupFile(payload, KDF, NOW)

    const backup = await readBackupFile(source(file.text))

    await expect(openBackup(backup, PASSWORD)).resolves.toEqual(data)
  })

  it('JSON이 아니거나 객체가 아니면 거절한다', async () => {
    for (const text of ['', 'not json', '[]', 'null', '"keymanage-backup"']) {
      await expect(readBackupFile(source(text)), text).rejects.toBeInstanceOf(NotABackupFileError)
    }
  })

  it('format이 다르면 거절한다', async () => {
    for (const format of ['keymanage-vault', undefined, 1]) {
      const text = await editedBackup((raw) => (raw.format = format))
      expect(() => parseBackup(text), String(format)).toThrow(NotABackupFileError)
    }
    expect(BACKUP_FORMAT).toBe('keymanage-backup')
  })

  it('필드가 없거나 타입이 틀리면 거절한다', async () => {
    const edits: Array<[string, (raw: Record<string, any>) => void]> = [
      ['formatVersion 없음', (raw) => delete raw.formatVersion],
      ['formatVersion 문자열', (raw) => (raw.formatVersion = '1')],
      ['formatVersion 0', (raw) => (raw.formatVersion = 0)],
      ['formatVersion 소수', (raw) => (raw.formatVersion = 1.5)],
      ['createdAt 없음', (raw) => delete raw.createdAt],
      ['createdAt 숫자', (raw) => (raw.createdAt = 0)],
      ['kdf 없음', (raw) => delete raw.kdf],
      ['kdf 배열', (raw) => (raw.kdf = [])],
      ['kdf.name 다름', (raw) => (raw.kdf.name = 'Argon2id')],
      ['kdf.iterations 문자열', (raw) => (raw.kdf.iterations = '600000')],
      ['kdf.salt 없음', (raw) => delete raw.kdf.salt],
      ['kdf.salt Base64 아님', (raw) => (raw.kdf.salt = 'AAECAwQFBgcICQoLDA0ODw')],
      ['vault 없음', (raw) => delete raw.vault],
      ['vault.iv 없음', (raw) => delete raw.vault.iv],
      ['vault.ciphertext 숫자', (raw) => (raw.vault.ciphertext = 1)],
      ['vault.tag Base64 아님', (raw) => (raw.vault.tag = '***')],
      ['vault.cipher 다름', (raw) => (raw.vault.cipher = 'AES-128-CBC')],
      ['vault.version 문자열', (raw) => (raw.vault.version = '1')],
    ]
    for (const [name, edit] of edits) {
      const text = await editedBackup(edit)
      expect(() => parseBackup(text), name).toThrow(NotABackupFileError)
    }
  })

  it('KDF 값이 SEC-001 검증(반복 횟수 범위, salt 16바이트)을 어기면 거절한다', async () => {
    const edits: Array<[string, (raw: Record<string, any>) => void]> = [
      ['반복 횟수 하한 미만', (raw) => (raw.kdf.iterations = MIN_ITERATIONS - 1)],
      ['반복 횟수 상한 초과', (raw) => (raw.kdf.iterations = MAX_ITERATIONS + 1)],
      ['salt 15바이트', (raw) => (raw.kdf.salt = 'AAECAwQFBgcICQoLDA0O')],
      ['salt 17바이트', (raw) => (raw.kdf.salt = 'AAECAwQFBgcICQoLDA0ODxA=')],
    ]
    for (const [name, edit] of edits) {
      const text = await editedBackup(edit)
      expect(() => parseBackup(text), name).toThrow(NotABackupFileError)
    }
    // 경계값은 받는다
    for (const iterations of [MIN_ITERATIONS, MAX_ITERATIONS]) {
      const text = await editedBackup((raw) => ((raw.kdf as Record<string, unknown>).iterations = iterations))
      expect(parseBackup(text).kdf.iterations).toBe(iterations)
    }
  })

  it('formatVersion이나 암호화 볼트 version이 아는 것보다 높으면 새 버전으로 거절한다', async () => {
    const higherFormat = await editedBackup((raw) => (raw.formatVersion = 2))
    expect(() => parseBackup(higherFormat)).toThrow(NewerBackupVersionError)

    const higherPayload = await editedBackup((raw) => ((raw.vault as Record<string, unknown>).version = 2))
    expect(() => parseBackup(higherPayload)).toThrow(NewerBackupVersionError)
  })

  it('모르는 최상위 필드는 무시한다', async () => {
    const text = await editedBackup((raw) => (raw.note = 'x'))
    expect(parseBackup(text).formatVersion).toBe(1)
  })
})

describe('백업 열기: 파일의 KDF 값과 비밀번호로 키를 만들어 복호화한다', () => {
  it('비밀번호가 맞으면 볼트를 돌려준다(앞뒤 공백 등은 마스터 비밀번호처럼 전처리)', async () => {
    const data = addItem(emptyVault(NOW), fields, NOW, 'a')
    const backup = parseBackup(await backupOf(data))

    await expect(openBackup(backup, `  ${PASSWORD} `)).resolves.toEqual(data)
  })

  it('비밀번호가 틀리면 BackupDecryptError', async () => {
    const backup = parseBackup(await backupOf(emptyVault(NOW)))

    await expect(openBackup(backup, 'wrong password')).rejects.toBeInstanceOf(BackupDecryptError)
  })

  it('태그나 암호문이 변조됐으면 BackupDecryptError', async () => {
    const tagFlipped = await editedBackup((raw) => {
      const vault = raw.vault as Record<string, string>
      vault.tag = (vault.tag[0] === 'A' ? 'B' : 'A') + vault.tag.slice(1)
    })
    await expect(openBackup(parseBackup(tagFlipped), PASSWORD)).rejects.toBeInstanceOf(BackupDecryptError)

    const shortTag = await editedBackup((raw) => ((raw.vault as Record<string, string>).tag = 'AAAA'))
    await expect(openBackup(parseBackup(shortTag), PASSWORD)).rejects.toBeInstanceOf(BackupDecryptError)
  })

  it('복호화한 볼트의 스키마 버전이 앱이 아는 것보다 높으면 새 버전으로 거절한다', async () => {
    const backup = parseBackup(await backupOf({ version: 2, updatedAt: '2026-11-01T00:00:00Z', items: [{ id: 'x', future: true }] } as never))

    await expect(openBackup(backup, PASSWORD)).rejects.toBeInstanceOf(NewerBackupVersionError)
  })

  it('복호화는 됐지만 볼트 내용이 규격에 맞지 않으면 VLT-001 형식 오류', async () => {
    const backup = parseBackup(await backupOf({ version: 1, updatedAt: '2026-11-01T00:00:00Z', items: 'not-array' } as never))

    await expect(openBackup(backup, PASSWORD)).rejects.toBeInstanceOf(InvalidVaultDataError)
  })

  it('비밀번호에 깨진 유니코드가 있으면 마스터 비밀번호와 같은 오류', async () => {
    const backup = parseBackup(await backupOf(emptyVault(NOW)))

    await expect(openBackup(backup, 'ab\uD83Dcd')).rejects.toBeInstanceOf(InvalidPasswordError)
  })
})
