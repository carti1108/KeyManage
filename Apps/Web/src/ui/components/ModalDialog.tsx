// 모달 대화상자 틀. body에 그려 배경을 inert로 막고 포커스를 대화상자 안에 가둔다
// 기다리는 동안 배경에서 편집·저장하면 꼬이는 작업(비밀번호 변경, 계정 삭제)이 쓴다
import { useEffect, useRef, type KeyboardEvent, type ReactNode, type RefObject } from 'react'
import { createPortal } from 'react-dom'

export interface ModalDialogProps {
  /** 제목 요소의 id (aria-labelledby) */
  labelledBy: string
  /** 본문 요소의 id (aria-describedby) */
  describedBy: string
  /** Escape 등 키 처리. 포커스 가두기 전에 부른다 */
  onKeyDown: (event: KeyboardEvent) => void
  children: ReactNode
}

export function ModalDialog({ labelledBy, describedBy, onKeyDown, children }: ModalDialogProps) {
  const overlay = useRef<HTMLDivElement>(null)
  useInertBackground(overlay)

  function keepFocusInside(event: KeyboardEvent) {
    onKeyDown(event)
    if (event.key !== 'Tab' || overlay.current === null) return
    const items = Array.from(overlay.current.querySelectorAll<HTMLElement>(FOCUSABLE))
    if (items.length === 0) return
    const first = items[0]
    const last = items[items.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  return createPortal(
    <div ref={overlay} className="fixed inset-0 z-50 flex items-center justify-center bg-overlay p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={labelledBy}
        aria-describedby={describedBy}
        className="w-full max-w-md rounded-md border border-border bg-background p-3"
        onKeyDown={keepFocusInside}
      >
        {children}
      </div>
    </div>,
    document.body,
  )
}

const FOCUSABLE = 'button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])'

/** 대화상자가 열린 동안 body의 다른 요소를 inert로 막는다. 닫히면 이 대화상자가 막은 것만 되돌린다 */
function useInertBackground(overlay: RefObject<HTMLDivElement | null>) {
  useEffect(() => {
    const own = overlay.current
    const blocked = Array.from(document.body.children).filter((element) => element !== own && !element.hasAttribute('inert'))
    for (const element of blocked) element.setAttribute('inert', '')
    return () => {
      for (const element of blocked) element.removeAttribute('inert')
    }
  }, [overlay])
}
