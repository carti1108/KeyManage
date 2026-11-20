package com.keymanage.feature.generator

import com.carti.ribs.Dependency
import com.keymanage.domain.platform.RandomBytes
import com.keymanage.feature.ClipboardPort

interface PasswordGeneratorDependency : Dependency {
    val passwordGeneratorViewFactory: PasswordGeneratorViewFactory
    val clipboard: ClipboardPort
    val randomBytes: RandomBytes
}

/** 부모에게 올리는 이벤트. 항목 폼처럼 결과를 받아 쓰는 부모만 듣는다 */
interface PasswordGeneratorListener {
    fun didUsePassword(password: String)
    fun didClosePasswordGenerator()
}
