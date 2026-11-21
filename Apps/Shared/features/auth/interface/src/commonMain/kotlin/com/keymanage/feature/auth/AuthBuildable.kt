@file:OptIn(ExperimentalObjCName::class)

package com.keymanage.feature.auth

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.carti.ribs.Buildable
import com.carti.ribs.ViewableRouting

/** 부모 RIB이 Auth를 만들 때 쓰는 계약. 구현(AuthBuilder)은 조립하는 곳이 넣는다 */
interface AuthBuildable : Buildable {
    /**
     * @param notice 로그인 화면에 띄울 안내(세션 만료, 다른 기기 비밀번호 변경, 계정 삭제 완료 등).
     * 사용자가 다시 시도하거나 모드를 바꾸면 이 RIB이 화면에서 지운다. 부모는 넘긴 뒤 자기 값을 비운다
     */
    fun build(@ObjCName("authListener") listener: AuthListener, notice: String? = null): AuthRouting
}

/** 부모 RIB이 붙이고 떼는 Auth의 Router */
interface AuthRouting : ViewableRouting {
    /** 이미 떠 있는 로그인 화면에 안내를 갈아 끼운다(로그아웃 결과, 다른 곳의 비밀번호 변경) */
    fun showNotice(notice: String)
}
