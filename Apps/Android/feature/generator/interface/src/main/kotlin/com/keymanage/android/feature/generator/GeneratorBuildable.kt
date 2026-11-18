package com.keymanage.android.feature.generator

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import com.keymanage.android.domain.clipboard.ClipboardBurner
import com.keymanage.android.domain.generator.PasswordGenerator
import com.uber.rib.core.Router

/** 비밀번호 생성기 탭 RIB(목업 6단계, GEN-001). 생성은 기기 안에서만 하고 결과를 저장하지 않는다. */
interface GeneratorBuildable {
    fun build(slot: MutableState<@Composable () -> Unit>): Router<*>
}

interface GeneratorDependency {
    val clipboard: ClipboardBurner
    val generator: PasswordGenerator
}
