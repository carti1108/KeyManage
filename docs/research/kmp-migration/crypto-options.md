# KMP 암호 구현 방식 비교

- 상태: 조사 문서(실험 브랜치 `research/kmp-migration` 전용, main에 합치지 않는다)
- 날짜: 2026-11-12
- 범위: 뷰를 제외한 클라이언트 코드를 Kotlin Multiplatform으로 옮길 때 [vault-spec.md](../../vault-spec.md)의 암호 규격을 어떻게 구현할지

> **요약** 네 가지 방식을 비교했다. 실험에는 **A. cryptography-kotlin**을 추천한다. 모든 대상(Android, iOS·macOS, 브라우저)에서 OS·브라우저의 검증된 구현을 호출하고, Kotlin 코드만으로 규격 전체를 덮는 유일한 방식이다. 대신 외부 라이브러리가 보안 경계 안에 들어온다. KeyManage 쪽에 얇은 암호 인터페이스를 두면 나중에 B로 바꿀 수 있다.

---

## 1. 구현해야 하는 것

[vault-spec.md](../../vault-spec.md) 2장 기준이다.

| 단계 | 규격 |
| :--- | :--- |
| 전처리 | NFC 정규화 → 앞뒤 공백 제거 → UTF-8 |
| 키 유도 | PBKDF2-HMAC-SHA256, 600,000회(허용 600,000 ~ 10,000,000), salt 16바이트, 출력 32바이트 |
| 키 분리 | HKDF-SHA256, salt 빈 값, info `keymanage-enc`·`keymanage-auth`, 출력 32바이트 |
| 암호화 | AES-256-GCM, IV 12바이트, 태그 16바이트, AAD `keymanage-vault|{version}|{cipher}` |
| 난수 | IV·salt용 보안 난수 |

검증 기준은 [crypto-test-vectors.json](../../crypto-test-vectors.json)이다. 지금은 웹만 이 벡터로 테스트하고 Apple은 쓰지 않는다.

## 2. 방식과 상관없이 생기는 제약

1. **Kotlin 공통 코드에는 NFC 정규화 API가 없다.** 대상마다 `expect`/`actual`로 플랫폼 API를 부른다(JVM·Android `java.text.Normalizer`, iOS `NSString.precomposedStringWithCanonicalMapping`, JS `String.prototype.normalize`).
2. **CryptoKit은 Swift 전용 API다.** Kotlin/Native는 C·Objective-C 헤더만 직접 부를 수 있어서, Kotlin에서 CryptoKit을 쓰려면 Swift 코드가 중간에 있어야 한다.
3. **CommonCrypto 공개 API에는 AES-GCM이 없다.** PBKDF2와 HMAC은 있다. 그래서 iOS에서 AES-GCM은 CryptoKit(Swift)이 필요하다.
4. **WebCrypto는 비동기만 지원한다.** 공유 암호 API는 `suspend` 함수여야 한다.
5. **웹의 PBKDF2 600,000회는 브라우저 내장 구현이어야 한다.** Kotlin/JS로 직접 계산하면 내장 구현보다 훨씬 느리다.

## 3. 선택지

### A. cryptography-kotlin 라이브러리

[whyoleg/cryptography-kotlin](https://github.com/whyoleg/cryptography-kotlin)(Apache-2.0, 0.6.0, 2026-04-02 릴리스). 직접 암호를 구현하지 않고 대상마다 플랫폼 구현을 감싸는 **공급자(provider)**를 쓴다.

| 알고리즘 | JDK(JVM·Android) | WebCrypto(JS·Wasm) | Apple(CommonCrypto) | CryptoKit |
| :--- | :---: | :---: | :---: | :---: |
| PBKDF2 | 지원 | 지원 | 지원 | 미지원 |
| HKDF | 지원 | 지원 | 지원 | 지원 |
| AES-GCM | 지원 | 지원 | 미지원 | 지원(태그 128비트만) |

- `cryptography-provider-optimal` 하나를 넣으면 Apple에서 CryptoKit을 먼저 쓰고 CommonCrypto로 보완한다. 그래서 iOS에서도 PBKDF2(CommonCrypto)와 AES-GCM(CryptoKit)을 모두 쓸 수 있다.
- CryptoKit 공급자는 Xcode에 들어 있는 Swift 라이브러리에 링크한다. Xcode가 `/Applications/Xcode.app`에 없으면 링크 오류가 날 수 있다.
- WebCrypto 공급자는 `suspend` 함수만 지원한다. `Blocking` 함수는 예외를 던진다.
- 보안 난수는 `cryptography-random` 모듈이 제공한다.
- 출처: 라이브러리 문서의 공급자 안내(`docs/getting-started/providers/`)와 알고리즘 지원 표(키 유도, AEAD 페이지).

### B. 직접 `expect`/`actual`로 플랫폼 API 호출

KeyManage가 대상별 구현을 직접 쓴다.

| 대상 | PBKDF2 | HKDF | AES-GCM |
| :--- | :--- | :--- | :--- |
| Android·JVM | JCA `PBKDF2WithHmacSHA256` | JCA에 없음. `HmacSHA256`으로 RFC 5869를 직접 구현 | JCA `AES/GCM/NoPadding` |
| 브라우저 | WebCrypto `deriveBits` | WebCrypto `HKDF` | WebCrypto `AES-GCM` |
| iOS·macOS | CommonCrypto `CCKeyDerivationPBKDF` | CommonCrypto `CCHmac`으로 직접 구현 | **Kotlin만으로 불가.** Swift(CryptoKit) 코드를 따로 써서 주입해야 함 |

- 외부 의존성이 없다.
- 하지만 iOS AES-GCM은 Swift 코드가 남아서 "뷰 외 전부 Kotlin" 목표를 다 채우지 못한다.
- HKDF 직접 구현, JS `external` 선언, cinterop 코드를 KeyManage가 유지보수한다. 결국 A가 이미 해 둔 일을 다시 하는 셈이다.

### C. 플랫폼 앱이 암호 인터페이스를 구현해 주입

Kotlin 공유 코드는 `VaultCrypto` 같은 인터페이스만 정의한다. Swift(CryptoKit), TypeScript(WebCrypto), Kotlin(JCA) 앱이 각자 구현해 넣는다.

- 지금 테스트된 Swift·TS 암호 코드를 그대로 쓸 수 있다.
- 하지만 암호 코드가 Kotlin 밖에 남아서 이번 실험 목표와 맞지 않는다.
- 플랫폼마다 구현이 다르므로 모든 플랫폼이 테스트 벡터를 따로 돌려야 한다.

### D. 순수 Kotlin으로 직접 구현

SHA-256, HMAC, AES를 Kotlin으로 직접 작성한다.

- 플랫폼 API에 전혀 의존하지 않는다.
- **추천하지 않는다.** 타이밍 공격 같은 부채널을 막는 구현을 직접 검증해야 하고, 웹·Native에서 PBKDF2 600,000회가 내장 구현보다 크게 느리다(2장 5번). 검증된 OS 구현을 두고 자체 암호를 쓰는 셈이다.

## 4. 비교

| 기준 | A. 라이브러리 | B. 직접 호출 | C. 앱이 주입 | D. 직접 구현 |
| :--- | :--- | :--- | :--- | :--- |
| 실제 암호 연산 | OS·브라우저 구현 | OS·브라우저 구현 | OS·브라우저 구현 | 자체 코드 |
| Kotlin으로 쓰는 비율 | 전부(NFC만 `expect`/`actual`) | iOS AES-GCM 제외 전부 | 인터페이스만 | 전부 |
| 외부 의존성 | 라이브러리 1개(보안 경계 안) | 없음 | 없음 | 없음 |
| iOS 연결 | Swift 런타임 링크 필요(라이브러리가 처리) | Swift 주입 코드 직접 작성 | 기존 Swift 코드 | 없음 |
| 웹 PBKDF2 성능 | 내장 구현 | 내장 구현 | 내장 구현 | 느림 |
| KeyManage 유지보수 | 적음 | 많음(HKDF·cinterop·JS 선언) | 플랫폼별 3벌 | 매우 많음 |
| 되돌리기 비용 | 인터페이스로 감싸면 낮음 | 중간 | 중간 | 높음 |
| 실험 목표 부합 | 높음 | 중간 | 낮음 | 높지만 위험 |

## 5. 추천

1. **실험에는 A를 쓴다.** 목표("뷰 외 전부 Kotlin")를 채우면서 실제 연산은 검증된 플랫폼 구현이 맡는다.
2. **KeyManage에 얇은 인터페이스를 둔다.** `PasswordKeyDeriver`, `KeySeparator`, `VaultCipher`처럼 vault-spec 단계 이름으로 감싸고, 라이브러리 타입은 그 안에서만 쓴다. 그러면 A에 문제가 생겼을 때 B로 바꾸는 범위가 이 구현 몇 개로 줄어든다.
3. **A를 버리는 조건:** 아래 확인 항목 중 하나라도 실패하면 B로 바꾼다.

## 6. 실험 2단계에서 확인할 것

| 항목 | 통과 기준 |
| :--- | :--- |
| 테스트 벡터 | Android, iOS 시뮬레이터, macOS, 브라우저(JS) 모두 `crypto-test-vectors.json` 전체 통과 |
| 기존 볼트 호환 | 현재 Apple·웹이 만든 볼트를 Kotlin 구현으로 열고, 그 반대도 됨 |
| PBKDF2 시간 | 600,000회 유도 시간을 대상별로 재고, 지금 Apple·웹 구현과 비교 |
| iOS 빌드 | xcframework 하나에 carti-RIBs-KMP와 함께 들어가고 Tuist 앱이 링크됨 |
| 웹 번들 | Kotlin/JS 번들 크기를 재고 지금 웹 번들과 비교 |
| 라이브러리 버전 | 0.x 버전이라 API가 바뀔 수 있음. 실험에서는 0.6.0으로 고정 |

## 7. 실험 결과 (2026-11-12, A 채택 후 2단계)

`Apps/Shared/crypto` 모듈에 A로 구현했다. 테스트는 `crypto-test-vectors.json`을 그대로 읽어 확인한다.

| 대상 | 테스트 | 키 유도 5건(PBKDF2 600,000회 + HKDF 2회) 걸린 시간 |
| :--- | :--- | :--- |
| JVM(JDK 21, Android JCA 대용) | 7/7 통과 | 0.72초 |
| JS Node | 7/7 통과 | 0.43초 |
| JS 브라우저(헤드리스 Chrome) | 7/7 통과 | 측정 안 됨(테스트 보고서에 시간이 남지 않음) |
| macOS arm64 | 7/7 통과 | 0.51초 |
| iOS 시뮬레이터 arm64 | 7/7 통과 | 0.48초 |

- 확인한 것: 전처리(NFC, 지정 공백만 제거), 마스터 키·암호화 키·인증 해시 5건, 볼트 암호화 결과(암호문·태그), AAD 변조 거절, IV 12바이트 새로 생성, 반복 횟수·salt 경계값.
- 수정한 것: WebCrypto의 복호화 실패(`OperationError`)는 Kotlin `Exception`이 아니라서 처음에는 브라우저·Node에서만 변조 거절 테스트가 실패했다. `Throwable`로 받고 코루틴 취소는 그대로 넘기도록 고쳤다.
- 라이브러리 사용 메모: IV를 직접 넘기는 `encryptWithIv`는 `DelicateCryptographyApi` 옵트인이 필요하다. IV를 페이로드에 따로 저장하는 형식이라 옵트인했고, 기본값은 매번 새 난수다.

### 아직 확인하지 못한 것

1. Android 실기기·에뮬레이터 실행(JVM으로만 대신 확인).
2. 현재 Apple 구현과의 호환. 벡터는 웹 구현으로 만들어서 웹 호환은 확인됐지만, Apple은 지금도 벡터 테스트가 없다.
3. 지금 Apple·웹 구현과의 PBKDF2 시간 비교.
4. xcframework에 carti-RIBs-KMP와 함께 넣어 Tuist 앱이 링크하는지.
5. Kotlin/JS 배포 번들 크기.

## 8. 정해야 할 것

이 문서는 결정이 아니다. 실험 결과를 본 뒤 고르고, main에 반영할 때 공통 ADR로 남긴다(ADR-0004를 대체하는 결정과 함께).
