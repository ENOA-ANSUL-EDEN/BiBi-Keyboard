package com.brycewg.asrkb.asr

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 本地模型加载协调器：
 * - 全局同一时刻只允许一个“加载任务”执行（跨 vendor 串行化）。
 * - 相同 key 的请求去重（不会新建加载，也不会打断当前加载）。
 * - 不同 key 的请求会取消当前加载，并以新 key 重新加载。
 */
internal object LocalModelLoadCoordinator {
  private const val TAG = "LocalModelLoadCoordinator"

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val stateMutex = Mutex()

  private var activeKey: String? = null
  private var activeJob: Job? = null

  fun request(key: String, loader: suspend () -> Unit) {
    scope.launch {
      val currentJob = coroutineContext[Job]
      if (currentJob == null) return@launch

      val (shouldRun, jobToCancel) = stateMutex.withLock {
        val prevJob = activeJob
        val sameInFlight = prevJob?.isActive == true && activeKey == key
        if (sameInFlight) return@withLock false to null

        activeKey = key
        activeJob = currentJob
        true to prevJob?.takeIf { !it.isCompleted }
      }

      if (!shouldRun) return@launch

      jobToCancel?.cancelAndJoin()
      try {
        loader()
      } catch (t: CancellationException) {
        throw t
      } catch (t: Throwable) {
        Log.e(TAG, "Local model load failed (key=$key)", t)
      } finally {
        stateMutex.withLock {
          if (activeJob == currentJob) {
            activeJob = null
            activeKey = null
          }
        }
      }
    }
  }

  fun cancel() {
    scope.launch {
      val jobToCancel = stateMutex.withLock {
        activeKey = null
        activeJob
      } ?: return@launch

      jobToCancel.cancelAndJoin()
      stateMutex.withLock {
        if (activeJob == jobToCancel) {
          activeJob = null
          activeKey = null
        }
      }
    }
  }
}
