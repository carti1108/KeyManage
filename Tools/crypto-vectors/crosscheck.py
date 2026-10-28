#!/usr/bin/env python3
"""암호 테스트 벡터 교차 검증 (Python 표준 라이브러리).

Node WebCrypto로 만든 docs/crypto-test-vectors.json을 다른 구현으로 다시 계산해 일치하는지 본다.
전처리(NFC → 앞뒤 공백 제거 → UTF-8), PBKDF2, HKDF를 직접 구현한다. AES-GCM은 표준 라이브러리에 없어 JDK 검증기가 맡는다.

사용법: python3 Tools/crypto-vectors/crosscheck.py
"""
import base64
import hashlib
import hmac
import json
import sys
import unicodedata
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

VECTORS = Path(__file__).resolve().parents[2] / "docs" / "crypto-test-vectors.json"
TRIMMED = " \u0009\u000A\u000D 　"


def normalize(password: str) -> str:
    return unicodedata.normalize("NFC", password).strip(TRIMMED)


def hkdf_sha256(key: bytes, info: bytes, length: int) -> bytes:
    # RFC 5869: salt가 비어 있으면 해시 길이만큼의 0 바이트를 쓴다
    prk = hmac.new(b"\x00" * 32, key, hashlib.sha256).digest()
    okm, block = b"", b""
    counter = 1
    while len(okm) < length:
        block = hmac.new(prk, block + info + bytes([counter]), hashlib.sha256).digest()
        okm += block
        counter += 1
    return okm[:length]


def main() -> int:
    vectors = json.loads(VECTORS.read_text(encoding="utf-8"))
    kdf, hk = vectors["kdf"], vectors["hkdf"]
    failures = []
    for case in vectors["keyDerivationCases"]:
        normalized = normalize(case["password"]).encode("utf-8")
        master = hashlib.pbkdf2_hmac("sha256", normalized, base64.b64decode(case["salt"]), kdf["iterations"], kdf["keyLength"])
        actual = {
            "normalizedPasswordUtf8": normalized,
            "masterKey": master,
            "encryptionKey": hkdf_sha256(master, hk["encryptionInfo"].encode(), hk["length"]),
            "authHash": hkdf_sha256(master, hk["authInfo"].encode(), hk["length"]),
        }
        for field, value in actual.items():
            if base64.b64encode(value).decode() != case[field]:
                failures.append(f"{case['name']}.{field}")
    if failures:
        print("불일치:", ", ".join(failures))
        return 1
    print(f"Python 교차 검증 통과: 키 유도 {len(vectors['keyDerivationCases'])}건 (전처리·PBKDF2·HKDF)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
