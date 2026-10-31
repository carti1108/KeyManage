// KMCard (docs/design-system.md 5.2)
import type { ComponentProps } from 'react'
import { cn } from '../cn'

const PADDING_CLASSES = {
  sm: 'p-3',
  md: 'p-4',
  lg: 'p-5',
} as const

const BASE_CLASSES = 'rounded-lg border bg-card text-card-foreground'

type Padding = keyof typeof PADDING_CLASSES

type StaticCardProps = ComponentProps<'div'> & { padding?: Padding; interactive?: false }
// 누를 수 있는 카드는 키보드로도 고를 수 있도록 button으로 그린다
type InteractiveCardProps = ComponentProps<'button'> & { padding?: Padding; interactive: true }

export type CardProps = StaticCardProps | InteractiveCardProps

export function Card(props: CardProps) {
  if (props.interactive) {
    const { padding = 'md', interactive: _interactive, type = 'button', className, ...rest } = props
    return (
      <button
        type={type}
        className={cn(
          BASE_CLASSES,
          PADDING_CLASSES[padding],
          'w-full text-left transition-colors hover:bg-muted active:scale-[0.99]',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
          className,
        )}
        {...rest}
      />
    )
  }
  const { padding = 'md', interactive: _interactive, className, ...rest } = props
  return <div className={cn(BASE_CLASSES, PADDING_CLASSES[padding], className)} {...rest} />
}
