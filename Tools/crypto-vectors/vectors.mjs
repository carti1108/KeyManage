#!/usr/bin/env node
// 플랫폼 간 암호 테스트 벡터 생성기 (docs/vault-spec.md, SEC-001, SEC-002).
// 웹 클라이언트가 실제로 쓸 WebCrypto API로 계산한다. 결과는 Python·JDK 교차 검증기로 다시 확인한다.
//
// 사용법:
//   node Tools/crypto-vectors/vectors.mjs           # docs/crypto-test-vectors.json 생성
//   node Tools/crypto-vectors/vectors.mjs --check   # 파일이 다시 계산한 값과 같은지 검사 (CI)

import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const OUTPUT = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'docs', 'crypto-test-vectors.json');

const KDF = { algorithm: 'PBKDF2-HMAC-SHA256', iterations: 600_000, keyLength: 32 };
const HKDF = { hash: 'SHA-256', salt: '', encryptionInfo: 'keymanage-enc', authInfo: 'keymanage-auth', length: 32 };

// 앞뒤에서 자르는 공백 문자. 플랫폼마다 trim의 범위가 달라 문자를 고정한다 (vault-spec 2장)
const TRIMMED = [' ', '\u0009', '\u000A', '\u000D', ' ', '　'];

export function normalizePassword(password) {
  let s = password.normalize('NFC');
  let start = 0;
  let end = s.length;
  while (start < end && TRIMMED.includes(s[start])) start += 1;
  while (end > start && TRIMMED.includes(s[end - 1])) end -= 1;
  return s.slice(start, end);
}

const b64 = (bytes) => Buffer.from(bytes).toString('base64');
const range = (from, count) => Uint8Array.from({ length: count }, (_, i) => (from + i) & 0xff);

async function deriveKeys(password, salt) {
  const subtle = globalThis.crypto.subtle;
  const passwordBytes = new TextEncoder().encode(normalizePassword(password));
  const passwordKey = await subtle.importKey('raw', passwordBytes, 'PBKDF2', false, ['deriveBits']);
  const masterKey = new Uint8Array(await subtle.deriveBits(
    { name: 'PBKDF2', hash: 'SHA-256', salt, iterations: KDF.iterations }, passwordKey, KDF.keyLength * 8,
  ));
  const hkdfKey = await subtle.importKey('raw', masterKey, 'HKDF', false, ['deriveBits']);
  const expand = async (info) => new Uint8Array(await subtle.deriveBits(
    { name: 'HKDF', hash: 'SHA-256', salt: new Uint8Array(0), info: new TextEncoder().encode(info) }, hkdfKey, HKDF.length * 8,
  ));
  return {
    normalized: passwordBytes,
    masterKey,
    encryptionKey: await expand(HKDF.encryptionInfo),
    authHash: await expand(HKDF.authInfo),
  };
}

async function encrypt(encryptionKey, iv, plaintext) {
  const key = await globalThis.crypto.subtle.importKey('raw', encryptionKey, 'AES-GCM', false, ['encrypt']);
  const sealed = new Uint8Array(await globalThis.crypto.subtle.encrypt(
    { name: 'AES-GCM', iv, tagLength: 128 }, key, new TextEncoder().encode(plaintext),
  ));
  // WebCrypto는 암호문 뒤에 16바이트 인증 태그를 붙여 돌려준다. vault-spec 3장은 둘을 나눠 담는다
  return { ciphertext: sealed.slice(0, sealed.length - 16), tag: sealed.slice(sealed.length - 16) };
}

// 키 유도 케이스. sameAs가 있으면 그 케이스와 같은 결과여야 한다 (전처리가 같은 비밀번호로 만든다는 확인)
const KEY_CASES = [
  { name: 'ascii-passphrase', description: '공백이 들어간 ASCII 패스프레이즈', password: 'correct horse battery staple', salt: range(0x00, 16) },
  { name: 'korean-emoji', description: '한글과 이모지(4바이트 UTF-8)', password: '한글 비밀번호 🔑 2026', salt: range(0x10, 16) },
  { name: 'nfc-composed', description: 'é를 한 글자(U+00E9)로 입력', password: 'Café au lait', salt: range(0x20, 16) },
  { name: 'nfd-decomposed', description: 'é를 e + U+0301로 입력해도 NFC 정규화로 같은 결과', password: 'Café au lait', salt: range(0x20, 16), sameAs: 'nfc-composed' },
  { name: 'surrounding-whitespace', description: '앞뒤 공백·탭·줄바꿈·전각 공백은 잘리고 중간 공백은 유지', password: ' \t correct horse battery staple　\n', salt: range(0x00, 16), sameAs: 'ascii-passphrase' },
];

const VAULT_PLAINTEXT = JSON.stringify({
  version: 1,
  updatedAt: '2026-10-27T11:20:00Z',
  items: [{
    id: '550e8400-e29b-41d4-a716-446655440000',
    serviceName: 'GitHub',
    username: 'octocat@github.com',
    password: 'SuperSecurePassword123!',
    url: 'https://github.com/login',
    notes: '개인 개발용 서브 계정',
    createdAt: '2026-10-27T10:00:00Z',
    updatedAt: '2026-10-27T11:20:00Z',
  }],
});

async function build() {
  const keyCases = [];
  const byName = {};
  for (const c of KEY_CASES) {
    const keys = await deriveKeys(c.password, c.salt);
    byName[c.name] = keys;
    keyCases.push({
      name: c.name,
      description: c.description,
      password: c.password,
      normalizedPasswordUtf8: b64(keys.normalized),
      salt: b64(c.salt),
      masterKey: b64(keys.masterKey),
      encryptionKey: b64(keys.encryptionKey),
      authHash: b64(keys.authHash),
      ...(c.sameAs ? { sameAs: c.sameAs } : {}),
    });
  }

  const iv = range(0x00, 12);
  const sealed = await encrypt(byName['ascii-passphrase'].encryptionKey, iv, VAULT_PLAINTEXT);
  const encryptionCases = [{
    name: 'vault-ascii-passphrase',
    description: 'ascii-passphrase의 암호화 키로 vault-spec 4장 예시 볼트를 암호화. 고정 IV는 테스트 전용이며 실제로는 매번 새로 만든다',
    keyFrom: 'ascii-passphrase',
    plaintext: VAULT_PLAINTEXT,
    payload: {
      version: 1,
      cipher: 'AES-256-GCM',
      iv: b64(iv),
      ciphertext: b64(sealed.ciphertext),
      tag: b64(sealed.tag),
      updatedAt: '2026-10-27T11:20:00Z',
    },
  }];

  return {
    description: 'KeyManage 플랫폼 간 암호 테스트 벡터. 모든 바이너리 값은 Standard Base64. 생성: Tools/crypto-vectors/vectors.mjs',
    passwordPreprocessing: {
      steps: ['NFC 정규화', '앞뒤의 지정 공백 문자 제거 (중간 공백은 유지)', 'UTF-8 인코딩'],
      trimmedCodePoints: TRIMMED.map((ch) => `U+${ch.codePointAt(0).toString(16).toUpperCase().padStart(4, '0')}`),
    },
    kdf: KDF,
    hkdf: HKDF,
    aead: { algorithm: 'AES-256-GCM', ivLength: 12, tagLength: 16, aad: null },
    keyDerivationCases: keyCases,
    encryptionCases,
  };
}

const vectors = await build();
for (const c of vectors.keyDerivationCases.filter((k) => k.sameAs)) {
  const base = vectors.keyDerivationCases.find((k) => k.name === c.sameAs);
  if (base.masterKey !== c.masterKey || base.authHash !== c.authHash) throw new Error(`${c.name}이 ${c.sameAs}와 다름: 전처리 규칙 위반`);
}
const json = `${JSON.stringify(vectors, null, 2)}\n`;

if (process.argv.includes('--check')) {
  const current = readFileSync(OUTPUT, 'utf8').replace(/\r\n/g, '\n');
  if (current !== json) {
    console.error('docs/crypto-test-vectors.json이 다시 계산한 값과 다릅니다. 직접 고치지 말고 생성기로 만드세요.');
    process.exit(1);
  }
  console.log('테스트 벡터 일치');
} else {
  writeFileSync(OUTPUT, json);
  console.log(`생성: ${OUTPUT}`);
}
