# [Contract] 백엔드 REST API 계약 명세서 (API Spec)

이 문서는 `KeyManage` 백엔드(`Back/`)와 모든 플랫폼 클라이언트(iOS, Android, Web) 간의 통신 규격을 정의한 인터페이스 계약(Contract)입니다.

---

## 1. 기본 규격 (Common Convention)

* **Base URL**: `http://localhost:8080/api/v1` (로컬) 또는 `https://<domain>/api/v1` (배포)
* **Content-Type**: `application/json; charset=UTF-8`
* **표준 에러 응답 형식 (Standard Error Response)**:
  모든 4xx, 5xx 응답은 아래 포맷을 따릅니다:
  ```json
  {
    "code": "INVALID_PARAMETER",
    "message": "비밀번호 길이는 최소 8자 이상 64자 이하이어야 합니다.",
    "timestamp": "2026-10-27T11:20:00Z"
  }
  ```

---

## 2. API 엔드포인트 상세

### 2.1. 무작위 비밀번호 생성 (Generate Password)
* **URL**: `POST /api/v1/password/generate`
* **설명**: 지정된 길이와 문자군 옵션에 맞춰 암호학적으로 안전한 무작위 비밀번호를 생성하고 즉시 엔트로피를 진단해 반환합니다.

#### Request Body
```json
{
  "length": 16,
  "includeUppercase": true,
  "includeLowercase": true,
  "includeNumbers": true,
  "includeSymbols": true
}
```
* `length` (Int, Optional): 8 이상 64 이하 (기본값: 16).
* `includeUppercase` (Boolean, Optional): 대문자 포함 여부 (기본값: true).
* `includeLowercase` (Boolean, Optional): 소문자 포함 여부 (기본값: true).
* `includeNumbers` (Boolean, Optional): 숫자 포함 여부 (기본값: true).
* `includeSymbols` (Boolean, Optional): 특수기호(`!@#$%^&*()_+-=[]{}|;:,.<>?`) 포함 여부 (기본값: true).
*(참고: 최소 1개 이상의 문자군이 true여야 함)*

#### Response (200 OK)
```json
{
  "password": "p9$K#m2@L9vX!q4R",
  "entropyScore": 96,
  "strength": "SECURE"
}
```
* `strength`: `"DANGER"` (0~59점), `"WARNING"` (60~79점), `"SECURE"` (80~100점).

---

### 2.2. 비밀번호 보안 진단 (Audit Passwords)
* **URL**: `POST /api/v1/password/audit`
* **설명**: 입력된 비밀번호 목록의 엔트로피 점수, 취약점, 중복 사용 여부를 일괄 진단합니다.

#### Request Body
```json
{
  "passwords": [
    "123456",
    "password!",
    "p9$K#m2@L9vX!q4R",
    "123456"
  ]
}
```

#### Response (200 OK)
```json
{
  "overallScore": 48,
  "totalCount": 4,
  "vulnerableCount": 2,
  "reusedCount": 2,
  "details": [
    {
      "maskedPassword": "12••••",
      "entropyScore": 15,
      "strength": "DANGER",
      "warning": "너무 짧고 단순한 숫자 조합입니다.",
      "isReused": true
    },
    {
      "maskedPassword": "pa••••",
      "entropyScore": 38,
      "strength": "DANGER",
      "warning": "사전에 등록된 흔한 비밀번호 패턴입니다.",
      "isReused": false
    },
    {
      "maskedPassword": "p9••••",
      "entropyScore": 96,
      "strength": "SECURE",
      "warning": null,
      "isReused": false
    },
    {
      "maskedPassword": "12••••",
      "entropyScore": 15,
      "strength": "DANGER",
      "warning": "다른 계정과 중복 사용된 비밀번호입니다.",
      "isReused": true
    }
  ]
}
```

---

### 2.3. 암호화 볼트 조회 (Get Vault)
* **URL**: `GET /api/v1/vault`
* **설명**: 서버에 저장된 사용자의 암호화된 볼트 데이터를 가져옵니다.

#### Request Headers
* `X-Vault-ID`: 사용자 또는 기기의 고유 볼트 ID (UUID 문자열)

#### Response (200 OK)
```json
{
  "vaultId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
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
}
```
*(저장된 볼트가 없을 경우 404 Not Found 또는 빈 페이로드 반환)*

---

### 2.4. 암호화 볼트 저장 및 동기화 (Save Vault)
* **URL**: `POST /api/v1/vault`
* **설명**: 클라이언트에서 암호화한 볼트 데이터를 서버에 저장합니다.

#### Request Body
```json
{
  "vaultId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
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
}
```

#### Response (200 OK)
```json
{
  "success": true,
  "updatedAt": "2026-10-27T11:20:00Z"
}
```
