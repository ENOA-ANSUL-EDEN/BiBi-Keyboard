/**
 * Shizuku / root 启动前台保活服务的适配层。
 *
 * 归属模块：ui/floating
 */
package com.brycewg.asrkb.ui.floating

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.brycewg.asrkb.BuildConfig
import rikka.shizuku.Shizuku
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

internal object PrivilegedKeepAliveStarter {

    private const val TAG = "PrivKeepAlive"
    internal const val SHIZUKU_REQUEST_CODE_KEEP_ALIVE = 4102
    private const val SHIZUKU_MANAGER_PACKAGE = "moe.shizuku.manager"
    private const val SHIZUKU_PRIVILEGED_API_PACKAGE = "moe.shizuku.privileged.api"

    private val shizukuInitialized = AtomicBoolean(false)
    private val shizukuBinderReady = AtomicBoolean(false)
    private val shizukuPermissionRequestPending = AtomicBoolean(false)
    private val mainHandler by lazy(LazyThreadSafetyMode.NONE) { Handler(Looper.getMainLooper()) }

    enum class ShizukuPermissionRequestResult {
        AlreadyGranted,
        Requested,
        WaitingForBinder,
        NotInstalled,
        Failed
    }

    enum class StartMethod {
        Shizuku,
        Root,
        Normal
    }

    data class StartResult(
        val ok: Boolean,
        val method: StartMethod,
        val exitCode: Int? = null,
        val stderr: String? = null
    )

    fun initShizuku() {
        if (!shizukuInitialized.compareAndSet(false, true)) return
        try {
            Shizuku.addBinderReceivedListenerSticky {
                shizukuBinderReady.set(true)
            }
            Shizuku.addBinderDeadListener {
                shizukuBinderReady.set(false)
            }
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) Log.d(TAG, "initShizuku failed", t)
        }
    }

    fun isShizukuManagerInstalled(context: Context): Boolean {
        return getPackageInfoOrNull(context.packageManager, SHIZUKU_MANAGER_PACKAGE) != null
    }

    fun isShizukuPrivilegedApiInstalled(context: Context): Boolean {
        return getPackageInfoOrNull(context.packageManager, SHIZUKU_PRIVILEGED_API_PACKAGE) != null
    }

    fun isShizukuBinderReady(): Boolean {
        initShizuku()
        if (shizukuBinderReady.get()) return true
        val ready = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
        if (ready) {
            shizukuBinderReady.set(true)
            return true
        }
        return false
    }

    fun isShizukuGranted(context: Context): Boolean {
        initShizuku()
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun requestShizukuPermission(context: Context): ShizukuPermissionRequestResult {
        initShizuku()
        if (!isShizukuManagerInstalled(context) && !isShizukuPrivilegedApiInstalled(context)) {
            return ShizukuPermissionRequestResult.NotInstalled
        }
        if (isShizukuGranted(context)) return ShizukuPermissionRequestResult.AlreadyGranted

        if (!isShizukuBinderReady()) {
            enqueueShizukuPermissionRequest()
            // 触发一次 binder 探测：部分机型在首次调用前不会主动拉起连接。
            try {
                Shizuku.pingBinder()
            } catch (_: Throwable) {
            }
            return ShizukuPermissionRequestResult.WaitingForBinder
        }

        return if (requestShizukuPermissionNow()) {
            ShizukuPermissionRequestResult.Requested
        } else {
            ShizukuPermissionRequestResult.Failed
        }
    }

    private fun enqueueShizukuPermissionRequest() {
        if (!shizukuPermissionRequestPending.compareAndSet(false, true)) return
        val listener = object : Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() {
                try {
                    Shizuku.removeBinderReceivedListener(this)
                } catch (_: Throwable) {
                }
                shizukuPermissionRequestPending.set(false)
                mainHandler.post { requestShizukuPermissionNow() }
            }
        }
        try {
            Shizuku.addBinderReceivedListenerSticky(listener)
        } catch (t: Throwable) {
            shizukuPermissionRequestPending.set(false)
            if (BuildConfig.DEBUG) Log.d(TAG, "enqueueShizukuPermissionRequest failed", t)
        }
    }

    private fun requestShizukuPermissionNow(): Boolean {
        return try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE_KEEP_ALIVE)
                true
            } else {
                true
            }
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) Log.d(TAG, "requestShizukuPermission failed", t)
            false
        }
    }

    fun isRootProbablyAvailable(): Boolean {
        val candidates = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/vendor/bin/su",
            "/su/bin/su"
        )
        if (candidates.any { File(it).exists() }) return true

        val path = System.getenv("PATH") ?: return false
        return path.split(":").any { dir ->
            if (dir.isBlank()) return@any false
            File(dir, "su").exists()
        }
    }

    fun startKeepAliveServiceIntent(context: Context): Intent {
        val token = FloatingKeepAliveService.getOrCreateCallerToken(context)
        return Intent(context, FloatingKeepAliveService::class.java).apply {
            action = FloatingKeepAliveService.ACTION_START
            putExtra(FloatingKeepAliveService.EXTRA_CALLER_TOKEN, token)
        }
    }

    fun startKeepAliveForegroundServiceCommand(context: Context): String {
        val component = "${context.packageName}/${FloatingKeepAliveService::class.java.name}"
        val token = FloatingKeepAliveService.getOrCreateCallerToken(context)
        return buildString {
            append("am start-foreground-service")
            append(" -n ")
            append(component)
            append(" -a ")
            append(FloatingKeepAliveService.ACTION_START)
            append(" --es ")
            append(FloatingKeepAliveService.EXTRA_CALLER_TOKEN)
            append(" ")
            append(token)
        }
    }

    fun tryStartKeepAliveByShizuku(context: Context): StartResult? {
        if (!isShizukuGranted(context)) return null
        val cmd = startKeepAliveForegroundServiceCommand(context)
        return runShizukuShell(cmd) ?: StartResult(ok = false, method = StartMethod.Shizuku, stderr = "Shizuku newProcess failed")
    }

    fun tryStartKeepAliveByRoot(context: Context): StartResult? {
        if (!isRootProbablyAvailable()) return null
        val cmd = startKeepAliveForegroundServiceCommand(context)
        return runRootShell(cmd) ?: StartResult(ok = false, method = StartMethod.Root, stderr = "su exec failed")
    }

    fun startKeepAliveFallback(context: Context): StartResult {
        return try {
            FloatingKeepAliveService.start(context)
            StartResult(ok = true, method = StartMethod.Normal)
        } catch (t: Throwable) {
            StartResult(ok = false, method = StartMethod.Normal, stderr = t.message)
        }
    }

    private fun runShizukuShell(command: String): StartResult? {
        return try {
            val process = createShizukuProcess(arrayOf("sh", "-c", command)) ?: return null
            val stderr = process.errorStream?.bufferedReader()?.use { it.readText() }?.trim().orEmpty()
            val exit = try {
                process.waitFor()
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Shizuku process waitFor failed", t)
                null
            }
            val ok = exit == null || exit == 0
            StartResult(
                ok = ok,
                method = StartMethod.Shizuku,
                exitCode = exit,
                stderr = stderr.ifBlank { null }
            )
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) Log.d(TAG, "runShizukuShell failed", t)
            null
        }
    }

    @Suppress("PrivateApi")
    private fun createShizukuProcess(cmd: Array<String>): Process? {
        return try {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            m.isAccessible = true
            m.invoke(null, cmd, null, null) as? Process
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) Log.d(TAG, "createShizukuProcess failed", t)
            null
        }
    }

    private fun runRootShell(command: String): StartResult? {
        return try {
            val process = ProcessBuilder("su", "-c", command).start()
            val stderr = process.errorStream?.let { InputStreamReader(it) }?.buffered()?.use { it.readText() }?.trim().orEmpty()
            val exit = try {
                process.waitFor()
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) Log.d(TAG, "root process waitFor failed", t)
                null
            }
            val ok = exit == null || exit == 0
            StartResult(
                ok = ok,
                method = StartMethod.Root,
                exitCode = exit,
                stderr = stderr.ifBlank { null }
            )
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) Log.d(TAG, "runRootShell failed", t)
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfoOrNull(packageManager: PackageManager, packageName: String): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                packageManager.getPackageInfo(packageName, 0)
            }
        } catch (_: Throwable) {
            null
        }
    }
}
