# 2026-11-19 · Android 누락 표준 문구 보강

- 이슈·PR: #3
- 커밋: 없음(커밋 전)

> **요약** 정책 감사를 Android로 넓히며 찾은 누락 표준 문구를 볼트 목록 화면에 붙이고 감사 규칙에 더했다.

## 사용 도구

- 도구: Claude Code(Opus 5.5), Gradle
- 플러그인·스킬: 없음

## 요청

8단계 PR을 머지하고 Android에 없던 표준 문구를 고치라는 요청이었다.

## AI 산출물

- `:feature:vaultlist`: 저장이 대기로 남으면 VLT-002 오프라인 경고, revision 역행이면 VLT-001 제목, 복사 뒤 COM-001 복사 직후 제목·본문
- `Scripts/check-policy-acceptance.sh`: Android 소스의 위 문구와 5MB 초과 문구 검사

## 사람의 판단

| 판단 | 내용 |
| :--- | :--- |
| 지시 | #151 머지 후 누락 문구 보강 |
| AI 기본값 | 누락으로 본 4개 중 5MB 초과 문구는 이미 있었고 직전 조사 정규식이 괄호를 그룹으로 읽어 놓친 것이었다. 제목은 별도 상태(`errorTitle`)로 두고 안내 띠 본문 앞 줄에 보임. revision 역행의 "서버 볼트 받아들이기" 버튼은 이번 범위에서 뺌. |

## 검증

| 검증 | 결과 |
| :--- | :--- |
| `./gradlew test lint assembleDebug` | 통과. 표준 문구 테스트 3개 추가 |
| `bash Scripts/check-policy-acceptance.sh` | 통과 |
| `bash Scripts/check-docs.sh` | 통과 |

## 검증하지 못한 것

1. 실기기에서 안내 띠 배치를 확인하지 못했다(adb 사용 불가).
2. revision 역행 때 서버 볼트를 받아들이는 버튼(VLT-001 문구 표)은 Android에 아직 없다.
