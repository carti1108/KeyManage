# KeyManage 문서 지도

> **요약** 공통 문서(정책, 공통 ADR, 공통 명세, AI 로그)는 이 `docs/`에 둔다. 플랫폼 문서는 각 플랫폼 폴더의 `docs/`에 둔다. 무엇을 어디에 쓰는지는 [문서 작성 가이드](writing-guide.md)를 따른다.

---

## 1. 찾는 것이 있을 때

| 찾는 것 | 볼 곳 |
| :--- | :--- |
| 앱이 어떻게 동작해야 하는가(값, 문구, 예외) | [정책](policies/README.md) |
| 왜 이렇게 만들었는가 | [공통 ADR](adr/README.md), 각 플랫폼의 `docs/adr/` |
| 암호화 파라미터와 볼트 JSON 형식 | [`vault-spec.md`](vault-spec.md) |
| 서버 API 요청·응답과 오류 코드 | [`api-spec.md`](api-spec.md) |
| 색상 토큰과 컴포넌트 규격 | [`design-system.md`](design-system.md) |
| 한 플랫폼의 구현 방법 | 아래 3장의 플랫폼 문서 |
| 새 문서를 쓰는 방법 | [문서 작성 가이드](writing-guide.md), [`templates/`](templates/) |
| AI와 어떻게 작업했는가 | [AI 활용 기록](ai-usage/README.md) |

---

## 2. 공통 문서

### 명세

모든 클라이언트와 백엔드가 같은 값을 써야 하는 규격이다.

| 명세 | 내용 |
| :--- | :--- |
| [`vault-spec.md`](vault-spec.md) | 볼트 암호화 규격: 키 유도·암호화 파라미터, 암호화 컨테이너, 평문 볼트 스키마, 병합 규칙 |
| [`api-spec.md`](api-spec.md) | 백엔드 REST API 계약: 볼트 조회·저장, 계정 인증, 오류 코드 |
| [`design-system.md`](design-system.md) | 디자인 시스템: 색상 토큰, 타이포그래피, 핵심 컴포넌트, 아이콘 의미 |
| [`crypto-test-vectors.json`](crypto-test-vectors.json) | 플랫폼 간 암호 테스트 벡터 |

### 기록과 정책

| 폴더 | 내용 | 관리 방법 |
| :--- | :--- | :--- |
| [`policies/`](policies/README.md) | 서비스 정책. 동작 규칙의 유일한 기준이다. | `policy-manager` 스킬 |
| [`adr/`](adr/README.md) | 공통 아키텍처 결정 기록. 채택한 결정은 고치지 않는다. | `adr-manager` 스킬 |
| [`ai-usage/`](ai-usage/README.md) | AI 활용 기록. 항목은 추가만 한다. | `project-docs:ai-usage` 스킬 |
| [`templates/`](templates/) | 정책·ADR·플랫폼 명세·AI 로그 템플릿 | [작성 가이드](writing-guide.md) |

---

## 3. 플랫폼 문서

이 표는 문서를 찾기 위한 지도다. 공통 문서의 본문은 플랫폼 문서를 근거로 삼지 않는다.

| 폴더 | 내용 |
| :--- | :--- |
| [`Apps/Web/docs/`](../Apps/Web/docs/README.md) | 웹·데스크톱(Tauri) 클라이언트 ADR, 기기 저장 명세, 플랫폼 바인딩 |
| [`Apps/Apple/docs/`](../Apps/Apple/docs/README.md) | Apple(iOS·macOS) 클라이언트 ADR, 구현 계약, 플랫폼 바인딩, 실험 보고서 |
| [`Back/docs/`](../Back/docs/README.md) | 백엔드 ADR |
| [`Apps/Android/docs/`](../Apps/Android/docs/README.md) | Android 클라이언트(아직 없음)를 만들 때 쓸 플랫폼 바인딩 |

---

## 4. 문서 검사

문서를 고치면 `bash Scripts/check-docs.sh`를 돌린다. 자세한 내용은 [작성 가이드](writing-guide.md) 4장에 있다.
