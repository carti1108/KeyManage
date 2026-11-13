// 백업 파일 이름 규칙 (VLT-004, ADR-0022). 파일 형식 읽기·쓰기와 비밀번호로 열기는 공유 Kotlin(BackupFileTest)이 검증한다
import { describe, expect, it } from 'vitest'
import { backupFileName } from './backupFile'

describe('파일 이름은 내보낸 기기의 현지 날짜', () => {
  it('keymanage-backup-YYYYMMDD.json', () => {
    expect(backupFileName(new Date(2026, 0, 5, 23, 59))).toBe('keymanage-backup-20260105.json')
    expect(backupFileName(new Date(2026, 10, 1, 0, 0))).toBe('keymanage-backup-20261101.json')
  })
})
