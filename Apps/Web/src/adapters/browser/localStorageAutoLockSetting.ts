// 자동 잠금 시간을 localStorage 키 하나에 둔다 (ADR-WEB-002)
// 계정과 묶지 않으므로 로그아웃·계정 전환 때 지우지 않는다. 다른 탭의 변경은 storage 이벤트로 받는다
import { InvalidAutoLockTimeoutError, isAutoLockOption } from '../../domain/lock/autoLock'
import { parseAutoLockSetting, type AutoLockSettingStore } from '../../domain/lock/autoLockSetting'

export const AUTO_LOCK_STORAGE_KEY = 'keymanage.autoLockMs'

type StorageLike = Pick<Storage, 'getItem' | 'setItem'>

/**
 * @param getStorage 저장소를 꺼낸다. 사생활 보호 설정에 따라 꺼내기만 해도 예외가 날 수 있어 쓸 때마다 부른다
 * @param events storage 이벤트를 받을 대상. 기본은 window
 */
export function createLocalStorageAutoLockSetting(
  getStorage: () => StorageLike = () => window.localStorage,
  events: Pick<Window, 'addEventListener' | 'removeEventListener'> = window,
): AutoLockSettingStore {
  // 이 탭에서 바꾸거나 다른 탭에서 받은 값. 저장에 실패해도 이번 실행에는 이 값을 쓴다
  let current: number | null = null

  return {
    read() {
      if (current !== null) return current
      try {
        return parseAutoLockSetting(getStorage().getItem(AUTO_LOCK_STORAGE_KEY))
      } catch {
        return parseAutoLockSetting(null)
      }
    },
    write(timeoutMs) {
      if (!isAutoLockOption(timeoutMs)) throw new InvalidAutoLockTimeoutError(timeoutMs)
      current = timeoutMs
      try {
        getStorage().setItem(AUTO_LOCK_STORAGE_KEY, String(timeoutMs))
      } catch {
        // 저장 공간 부족·접근 차단. 정책상 안내 없이 이번 실행에만 쓴다 (ADR-WEB-002)
      }
    },
    subscribe(listener) {
      function onStorage(event: StorageEvent) {
        // 전체 지우기(key 없음)와 다른 키는 이 설정과 무관하다
        if (event.key !== AUTO_LOCK_STORAGE_KEY) return
        current = parseAutoLockSetting(event.newValue)
        listener(current)
      }
      events.addEventListener('storage', onStorage)
      return () => events.removeEventListener('storage', onStorage)
    },
  }
}
