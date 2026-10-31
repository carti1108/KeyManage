// 볼트가 열린 뒤의 첫 화면. 항목 목록은 다음 단계에서 채운다
import { Lock, LogOut, WifiOff } from 'lucide-react'
import { Alert } from '../components/Alert'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { OFFLINE_NOTICE, READ_ONLY_NOTICE } from '../messages/vaultNotices'

export interface VaultHomeScreenProps {
  email: string
  itemCount: number
  readOnly: boolean
  offline: boolean
  onLock: () => void
  onLogout: () => void
}

export function VaultHomeScreen({ email, itemCount, readOnly, offline, onLock, onLogout }: VaultHomeScreenProps) {
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
      <Card>
        <p className="text-sm text-muted-foreground">저장된 항목 {itemCount}개</p>
      </Card>
    </main>
  )
}
