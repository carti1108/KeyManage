// 같은 브라우저에서 같은 앱을 연 탭끼리 맞추는 포트 (ADR-WEB-001)
// 메시지에는 "무엇이 바뀌었다"만 담는다. 키·평문·인증 해시·토큰·이메일은 탭 사이로 넘기지 않는다

export type TabMessageKind = 'record-changed' | 'locked' | 'logged-out' | 'activity'

export const TAB_MESSAGE_KINDS: readonly TabMessageKind[] = ['record-changed', 'locked', 'logged-out', 'activity']

/** device: 기기 기록 읽기·병합·쓰기, sync: 대기 내용 재전송 */
export type TabLockName = 'device' | 'sync'

export interface TabLockOptions {
  /** 잠금을 바로 얻지 못하면 fn을 실행하지 않고 null을 준다 */
  readonly ifAvailable?: boolean
}

export interface TabLock {
  /** 다른 탭과 겹치지 않게 fn을 실행한다. ifAvailable이면 잠금을 바로 못 얻을 때 fn 없이 null */
  run<T>(name: TabLockName, fn: () => Promise<T>, options?: TabLockOptions): Promise<T | null>
}

export interface TabChannel {
  post(kind: TabMessageKind): void
  /** 다른 탭이 보낸 메시지만 받는다. 형식이 틀린 메시지는 버린다 */
  subscribe(listener: (kind: TabMessageKind) => void): () => void
}

export interface Tabs {
  readonly lock: TabLock
  readonly channel: TabChannel
}

export function isTabMessageKind(value: unknown): value is TabMessageKind {
  return typeof value === 'string' && (TAB_MESSAGE_KINDS as readonly string[]).includes(value)
}
