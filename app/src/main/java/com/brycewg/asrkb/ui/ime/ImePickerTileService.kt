package com.brycewg.asrkb.ui.ime

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ImePickerTileService : TileService() {

  override fun onStartListening() {
    super.onStartListening()
    qsTile?.let { tile ->
      tile.state = Tile.STATE_INACTIVE
      tile.updateTile()
    }
  }

  override fun onClick() {
    super.onClick()
    unlockAndRun {
      val intent = Intent(this, ImePickerActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

      if (Build.VERSION.SDK_INT >= 34) {
        val pendingIntent = PendingIntent.getActivity(
          this,
          0,
          intent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        startActivityAndCollapse(pendingIntent)
      } else {
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
      }
    }
  }
}

