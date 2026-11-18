# 2026-11-18 · Android 암호화 백업 내보내기와 복원

- 이슈·PR: #3
- 커밋: 없음(커밋 전)

> **요약** 6단계 마지막 PR로 Android 백업 파일(vault-spec 6장) 코덱, 내보내기·열기·복원 유스케이스, 항목 되돌리기(VLT-004 규칙 6·7)를 넣었다.

## 사용 도구

- 도구: Claude Code(Opus 5.5), Gradle
- 플러그인·스킬: 없음

## 요청

6c PR을 머지하고 6d(암호화 백업)를 진행하는 요청이었다.

## AI 산출물

- `:core:domain`: `BackupFile`·`BackupCodec`·`BackupError`, `VaultRestorer`(웹 `restoreItems`와 같은 순서·시각 규칙), `BackupUseCase`(내보내기, 파일 KDF 값으로 키 유도, 읽기 전용 볼트 복원 거절, 파일 이름)
- `:core:data`: `JsonBackupCodec`(파일 5MB + 64KB, vault 필드 5MB, format·formatVersion·kdf.name 검사)
- `Apps/Android/docs/platform-bindings.md`: 암호화 백업 절

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 지시 | #137 머지 후 6d 진행 |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `./gradlew test lint assembleDebug` | 통과. 복원 8개, 유스케이스 9개, 코덱 7개(실제 PBKDF2·HKDF·AES-GCM 왕복 포함) 추가 |
| `bash Scripts/check-docs.sh` | 통과 |

## 검증하지 못한 것

1. 웹에서 내보낸 백업 파일을 Android가 복원하는 상호 운용 확인은 8단계 교차 검증 픽스처로 한다.
2. 파일 선택(SAF), 내보내기·복원 전 안내 문구, 복원 중 중복 실행 막기는 화면 단계에서 붙인다.
