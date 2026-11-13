// JsSessionState를 웹 SessionState로 바꾼다. 같은 내용이면 같은 객체를 돌려준다
// 화면이 상태 객체(와 그 안의 볼트) 동일성에 기대어 다시 그리므로, Kotlin이 호출마다 새 객체를 줘도 내용이 같으면 이전 객체를 쓴다
import type { EncryptedVaultPayload } from '../../domain/crypto/vaultCipher'
import type { SessionState } from '../../domain/session/sessionTypes'
import { parseVaultData } from '../../domain/vault/vaultData'
import type { JsSessionState } from './kotlinTypes'

function convert(js: JsSessionState): SessionState {
  switch (js.status) {
    case 'signedOut':
      return { status: 'signedOut' }
    case 'locked':
      if (js.email == null) throw new TypeError('Kotlin 잠금 상태에 이메일이 없음')
      return { status: 'locked', email: js.email, pendingSync: js.pendingSync }
    case 'unlocked': {
      if (js.email == null || js.vaultJson == null) throw new TypeError('Kotlin 잠금 해제 상태에 이메일이나 볼트가 없음')
      // payloadJson은 같은 모듈이 EncryptedVaultPayload로 만든 값이라 모양을 다시 검사하지 않는다
      const payload = js.payloadJson == null ? null : (JSON.parse(js.payloadJson) as EncryptedVaultPayload)
      return {
        status: 'unlocked',
        email: js.email,
        vault: { revision: js.revision, data: parseVaultData(js.vaultJson), readOnly: js.readOnly, payload },
        pendingSync: js.pendingSync,
        signedIn: js.signedIn,
        storedOnDevice: js.storedOnDevice,
      }
    }
    default:
      throw new TypeError(`Kotlin이 모르는 세션 상태를 줌: ${js.status}`)
  }
}

function sameContent(a: JsSessionState, b: JsSessionState): boolean {
  return (
    a.status === b.status &&
    a.email === b.email &&
    a.pendingSync === b.pendingSync &&
    a.signedIn === b.signedIn &&
    a.storedOnDevice === b.storedOnDevice &&
    a.revision === b.revision &&
    a.readOnly === b.readOnly &&
    a.vaultJson === b.vaultJson &&
    a.payloadJson === b.payloadJson
  )
}

/** 상태를 읽을 때마다 부른다. 마지막 내용과 같으면 마지막 결과를 그대로 준다 */
export function createStateReader(read: () => JsSessionState): () => SessionState {
  let last: { readonly source: JsSessionState; readonly state: SessionState } | null = null
  return () => {
    const source = read()
    if (last !== null && sameContent(last.source, source)) return last.state
    // 변환이 실패하면 이전 캐시를 그대로 둔다
    const state = convert(source)
    last = { source: snapshot(source), state }
    return state
  }
}

// Kotlin 쪽 객체가 나중에 바뀌어도 비교 기준이 흔들리지 않게 값을 복사해 둔다
function snapshot(js: JsSessionState): JsSessionState {
  const { status, email, pendingSync, signedIn, storedOnDevice, revision, readOnly, vaultJson, payloadJson } = js
  return { status, email, pendingSync, signedIn, storedOnDevice, revision, readOnly, vaultJson, payloadJson }
}
