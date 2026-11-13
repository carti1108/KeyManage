import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import * as kotlinModule from 'keymanage-web-session'
import './index.css'
import App from './app/App.tsx'
import { bootKotlinApp, createKotlinApp } from './app/kotlinApp'

// RIB 트리는 React 밖에서 한 번만 시작한다. StrictMode의 이중 마운트가 RIB을 두 번 붙이지 않게 한다 (carti ADR-0009)
const kotlinApp = createKotlinApp(kotlinModule)
// 기기 기록을 읽기 전에는 로그인 화면을 그리지 않는다 (SEC-003, ADR-0019)
void bootKotlinApp(kotlinApp).then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App kotlinApp={kotlinApp} />
    </StrictMode>,
  )
})
