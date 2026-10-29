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

# 규격 값은 검사 대상 파일에서 읽지 않고 vault-spec 2장에서 그대로 옮겨 적는다.
# 생성기가 잘못된 값을 파일에 써도 따라가지 않도록 하기 위해서다
TRIMMED = " \u0009\u000A\u000D 　"
ITERATIONS = 600_000
KEY_LENGTH = 32
ENCRYPTION_INFO = b"keymanage-enc"
AUTH_INFO = b"keymanage-auth"
FIELDS = ["normalizedPasswordUtf8", "masterKey", "encryptionKey", "authHash"]


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
    failures = []

    kdf, hk = vectors["kdf"], vectors["hkdf"]
    if (kdf["iterations"], kdf["keyLength"]) != (ITERATIONS, KEY_LENGTH):
        failures.append("kdf 파라미터가 규격과 다름")
    if (hk["encryptionInfo"].encode(), hk["authInfo"].encode(), hk["length"]) != (ENCRYPTION_INFO, AUTH_INFO, KEY_LENGTH):
        failures.append("hkdf 파라미터가 규격과 다름")

    cases = {case["name"]: case for case in vectors["keyDerivationCases"]}
    for case in cases.values():
        normalized = normalize(case["password"]).encode("utf-8")
        master = hashlib.pbkdf2_hmac("sha256", normalized, base64.b64decode(case["salt"]), ITERATIONS, KEY_LENGTH)
        actual = {
            "normalizedPasswordUtf8": normalized,
            "masterKey": master,
            "encryptionKey": hkdf_sha256(master, ENCRYPTION_INFO, KEY_LENGTH),
            "authHash": hkdf_sha256(master, AUTH_INFO, KEY_LENGTH),
        }
        for field, value in actual.items():
            if base64.b64encode(value).decode() != case[field]:
                failures.append(f"{case['name']}.{field}")
        # 전처리 후 같은 비밀번호가 되는 케이스는 기준 케이스와 모든 값이 같아야 한다
        if "sameAs" in case:
            base = cases.get(case["sameAs"])
            if base is None or any(base[f] != case[f] for f in FIELDS):
                failures.append(f"{case['name']} != {case['sameAs']}")

    if failures:
        print("불일치:", ", ".join(failures))
        return 1
    print(f"Python 교차 검증 통과: 키 유도 {len(cases)}건 (전처리·PBKDF2·HKDF, 같은 결과 케이스 확인)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
