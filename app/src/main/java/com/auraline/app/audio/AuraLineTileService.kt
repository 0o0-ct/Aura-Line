package com.auraline.app.audio

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.auraline.app.di.AuraLineModule

/**
 * Servicio de Tile para los Ajustes Rápidos (Quick Settings Tile) de Android.
 * Permite encender/apagar el motor DSP de AuraLine directamente desde la barra de estado.
 */
@RequiresApi(Build.VERSION_CODES.N)
class AuraLineTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val repo = AuraLineModule.repository(applicationContext)
        val currentState = repo.getState()
        val newState = !currentState.motorEnabled
        repo.toggleMotor(newState)
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val repo = AuraLineModule.repository(applicationContext)
        val isEnabled = repo.getState().motorEnabled

        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "AuraLine DSP"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isEnabled) "Motor Activo" else "Motor Apagado"
        }
        tile.updateTile()
    }
}
