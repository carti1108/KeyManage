# KeyManage Apple 클라이언트 (iOS & macOS)

iOS 및 macOS 멀티플랫폼을 지원하는 네이티브 클라이언트입니다.

마이크로 피처(Micro-Features) 모듈화 아키텍처([ADR-0015](docs/adr/ADR-0015-tuist-micro-features-modularization.md))와 Uber microRIBs 패턴([ADR-0014](docs/adr/ADR-0014-apple-multiplatform-microribs.md))을 적용하여 화면 계층 간 강한 격리와 높은 테스트 가능성을 확보했습니다.

---

## 기술 스택

| 분류 | 기술 규격 및 사양 |
| :--- | :--- |
| **Language** | Swift 6 (Complete Concurrency) |
| **Architecture** | Uber microRIBs (Router, Interactor, Builder) |
| **Modularization**| Tuist 4 (Micro-Features) |
| **Security & Crypto** | CryptoKit, LocalAuthentication (Face ID / Touch ID), Keychain |
| **Testing** | Swift Testing |

---

## 모듈 구조

```text
Apps/Apple/
├── Projects/
│   ├── App/                   # 애플리케이션 진입점 및 메인 루트 빌더 (RootBuilder)
│   ├── Features/              # 화면 단위 기능 모듈 (microRIBs)
│   │   ├── AuthFeature/       # 회원가입 및 마스터 키 유도 로그인
│   │   ├── VaultFeature/      # 볼트 항목 목록, 편집, 3-way 충돌 병합 UI
│   │   └── SettingsFeature/   # 자동 유휴 잠금, 생체 인증, 암호화 백업 내보내기/복원
│   └── Core/                  # 플랫폼 공통 비즈니스 코어 및 인프라
│       ├── CoreCrypto/        # CryptoKit 기반 PBKDF2 / HKDF / AES-GCM 암호 엔진
│       ├── CoreDomain/        # 볼트 엔티티, 유스케이스, 리비전 충돌 병합 알고리즘
│       ├── CoreNetwork/       # 백엔드 REST API 통신 및 세션 관리
│       └── CoreStorage/       # Keychain 연동 및 기기 암호화 볼트 보존 (ADR-APL-001)
└── Tuist/                     # Tuist 프로젝트 설정 및 외부 의존성 매니페스트
```

---

## 트러블 슈팅

Apple 클라이언트 빌드, Swift 6 동시성 데이터 레이스 및 생체 인증/키체인 연동 관련 주요 문제 해결 내역을 정리합니다.
