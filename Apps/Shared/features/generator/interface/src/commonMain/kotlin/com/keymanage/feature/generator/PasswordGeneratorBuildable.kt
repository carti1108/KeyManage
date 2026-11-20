package com.keymanage.feature.generator

import com.carti.ribs.Buildable
import com.carti.ribs.ViewableRouting

/** 부모 RIB이 비밀번호 생성기를 만들 때 쓰는 계약. 구현(PasswordGeneratorBuilder)은 조립하는 곳이 넣는다 */
interface PasswordGeneratorBuildable : Buildable {
    /**
     * @param listener 항목 폼 안에서 쓸 때 넘긴다. 있으면 사용·닫기 버튼이 생긴다. 생성기 탭처럼 혼자 쓰면 null
     * @param allowCopy 복사 버튼을 둘지. 생성기 탭에서 true
     */
    fun build(listener: PasswordGeneratorListener?, allowCopy: Boolean): PasswordGeneratorRouting
}

/** 부모 RIB이 붙이고 떼는 비밀번호 생성기의 Router */
interface PasswordGeneratorRouting : ViewableRouting
