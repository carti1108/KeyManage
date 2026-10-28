# KeyManage 문서 지도

이 문서는 `KeyManage` 레포의 공식 문서 체계 진입점입니다. 모든 설계 결정, 서비스 정책, 크로스 플랫폼 공통 규격, AI 활용 과정은 이 디렉터리(`docs/`) 하위에 기록되고 추적됩니다.

---

## 1. 플랫폼 공통 개발 명세서

백엔드와 모든 플랫폼 클라이언트(iOS/macOS, Android, Web)가 병렬 개발 시 공유하는 표준 계약 문서입니다.

| 명세서 | 내용 |
| :--- | :--- |
| [`vault-spec.md`](vault-spec.md) | **볼트 암호화 규격**: PBKDF2/AES-GCM 파라미터, Base64 직렬화 및 평문 볼트 JSON 스키마 |
| [`api-spec.md`](api-spec.md) | **백엔드 REST API 계약**: 비밀번호 생성, 엔트로피 감사, 볼트 동기화 엔드포인트 및 DTO 스펙 |
| [`design-system.md`](design-system.md) | **시멘틱 디자인 시스템**: 다크/라이트 컬러 토큰, 타이포그래피, 핵심 컴포넌트 및 아이콘 매핑표 |

---

## 2. 거버넌스 및 정책 문서 체계

| 폴더 | 용도 | 관리 규칙 및 플러그인 |
| :--- | :--- | :--- |
| [`adr/`](adr/README.md) | 아키텍처 및 기술 결정 기록 (ADR) | `adr-manager` (한 번 채택된 결정은 불변, 대체 시 새 ADR 발행) |
| [`policies/`](policies/README.md) | 서비스 정책 SSOT (Single Source of Truth) | `policy-manager` (영역별 규칙, 경계값, 예외, 검증 방법 관리) |
| [`ai-usage/`](ai-usage/README.md) | AI 활용 기록 및 평가 보고서 | `project-docs:ai-usage` (작업 단위별 append-only 기록) |

---

## 3. 문서 정합성 검사

모든 문서는 링크 깨짐, 번호 중복, 필수 필드 누락을 방지하기 위해 자동 검사 스크립트로 검증됩니다.

```bash
bash Scripts/check-docs.sh
```
