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

### 2.1. 무작위 비밀번호 생성 · 2.2. 비밀번호 보안 진단 (제거됨)
* 제거됨 ([ADR-0005](adr/ADR-0005-client-only-password-generation-and-audit.md)): 비밀번호 생성과 진단은 평문 비밀번호가 서버로 가지 않도록 각 클라이언트에서 수행하며, 규칙은 [GEN-001](policies/GEN/GEN-001-password-generation-and-entropy-scoring.md)을 따른다.
* 섹션 번호는 기존 참조가 깨지지 않도록 비워 둔다.

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
