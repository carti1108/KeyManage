// 브라우저가 아는 네트워크 연결 상태. 오프라인 경고를 켜고 끄는 데 쓴다 (VLT-002)

/** navigator.onLine. false면 확실히 끊긴 것이고, true여도 서버에 닿는다는 보장은 없다 */
export function isNetworkOnline(): boolean {
  return navigator.onLine
}

/**
 * offline·online 이벤트마다 알린다. 재연결 때 대기 내용 재전송은 pendingSyncWiring이 따로 맡는다
 * @returns 해제 함수
 */
export function subscribeNetworkStatus(onChange: () => void): () => void {
  window.addEventListener('offline', onChange)
  window.addEventListener('online', onChange)
  return () => {
    window.removeEventListener('offline', onChange)
    window.removeEventListener('online', onChange)
  }
}
