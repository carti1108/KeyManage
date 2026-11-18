# 2026-11-18 · Android 기기 저장과 동기화 대기

- 이슈·PR: #3
- 커밋: 없음(커밋 전)

> **요약** 6단계 세 번째 PR로 Android 기기 기록(VLT-003)과 동기화 대기(VLT-002)를 도메인 조정자와 파일 저장소로 넣고, 저장 응답 revision 검사(기대값+1)를 저장 유스케이스에 더했다.

## 사용 도구

- 도구: Claude Code(Opus 5.5), Gradle
- 플러그인·스킬: 없음

## 요청

6b PR을 머지하고 6c(기기 저장·오프라인 대기)를 진행하는 요청이었다.

## AI 산출물

- `:core:domain`: `DeviceRecord`·`DeviceAccount`·`DeviceRecordStore`·`DeviceStorageError`, `VaultSyncCoordinator`(대기 판정, 대기 위 연속 수정, 분명한 거절 뒤 자동 재전송 중단, 계정 전환·KDF 변경 처리, 기기 저장 실패 경고), `SyncError.UnexpectedRevision`
- `:core:data`: `FileDeviceRecordStore`(JSON 파일 하나, 임시 파일 뒤 원자적 교체)
- `Apps/Android/docs/platform-bindings.md`: 기기 저장과 동기화 대기 절

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 지시 | #136 머지 후 6c 진행 |
| AI 기본값 | 기기 기록은 이미 AES-GCM 암호문이라 Keystore로 다시 감싸지 않았다. 웹 `vaultSync.ts`와 같이 저장 응답 revision을 기대값+1로 검사했다. |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `./gradlew test lint assembleDebug` | 통과. 조정자 14개, 파일 저장소 6개 추가 |
| `bash Scripts/check-docs.sh` | 통과 |

## 검증하지 못한 것

1. 30초 간격·재연결·잠금 해제 재전송 시점과 오프라인 배너, 대기 중 로그아웃 확인은 화면 단계에서 붙인다.
2. 기기에서 `noBackupFilesDir` 경로로 실제 저장해 보지 않았다.
