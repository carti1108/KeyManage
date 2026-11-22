import { clsx, type ClassValue } from 'clsx'
import { extendTailwindMerge } from 'tailwind-merge'

// 타이포그래피 역할 토큰(styles/tokens.css의 --text-*). tailwind-merge는 모르는 text-* 를 글자색으로 보므로
// 알려 주지 않으면 text-label이 text-primary-foreground 같은 글자색을 지운다. 토큰을 더하면 여기도 더한다
const TEXT_ROLES = ['display', 'title', 'heading', 'body', 'label', 'caption']

const merge = extendTailwindMerge({ extend: { theme: { text: TEXT_ROLES } } })

/** 조건부 클래스를 합치고, 겹치는 Tailwind 클래스는 뒤의 것으로 정리한다 */
export function cn(...inputs: ClassValue[]): string {
  return merge(clsx(inputs))
}
