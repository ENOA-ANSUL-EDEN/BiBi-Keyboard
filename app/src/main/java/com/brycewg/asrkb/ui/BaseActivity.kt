package com.brycewg.asrkb.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

/**
 * 基础 Activity 类
 *
 * 统一处理 Android 15 (SDK 35) 边缘到边缘显示的兼容性：
 * - 调用 enableEdgeToEdge() 确保在所有 Android 版本上行为一致
 * - 子类应在 setContentView() 后调用 WindowInsetsHelper.applySystemBarsInsets() 处理 insets
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 在 super.onCreate() 之前调用以确保窗口属性正确设置
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }
}
