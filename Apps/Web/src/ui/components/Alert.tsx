// 오류·안내 상자. 오류는 바로 읽어 주도록 alert, 안내는 status로 알린다
import type { ReactNode } from 'react'
import { cn } from '../cn'

const VARIANT_CLASSES = {
  error: 'border-destructive/40 bg-destructive/10 text-destructive',
  info: 'border-border bg-muted text-foreground',
} as const

export interface AlertProps {
  variant?: keyof typeof VARIANT_CLASSES
  title?: string
  children: ReactNode
  /** 본문 아래 버튼 등 */
  actions?: ReactNode
  /** false면 역할을 붙이지 않는다. 늘 있는 바깥 알림 영역 안에 넣을 때 쓴다 */
  live?: boolean
  className?: string
}

export function Alert({ variant = 'error', title, children, actions, live = true, className }: AlertProps) {
  return (
    <div
      role={live ? (variant === 'error' ? 'alert' : 'status') : undefined}
      className={cn('flex flex-col gap-1 rounded-md border px-3 py-2 text-label', VARIANT_CLASSES[variant], className)}
    >
      {title && <p className="font-semibold">{title}</p>}
      <p>{children}</p>
      {actions && <div className="mt-2 flex gap-2">{actions}</div>}
    </div>
  )
}
