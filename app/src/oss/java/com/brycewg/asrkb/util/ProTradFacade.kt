package com.brycewg.asrkb.util

import android.content.Context

/**
 * OSS 变体占位：不进行任何繁体转换。
 */
object ProTradFacade {
  fun maybeToTraditional(context: Context, input: String): String = input
}

