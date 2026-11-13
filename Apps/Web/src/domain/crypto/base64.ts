// Standard Base64 (RFC 4648) ↔ 바이트. 브라우저·Node·데스크톱 웹뷰에 모두 있는 btoa/atob만 쓴다

/** 표준 Base64가 아님 (공백, 패딩 누락, URL용 문자 등) */
export class InvalidBase64Error extends Error {
  constructor() {
    super('표준 Base64 형식이 아님')
    this.name = 'InvalidBase64Error'
  }
}

// atob는 공백과 패딩 누락을 너그럽게 받아들이므로, 플랫폼마다 판단이 갈리지 않게 표준 형식만 허용한다.
// 4글자 묶음 반복((?:...{4})*)은 볼트 한도 근처(약 7MB) 문자열에서 정규식 스택이 넘치므로, 문자 반복과 길이 검사로 나눈다
const STANDARD = /^[A-Za-z0-9+/]*={0,2}$/

/** @throws InvalidBase64Error 표준 Base64가 아닐 때 */
export function fromBase64(text: string): Uint8Array<ArrayBuffer> {
  if (text.length % 4 !== 0 || !STANDARD.test(text)) throw new InvalidBase64Error()
  const binary = atob(text)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  return bytes
}
