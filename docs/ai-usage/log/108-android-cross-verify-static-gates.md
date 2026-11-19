# 2026-11-19 · Android 교차 검증과 정적 감사 Kotlin 확장

- 이슈·PR: #3
- 커밋: 없음(커밋 전)

> **요약** 에픽 #3 8단계로 다른 플랫폼이 암호화한 볼트를 Android에서 평문 볼트와 백업 파일까지 여는 테스트를 넣고, 정책 감사와 TDD 무결성 감사가 Kotlin을 검사하게 넓혔다.

## 사용 도구

- 도구: Claude Code(Opus 5.5), Gradle
- 플러그인·스킬: 없음

## 요청

8a-3 PR을 머지하고 8단계(교차 검증, 감사 스크립트 Kotlin 확장)를 진행하라는 요청이었다.

## AI 산출물

- `:core:data` `CrossPlatformVaultTest`: 공용 벡터 암호문을 복호화해 `JsonVaultSerializer`로 항목 값까지 확인, 같은 암호문을 백업 파일로 감싸 `BackupUseCase.open`에 마스터 비밀번호만 넣어 열기
- `Scripts/check-policy-acceptance.sh`: Android 구역. 경계값은 `*Test.kt`, 표준 문구 원문은 `src/main` 소스에서 확인
- `Scripts/verify-tdd-integrity.sh`: Kotlin 어설션(`assertContentEquals`, `assertFailsWith`, `assertNull` 등) 삭제도 잡음
- `.githooks/pre-commit`: Android Kotlin 변경 때 정책 감사 실행

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 지시 | #150 머지 후 8단계 진행 |
| AI 기본값 | Apple 산출물을 새로 만들지 않고, 웹이 만들고 iOS가 통과하는 공용 벡터를 교차 검증 근거로 씀. Android 테스트는 문구를 상수로 비교하므로 문구 원문은 소스에서 검사. Android에 없는 표준 문구 4개는 이번 감사 규칙에서 빼고 따로 고칠 거리로 남김. |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `./gradlew :core:data:testDebugUnitTest --tests '*CrossPlatformVaultTest'` | 통과(2개) |
| `bash Scripts/check-policy-acceptance.sh` | 통과. 규칙 하나를 바꾸면 Android 위반으로 실패하는 것도 확인 |
| `bash Scripts/check-docs.sh` | 통과 |

## 검증하지 못한 것

1. iOS 앱이 실제로 내보낸 백업 파일은 없어 공용 벡터로 대신했다.
2. Android 소스에 없는 표준 문구: 5MB 초과(VLT-001), revision 역행 제목(VLT-001), 오프라인 배너(VLT-002), 클립보드 소각 안내(COM-001).
