package com.brycewg.asrkb.ui.settings.backup

import android.content.Context
import android.util.Log
import com.brycewg.asrkb.BuildConfig
import com.brycewg.asrkb.store.Prefs
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WebDAV 备份公共辅助：供主工程复用的后台任务工具。
 * 注意：不包含任何 UI 展示，调用方需自行提示。
 */
object WebDavBackupHelper {
  private const val TAG = "WebDavBackupHelper"
  private const val WEBDAV_DIRECTORY = "LexiSharp"
  private const val WEBDAV_FILENAME = "asr_keyboard_settings.json"

  sealed class UploadResult {
    object Success : UploadResult()
    data class Error(
      val statusCode: Int?,
      val responsePhrase: String?,
      val throwable: Throwable?
    ) : UploadResult()
  }

  sealed class DownloadResult {
    data class Success(val json: String) : DownloadResult()
    object NotFound : DownloadResult()
    data class Error(
      val statusCode: Int?,
      val responsePhrase: String?,
      val throwable: Throwable?
    ) : DownloadResult()
  }

  fun normalizeBaseUrl(url: String): String = url.trim().trimEnd('/')
  fun buildDirectoryUrl(baseUrl: String): String = "$baseUrl/$WEBDAV_DIRECTORY/"
  fun buildFileUrl(baseUrl: String): String = "$baseUrl/$WEBDAV_DIRECTORY/$WEBDAV_FILENAME"

  private fun createSardine(prefs: Prefs): OkHttpSardine {
    val sardine = OkHttpSardine()
    val user = prefs.webdavUsername.trim()
    val pass = prefs.webdavPassword.trim()
    if (user.isNotEmpty()) {
      sardine.setCredentials(user, pass)
    }
    return sardine
  }

  private fun ensureDirectoryExists(sardine: OkHttpSardine, dirUrl: String) {
    val normalizedDirUrl = if (dirUrl.endsWith("/")) dirUrl else "$dirUrl/"

    val exists = try {
      sardine.exists(normalizedDirUrl)
    } catch (t: Throwable) {
      // 某些 WebDAV 服务器在目录 URL 无尾斜杠或已存在时返回 3xx/405，这里通过文案粗略判断视作“已存在”
      if (isDirectoryAlreadyExistsError(t)) {
        if (BuildConfig.DEBUG) {
          Log.d(TAG, "exists($normalizedDirUrl) -> ${t.javaClass.simpleName}: ${t.message}, treat as existing directory")
        }
        true
      } else {
        throw t
      }
    }

    if (!exists) {
      try {
        sardine.createDirectory(normalizedDirUrl)
      } catch (t: Throwable) {
        if (isDirectoryAlreadyExistsError(t)) {
          if (BuildConfig.DEBUG) {
            Log.d(TAG, "createDirectory($normalizedDirUrl) -> ${t.javaClass.simpleName}: ${t.message}, treat as existing directory")
          }
        } else {
          throw t
        }
      }
    }
  }

  /**
   * 将当前偏好（含密钥）导出为 JSON 并通过 WebDAV 上传到固定路径。
   * @return true 表示上传成功；false 表示参数不全或上传失败。
   */
  suspend fun uploadSettings(context: Context, prefs: Prefs): Boolean =
    when (uploadSettingsWithStatus(context, prefs)) {
      is UploadResult.Success -> true
      is UploadResult.Error -> false
    }

  /**
   * 带详细状态的上传版本，便于 UI 显示具体错误信息。
   */
  suspend fun uploadSettingsWithStatus(context: Context, prefs: Prefs): UploadResult =
    withContext(Dispatchers.IO) {
      val rawUrl = prefs.webdavUrl.trim()
      if (rawUrl.isEmpty()) {
        return@withContext UploadResult.Error(null, "EMPTY_URL", null)
      }
      val baseUrl = normalizeBaseUrl(rawUrl)

      try {
        val sardine = createSardine(prefs)
        val dirUrl = buildDirectoryUrl(baseUrl)

        // 目录不存在则创建（容忍 301/302/405 视作“已存在/已创建”）
        ensureDirectoryExists(sardine, dirUrl)

        // 上传设置 JSON
        val payload = prefs.exportJsonString()
        val fileUrl = buildFileUrl(baseUrl)
        sardine.put(fileUrl, payload.toByteArray(Charsets.UTF_8), "application/json")

        UploadResult.Success
      } catch (t: Throwable) {
        Log.e(TAG, "WebDAV upload error: ${t.message}", t)
        UploadResult.Error(null, t.message, t)
      }
    }

  /**
   * 从 WebDAV 下载备份 JSON 文本。
   * @return JSON 字符串；若未配置 URL 或下载失败返回 null。
   */
  suspend fun downloadSettings(prefs: Prefs): String? =
    when (val result = downloadSettingsWithStatus(prefs)) {
      is DownloadResult.Success -> result.json
      is DownloadResult.NotFound -> null
      is DownloadResult.Error -> null
    }

  /**
   * 带详细状态的下载版本，便于 UI 显示具体错误信息（包括 404 备份缺失）。
   */
  suspend fun downloadSettingsWithStatus(prefs: Prefs): DownloadResult =
    withContext(Dispatchers.IO) {
      val rawUrl = prefs.webdavUrl.trim()
      if (rawUrl.isEmpty()) {
        return@withContext DownloadResult.Error(null, "EMPTY_URL", null)
      }
      val baseUrl = normalizeBaseUrl(rawUrl)
      val fileUrl = buildFileUrl(baseUrl)

      try {
        val sardine = createSardine(prefs)
        sardine.get(fileUrl).use { stream ->
          val text = stream.bufferedReader(Charsets.UTF_8).readText()
          DownloadResult.Success(text)
        }
      } catch (t: Throwable) {
        return@withContext if (isNotFoundError(t)) {
          Log.w(TAG, "WebDAV backup not found at $fileUrl: ${t.message}")
          DownloadResult.NotFound
        } else {
          Log.e(TAG, "WebDAV download error: ${t.message}", t)
          DownloadResult.Error(null, t.message, t)
        }
      }
    }

  // 粗略根据异常信息判断是否属于“目录已存在 / 尾斜杠问题”等可忽略错误
  private fun isDirectoryAlreadyExistsError(t: Throwable): Boolean {
    val msg = t.message?.lowercase() ?: return false
    return msg.contains("301") ||
      msg.contains("302") ||
      msg.contains("405") ||
      msg.contains("already exists")
  }

  // 粗略根据异常信息判断 404（备份文件不存在）场景
  private fun isNotFoundError(t: Throwable): Boolean {
    val msg = t.message?.lowercase() ?: return false
    return msg.contains("404") || msg.contains("not found")
  }
}
