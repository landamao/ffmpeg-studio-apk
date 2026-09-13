package com.ffmpegstudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ffmpegstudio.data.AppViewModel
import com.ffmpegstudio.ui.AppRoot

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 退出/切后台时保存会话状态（页面、工作台、折叠状态），下次启动原样恢复
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.persistSession()
        })
        setContent {
            AppRoot(vm)
        }
    }
}
