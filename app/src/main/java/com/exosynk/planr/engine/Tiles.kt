package com.exosynk.planr.engine

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.exosynk.planr.PlanrApp
import com.exosynk.planr.smart.ShareActivity
import com.exosynk.planr.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opens an activity from a tile, collapsing the shade (API 34 needs a PendingIntent). */
@SuppressLint("StartActivityAndCollapseDeprecated")
private fun TileService.launch(intent: Intent) {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (Build.VERSION.SDK_INT >= 34) {
        startActivityAndCollapse(
            PendingIntent.getActivity(this, intent.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
    } else {
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
    }
}

/** Start / stop a 25-minute focus block (your shielded apps are blocked). */
class FocusTileService : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        val app = PlanrApp.instance
        val focus = app.focus
        if (focus.state.value.active) {
            if (!app.settings.isLockdown()) focus.stop()
            refresh()
        } else {
            app.appScope.launch {
                val blocked = app.db.shieldDao().getAll().filter { it.enabled }.map { it.packageName }.toSet()
                focus.start(25, 5, 1, blocked)
                withContext(Dispatchers.Main) { refresh() }
            }
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val s = PlanrApp.instance.focus.advance()
        tile.state = if (s.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Focus"
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (s.active) {
                val left = ((s.phaseEndAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
                "${if (s.phase == com.exosynk.planr.data.FocusPhase.FOCUS) "Focus" else "Break"} · ${left}m left"
            } else "25 min"
        }
        tile.updateTile()
    }
}

/** Quick add: the smart-input sheet ("call Parth at 9 am"). */
class QuickAddTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply { state = Tile.STATE_INACTIVE; label = "Quick add"; updateTile() }
    }

    override fun onClick() {
        if (PlanrApp.instance.offGrid.isLocked()) return
        launch(Intent(this, ShareActivity::class.java))
    }
}

/** Opens Off-Grid setup. Never locks the phone from a single tap. */
class OffGridTileService : TileService() {
    override fun onStartListening() {
        val locked = PlanrApp.instance.offGrid.isLocked()
        qsTile?.apply {
            state = if (locked) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Off-Grid"
            if (Build.VERSION.SDK_INT >= 29) subtitle = if (locked) "On" else "Set up"
            updateTile()
        }
    }

    override fun onClick() {
        launch(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_OFFGRID))
    }
}
