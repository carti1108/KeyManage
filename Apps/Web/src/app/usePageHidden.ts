// 탭이 백그라운드로 갔는지 따라간다. 가림 화면을 씌우는 데 쓴다 (SEC-003)
import { useSyncExternalStore } from 'react'

function subscribe(onChange: () => void): () => void {
  document.addEventListener('visibilitychange', onChange)
  return () => document.removeEventListener('visibilitychange', onChange)
}

const isHidden = () => document.visibilityState === 'hidden'

export function usePageHidden(): boolean {
  return useSyncExternalStore(subscribe, isHidden)
}
