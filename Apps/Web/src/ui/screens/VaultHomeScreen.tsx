// 볼트가 열린 뒤의 틀. 머리글(잠그기·로그아웃)과 상태 안내 아래에 볼트 화면을 둔다
import { Lock, LogOut, WifiOff } from 'lucide-react'
import type { ReactNode } from 'react'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { OFFLINE_NOTICE, READ_ONLY_NOTICE } from '../messages/vaultNotices'

export interface VaultHomeScreenProps {
  email: string
  readOnly: boolean
  offline: boolean
  onLock: () => void
  onLogout: () => void
  children: ReactNode
}

export function VaultHomeScreen({ email, readOnly, offline, onLock, onLogout, children }: VaultHomeScreenProps) {
  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-4 bg-background p-4">
      <header className="flex items-center justify-between gap-2">
        <div className="min-w-0">
          <h1 className="text-xl font-bold">KeyManage</h1>
          <p className="truncate text-sm text-muted-foreground">{email}</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" icon={<Lock aria-hidden className="size-4" />} onClick={onLock}>
            잠그기
          </Button>
          <Button variant="ghost" size="sm" icon={<LogOut aria-hidden className="size-4" />} onClick={onLogout}>
            로그아웃
          </Button>
        </div>
      </header>
      {offline && (
        <Alert variant="info">
          <WifiOff aria-hidden className="mr-1 inline size-4" />
          {OFFLINE_NOTICE}
        </Alert>
      )}
      {readOnly && <Alert variant="info">{READ_ONLY_NOTICE}</Alert>}
      {children}
    </main>
  )
}
