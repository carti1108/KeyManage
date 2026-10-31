// 백그라운드로 갈 때 앱 전환 화면에 볼트 내용이 비치지 않게 덮는다 (SEC-003)
import { Lock } from 'lucide-react'

export function PrivacyCover() {
  return (
    <div data-testid="privacy-cover" className="fixed inset-0 z-50 flex items-center justify-center bg-background">
      <Lock aria-hidden className="size-10 text-muted-foreground" />
    </div>
  )
}
