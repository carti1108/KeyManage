// 테스트 전용: 플랫폼 간 테스트 벡터와, 60만 회 PBKDF2를 케이스마다 한 번만 하도록 캐시한 키 유도
import vectors from '../../../../../docs/crypto-test-vectors.json'
import { fromBase64 } from './base64'
import { deriveKeys, type DerivedKeys } from './keys'

export { vectors }

export type KeyCase = (typeof vectors.keyDerivationCases)[number]

const cache = new Map<string, Promise<DerivedKeys>>()

export function keysOf(name: string): Promise<DerivedKeys> {
  let derived = cache.get(name)
  if (!derived) {
    const c = vectors.keyDerivationCases.find((k) => k.name === name)
    if (!c) throw new Error(`테스트 벡터 케이스 없음: ${name}`)
    derived = deriveKeys(c.password, fromBase64(c.salt), vectors.kdf.iterations)
    cache.set(name, derived)
  }
  return derived
}
