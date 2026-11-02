// 화면 테스트용 백업 파일. 형식 검사만 통과하는 값이며 실제로 복호화되지는 않는다
import type { BackupFileSource, VaultBackup } from '../domain/backup/backupFile'

export const BACKUP: VaultBackup = {
  formatVersion: 1,
  createdAt: '2026-11-01T00:30:00Z',
  kdf: { iterations: 600_000, salt: 'AAECAwQFBgcICQoLDA0ODw==' },
  vault: { version: 1, cipher: 'AES-256-GCM', iv: 'AAAAAAAAAAAAAAAA', ciphertext: 'AAAA', tag: 'AAAAAAAAAAAAAAAAAAAAAA==' },
}

/** BACKUP을 vault-spec 6장 JSON으로 쓴다. edit로 필드를 고칠 수 있다 */
export function backupText(edit: (raw: Record<string, unknown>) => void = () => {}): string {
  const raw: Record<string, unknown> = { format: 'keymanage-backup', ...structuredClone(BACKUP), kdf: { name: 'PBKDF2-HMAC-SHA256', ...BACKUP.kdf } }
  edit(raw)
  return JSON.stringify(raw)
}

export function fileSource(text: string, size = text.length): BackupFileSource {
  return { size, text: async () => text }
}
