@file:OptIn(ExperimentalJsExport::class)

package com.keymanage.websession

import com.carti.ribs.ViewControllable
import com.carti.ribs.web.WebViewControllable
import com.keymanage.feature.messages.ErrorMessage

// 웹 뷰 구현(WebXxxView)이 함께 쓰는 것 (domain-migration-plan 11.3, carti ADR-0009)
// React는 뷰의 store를 구독해 그리고, 입력은 뷰가 내보낸 함수로 전달한다. 상태 클래스는 JS가 읽기 쉬운 값(문자열·숫자·불린·배열)만 든다

/** 제목이 없으면 본문만 보인다 */
@JsExport
data class WebMessage(val title: String?, val body: String)

internal fun ErrorMessage.toWeb(): WebMessage = WebMessage(title, body)

internal fun ViewControllable.asWeb(): WebViewControllable =
    this as? WebViewControllable ?: error("${this::class.simpleName} is not a WebViewControllable")

