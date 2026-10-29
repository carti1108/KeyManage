import { LaunchScreen } from '../ui/screens/LaunchScreen'

// 조립 지점(Composition Root): 화면은 ui에 두고, 여기서는 어댑터를 만들어 포트로 화면에 넘기는 일만 한다 (ADR-0004)
function App() {
  return <LaunchScreen />
}

export default App
