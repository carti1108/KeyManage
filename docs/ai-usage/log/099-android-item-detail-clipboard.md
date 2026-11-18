# 2026-11-18 · Android 항목 상세·편집과 클립보드 자동 비우기

- 이슈·PR: #3
- 커밋: 없음(커밋 전)

> **요약** 7c 첫 PR로 항목 상세·추가·수정·삭제와 저장, 아이디·비밀번호 복사와 30초 자동 비우기(COM-001), 잠글 때 클립보드 비우기를 넣었다. #140은 테스트 삭제가 feat 커밋에 섞여 CI TDD 게이트에 걸려 #141로 다시 올렸다.

## 사용 도구

- 도구: Claude Code(Opus 5.5), Gradle
- 플러그인·스킬: 없음

## 요청

7b PR을 머지하고 7c를 진행하는 요청이었다. 7c는 항목·복사(7c-1), 생성기·강도 게이지(7c-2), 설정·자동 잠금·탭 구조(7c-3)로 나눴다.

## AI 산출물

- `:core:domain`: `VaultEditing`·`ItemFields`, `ClipboardBurner`·`ClipboardPort`·`Scheduler`, `VaultSessionUseCase.save`, `VaultSyncCoordinator.saveLocally`
- `:core:data`: `AndroidClipboard`, `MainLooperScheduler`
- `:feature:vaultlist`: 목록·상세·편집 화면 상태(`VaultScreen`), 삭제 확인, 저장 오류 문구(VLT-001), 복사 배지
- `:feature:root`: `VaultSaver` 구현, 잠글 때 클립보드 비우기

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 지시 | #140 대신 새 PR로 올림(강제 푸시 대신) |
| AI 기본값 | 상세·편집은 Apple과 같이 VaultList 피처 안에 둠. 기존 `VaultListInteractorTest`의 생성자 호출만 바꾸는 변경은 별도 `test:` 커밋으로 나눔. |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `./gradlew test lint assembleDebug` | 통과. 편집 7개, 클립보드 5개, 세션 저장 2개, 항목 화면 11개, Root 3개 추가 |
| `bash Scripts/check-docs.sh` | 통과 |

## 검증하지 못한 것

1. adb 데몬이 이 환경에서 뜨지 않아 실제 기기의 클립보드 동작(민감 표시, 다른 앱 복사 감지)을 확인하지 못했다.
2. 상세 화면의 강도 게이지는 GEN-001과 함께 다음 PR에서 붙인다.
