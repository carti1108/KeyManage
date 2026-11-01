// 기기 저장소 쓰기를 한 번에 하나씩 순서대로 하고, 실패는 알린다 (ADR-0019, VLT-001)
// 쓰기 실패로 사용 중인 볼트를 막지 않는다. 메모리의 값은 그대로 쓰고, 화면에 경고하고, 떠날 때 확인을 받는다
import type { DeviceRecord, DeviceStore } from './deviceRecord'

export function createDevicePersistence(store: DeviceStore, onError: (error: unknown) => void) {
  let chain: Promise<boolean> = Promise.resolve(true)
  let requested = 0
  // 마지막으로 요청한 쓰기가 아직 끝나지 않았거나 실패함
  let behind = false
  // 마지막으로 끝난 쓰기가 실패함. 다음 쓰기가 성공하면 풀린다
  let failed = false

  /** @returns 이 쓰기가 성공했는지. 실패해도 거절하지 않고 오류는 onError로 알린다 */
  function enqueue(write: () => Promise<void>): Promise<boolean> {
    const id = ++requested
    behind = true
    chain = chain.then(write).then(
      () => {
        failed = false
        if (id === requested) behind = false
        return true
      },
      (error: unknown) => {
        failed = true
        onError(error)
        return false
      },
    )
    return chain
  }

  return {
    save: (record: DeviceRecord) => enqueue(() => store.save(record)),
    clear: () => enqueue(() => store.clear()),
    /** 마지막으로 요청한 기록이 아직 기기에 반영되지 않음 (떠남 확인용) */
    isBehind: () => behind,
    /** 마지막 쓰기가 실패해 기기의 기록이 메모리보다 오래됨 (화면 경고용) */
    hasFailed: () => failed,
  }
}
