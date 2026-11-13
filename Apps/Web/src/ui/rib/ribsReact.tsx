// carti-RIBs-KMP react/ribs-react/src/index.tsx 복사본 (npm 패키지가 아니라 파일 하나). 고치면 원본과 맞춘다 (domain-migration-plan 11.4)
// 원본 그대로 두려고 훅과 컴포넌트를 한 파일에 둔다
/* eslint-disable react/only-export-components */
// carti-RIBs-KMP React 어댑터(ADR-0009). ribs-web의 ViewStore·ScreenSlot을 React에서 구독한다.
// iOS의 swift/RIBsKMPSwiftUI, Android의 ribs-compose에 해당한다
import { Fragment, useCallback, useSyncExternalStore, type ReactNode } from 'react'

/** ribs-web `ViewStore`·`ScreenSlot`이 만족하는 모양 */
export interface Subscribable<S> {
  subscribe(listener: () => void): () => void
  getSnapshot(): S
}

/** Kotlin 화면 상태를 구독한다. 상태가 바뀔 때만 다시 그린다(같은 값이면 같은 객체가 온다). */
export function useViewStore<S>(store: Subscribable<S>): S {
  // Kotlin 메서드는 this가 필요하므로 화살표 함수로 감싼다
  const subscribe = useCallback((listener: () => void) => store.subscribe(listener), [store])
  const getSnapshot = useCallback(() => store.getSnapshot(), [store])
  return useSyncExternalStore(subscribe, getSnapshot)
}

/** ribs-web `CurrentScreen` */
export interface CurrentScreenLike {
  readonly screenKey: string
  readonly view: unknown
}

/** RIB 뷰의 screenKey마다 그릴 React 요소를 돌려주는 함수 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any -- 화면마다 뷰 타입이 달라 호출하는 쪽에서 좁힌다
export type ScreenRenderers = Readonly<Record<string, (view: any) => ReactNode>>

export interface RibSlotProps {
  slot: Subscribable<CurrentScreenLike | null | undefined>
  screens: ScreenRenderers
}

const viewIds = new WeakMap<object, number>()
let nextViewId = 0

function identityOf(view: unknown): number {
  if (typeof view !== 'object' || view === null) throw new Error('RIB view must be an object')
  let id = viewIds.get(view)
  if (id === undefined) {
    id = nextViewId++
    viewIds.set(view, id)
  }
  return id
}

/**
 * 자식 RIB 화면 하나를 그리는 자리. Swift `RIBSlotView`에 해당한다.
 * 같은 종류의 새 RIB 화면으로 바뀌어도 이전 화면의 React 상태가 남지 않도록 뷰 객체마다 key를 준다.
 */
export function RibSlot({ slot, screens }: RibSlotProps): ReactNode {
  const current = useViewStore(slot)
  if (current == null) return null
  const render = screens[current.screenKey]
  if (render === undefined) throw new Error(`No React screen registered for RIB view '${current.screenKey}'`)
  return <Fragment key={identityOf(current.view)}>{render(current.view)}</Fragment>
}
