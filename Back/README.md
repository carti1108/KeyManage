# KeyManage 백엔드 (Backend)

포트-어댑터(Hexagonal) 멀티 모듈 백엔드 서버입니다 ([ADR-0002](docs/adr/ADR-0002-hexagonal-multi-module-backend.md)).

서버는 영지식(Zero-Knowledge) 보안 원칙에 따라 복호화 키나 평문 볼트를 일절 다루지 않으며, 클라이언트가 전송한 암호문(Ciphertext)의 원자적 저장 및 동시성 제어(Revision 관리)만 수행합니다 ([SEC-001](../../docs/policies/SEC-001-zero-knowledge-encryption.md)).

---

## 기술 스택

| 분류 | 기술 규격 및 사양 |
| :--- | :--- |
| **Language** | Kotlin 2.1 (JVM 21) |
| **Framework** | Spring Boot 3.4 |
| **Build Tool** | Gradle |
| **Testing** | JUnit 5, MockK, PIT (뮤테이션 테스트) |
| **Storage Engine** | 원자적 파일 저장소 (Atomic File-Based Storage, ADR-0003, ADR-0007) |

---

## 모듈 구조

비즈니스 코어인 `domain` 모듈을 중심에 두고, 외부 의존성을 포트-어댑터로 격리한 헥사고날 아키텍처를 따릅니다.

```text
Back/
├── domain/            # 순수 비즈니스 로직: 볼트 엔티티, 리비전 제어, 회원 가입/인증 포트
├── api/               # 인바운드 어댑터: Spring Web MVC REST API 컨트롤러, DTO, 세션 필터
├── infrastructure/    # 아웃바운드 어댑터: 원자적 파일 저장소 어댑터, 세션 저장소
└── app/               # 조립 루트(Composition Root): 스프링 부트 메인 진입점 및 의존성 주입
```

---

## 트러블 슈팅

백엔드 서버 기동, 동시성 리비전 경합 및 파일 볼트 저장소 관련 주요 문제 해결 내역을 정리합니다.
