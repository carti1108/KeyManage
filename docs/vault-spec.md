# [Spec] 볼트 암호화 규격 및 내부 데이터 스키마 (Vault Spec)

이 문서는 모든 클라이언트(iOS/macOS, Android, Web)가 마스터 키로 볼트를 암호화·복호화할 때 지켜야 하는 암호학적 규격과, 복호화된 평문 데이터의 표준 JSON 구조를 정의합니다. 플랫폼 간 상호 운용성을 위해 모든 구현체는 이 명세를 엄격히 준수해야 합니다.

---

## 1. 암호화 아키텍처 개요 (Zero-Knowledge)

```
[ Master Password (Plaintext) ] + [ Salt (16 Bytes, CSPRNG) ]
                       │
                       ▼  PBKDF2-HMAC-SHA256 (100,000 Iterations)
            [ Derived Key (256-bit) ]
                       │
                       ▼  AES-256-GCM (IV 12 Bytes)
   [ Vault Plaintext JSON ] ───► [ Ciphertext ] + [ Auth Tag (16 Bytes) ]
                       │
                       ▼
       [ EncryptedVaultPayload (JSON or Base64 Container) ]
                       │
                       ▼  HTTPS
             [ Backend Storage ]
```

---

## 2. 암호학적 파라미터 규격

| 파라미터 | 규격 | 비고 |
| :--- | :--- | :--- |
| **Key Derivation** | PBKDF2-HMAC-SHA256 | 모던 표준 KDF |
| **Salt** | 16 바이트 (128비트) | 볼트 생성 시 암호학적 난수(CSPRNG)로 생성 |
| **Iteration Count** | 100,000 회 | 무차별 대입 공격(Brute-force) 방어 |
| **Key Size** | 32 바이트 (256비트) | AES-256 대칭키 |
| **Cipher Algorithm** | AES-256-GCM | 기밀성 및 무결성(AEAD) 동시 보장 |
| **IV / Nonce** | 12 바이트 (96비트) | 암호화할 때마다 매번 새로 생성 |
| **Auth Tag** | 16 바이트 (128비트) | 변조 방지 인증 태그 |

---

## 3. 암호화 볼트 전송/저장 컨테이너 규격 (`EncryptedVaultPayload`)

백엔드로 전송되거나 로컬에 영속화되는 암호화 덩어리는 아래의 표준 JSON 구조를 갖습니다.

```json
{
  "version": 1,
  "kdf": "PBKDF2-HMAC-SHA256",
  "iterations": 100000,
  "salt": "dGhpcyBpcyBhIHNhbHQxNg==",
  "cipher": "AES-256-GCM",
  "iv": "MDEyMzQ1Njc4OWFi",
  "ciphertext": "k7Z9X...base64...",
  "tag": "YWJjZGVmZ2hpamtsbW5vcA==",
  "updatedAt": "2026-10-27T11:20:00Z"
}
```

* 모든 바이너리 필드(`salt`, `iv`, `ciphertext`, `tag`)는 **Standard Base64 (RFC 4648)** 로 인코딩됩니다.
* 마스터 키 불일치 등으로 인증 태그(`tag`) 검증에 실패할 경우, 플랫폼 클라이언트는 즉시 복호화를 중단하고 사용자에게 마스터 키 오류를 알립니다.

---

## 4. 복호화된 평문 볼트 스키마 (`VaultData`)

복호화에 성공했을 때 메모리 상에 존재하는 평문 JSON 구조입니다.

```json
{
  "version": 1,
  "updatedAt": "2026-10-27T11:20:00Z",
  "items": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "serviceName": "GitHub",
      "username": "octocat@github.com",
      "password": "SuperSecurePassword123!",
      "url": "https://github.com/login",
      "notes": "개인 개발용 서브 계정",
      "createdAt": "2026-10-27T10:00:00Z",
      "updatedAt": "2026-10-27T11:20:00Z"
    }
  ]
}
```

### 필드 정의
* `version` (Int): 볼트 스키마 버전 (기본값: 1).
* `updatedAt` (String): ISO 8601 UTC 타임스탬프 (`YYYY-MM-DDTHH:mm:ssZ`).
* `items` (Array): 계정 자격증명 목록.
  * `id` (String): UUID v4 고유 식별자.
  * `serviceName` (String): 서비스/사이트명 (예: "Google", "GitHub").
  * `username` (String): 로그인 아이디 또는 이메일.
  * `password` (String): 평문 비밀번호.
  * `url` (String, Optional): 로그인 페이지 URL.
  * `notes` (String, Optional): 사용자 메모.
  * `createdAt` (String): 등록 일시 (ISO 8601).
  * `updatedAt` (String): 최종 수정 일시 (ISO 8601).

---

## 5. 플랫폼별 표준 암호화 라이브러리 매핑

| 플랫폼 | 언어 / 프레임워크 | PBKDF2 라이브러리 | AES-256-GCM 라이브러리 |
| :--- | :--- | :--- | :--- |
| **iOS / macOS** | Swift | `CommonCrypto` (`CCKeyDerivationPBKDF`) | `CryptoKit.AES.GCM` |
| **Android** | Kotlin | `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` | `Cipher.getInstance("AES/GCM/NoPadding")` |
| **Web** | TypeScript / JS | Web Crypto API (`subtle.deriveKey`) | Web Crypto API (`subtle.encrypt({ name: "AES-GCM" })`) |
