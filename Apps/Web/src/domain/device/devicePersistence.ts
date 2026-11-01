// 기기 저장소 쓰기를 한 번에 하나씩 순서대로 하고, 실패는 알린다 (ADR-0019, VLT-001)
// 쓰기 실패로 사용 중인 볼트를 막지 않는다. 메모리의 값은 그대로 쓰고, 떠날 때 확인을 받는다
import type { DeviceRecord, DeviceStore } from './deviceRecord'

export function createDevicePersistence(store: DeviceStore, onError: (error: unknown) => void) {
  let chain: Promise<void> = Promise.resolve()
  let requested = 0
  // 마지막으로 요청한 쓰기가 아직 끝나지 않았거나 실패함
  let behind = false

  /** 실패해도 거절하지 않는다. 오류는 onError로 알린다 */
  function enqueue(write: () => Promise<void>): Promise<void> {
    const id = ++requested
    behind = true
    chain = chain.then(write).then(
      () => {
        if (id === requested) behind = false
      },
      (error: unknown) => onError(error),
    )
    return chain
  }

  return {
    save: (record: DeviceRecord) => enqueue(() => store.save(record)),
    clear: () => enqueue(() => store.clear()),
    /** 마지막으로 요청한 기록이 아직 기기에 반영되지 않음 (떠남 확인용) */
    isBehind: () => behind,
  }
}
