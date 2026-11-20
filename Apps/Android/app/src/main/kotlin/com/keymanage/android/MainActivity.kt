package com.keymanage.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.keymanage.android.designsystem.KeyManageTheme
import kotlinx.coroutines.launch

/**
 * 앱의 유일한 Activity. 화면 로직은 공유 RIB 트리([KeyManageProcess])에 있고, 여기서는 그 루트 화면을 그리고 생명주기와 조작을 알린다.
 * 화면이 다시 만들어져도(회전 등) 트리와 세션은 프로세스에 남는다
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 앱 전환 화면·화면 녹화에 볼트가 비치지 않게 한다 (SEC-003 규칙 3)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        val process = KeyManageProcess.attach(this)
        // 기기 기록을 읽기 전에는 로그인 화면을 그리지 않는다 (SEC-003 규칙 9)
        lifecycleScope.launch { process.start() }
        setContent {
            KeyManageTheme {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) { process.RootContent() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        KeyManageProcess.attach(this).app.onForeground()
    }

    override fun onStop() {
        KeyManageProcess.attach(this).app.onBackground()
        super.onStop()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        KeyManageProcess.attach(this).app.onUserInteraction()
    }

    override fun onDestroy() {
        KeyManageProcess.detach(this)
        super.onDestroy()
    }
}
