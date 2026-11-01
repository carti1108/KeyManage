// 브라우저가 오프라인인지 따라간다. 시작할 때 이미 끊겨 있으면 처음부터 오프라인이다 (VLT-002)
import { useSyncExternalStore } from 'react'
import { isNetworkOnline, subscribeNetworkStatus } from '../adapters/browser/networkStatus'

const isOffline = () => !isNetworkOnline()

export function useNetworkOffline(): boolean {
  return useSyncExternalStore(subscribeNetworkStatus, isOffline)
}
