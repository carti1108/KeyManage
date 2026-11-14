// 이름표가 붙은 한 줄 입력 필드
import { useId, type ComponentProps } from 'react'
import { cn } from '../cn'

export interface TextFieldProps extends ComponentProps<'input'> {
  label: string
}

export function TextField({ label, id, className, ...props }: TextFieldProps) {
  const fallbackId = useId()
  const inputId = id ?? fallbackId
  return (
    <div className={cn('flex flex-col gap-2', className)}>
      <label htmlFor={inputId} className="text-sm font-medium">
        {label}
      </label>
      <input
        id={inputId}
        className="h-10 rounded-md border border-input bg-transparent px-3 text-sm outline-none placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-ring"
        {...props}
      />
    </div>
  )
}
