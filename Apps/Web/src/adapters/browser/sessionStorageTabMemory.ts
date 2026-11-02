// 탭 기억을 sessionStorage 키 하나에 둔다. 같은 탭의 새로고침·탭 폐기 뒤 복원에는 남고 새 탭에는 없다 (SEC-003 규칙 11)
// 사생활 보호 설정에 따라 접근만 해도 예외가 날 수 있으므로 모든 접근을 감싸고, 실패하면 기억 없음으로 동작한다
import type { RememberedKdf, TabMemory } from '../../domain/device/tabMemory'

export const TAB_MEMORY_STORAGE_KEY = 'keymanage.tabKdf'

type StorageLike = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

function parse(raw: string | null): RememberedKdf | null {
  if (raw === null) return null
  let value: unknown
  try {
    value = JSON.parse(raw)
  } catch {
    return null
  }
  if (typeof value !== 'object' || value === null) return null
  const { email, salt, iterations } = value as Record<string, unknown>
  if (typeof email !== 'string' || typeof salt !== 'string' || !Number.isSafeInteger(iterations)) return null
  return { email, salt, iterations: iterations as number }
}

/** @param getStorage 저장소를 꺼낸다. 꺼내기만 해도 예외가 날 수 있어 쓸 때마다 부른다 */
export function createSessionStorageTabMemory(getStorage: () => StorageLike = () => window.sessionStorage): TabMemory {
  return {
    get() {
      try {
        return parse(getStorage().getItem(TAB_MEMORY_STORAGE_KEY))
      } catch {
        return null
      }
    },
    set({ email, salt, iterations }) {
      try {
        getStorage().setItem(TAB_MEMORY_STORAGE_KEY, JSON.stringify({ email, salt, iterations }))
      } catch {
        // 기억하지 못하면 새로 열렸을 때 지금처럼 기기 기록으로 시작한다
      }
    },
    clear() {
      try {
        getStorage().removeItem(TAB_MEMORY_STORAGE_KEY)
      } catch {
        // 지우지 못해도 다음 로그인·잠금 해제가 덮어쓴다
      }
    },
  }
}
