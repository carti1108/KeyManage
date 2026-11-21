import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '../index.css'
import { ExampleApp } from './ExampleApp'

// 피처 Example 진입점. `npm run example`이 example.html로 연다
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ExampleApp />
  </StrictMode>,
)
