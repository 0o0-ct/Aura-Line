package com.auraline.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(name = "AuraKit shell", showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun AuraKitShellPreview() {
    AuraKitPreviewTheme {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AuraSurface(depth = AuraDepth.Floating, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AuraStatusPill(AuraStatus("Motor activo", AuraStatusState.Active))
                    AuraChip("Ecualizador", AuraChipState.Selected, onClick = {})
                }
            }
            AuraSurface(depth = AuraDepth.Surface, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AuraBandFader(
                        value = 3.5f,
                        valueRange = -12f..12f,
                        onValueChange = {},
                        onReset = {},
                        label = "Banda"
                    )
                    AuraBandFader(
                        value = 0f,
                        valueRange = -12f..12f,
                        onValueChange = {},
                        onReset = {},
                        label = "Centro"
                    )
                }
            }
        }
    }
}

@Preview(name = "AuraKit chip states", showBackground = true)
@Composable
private fun AuraKitChipStatesPreview() {
    AuraKitPreviewTheme {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AuraChip("Plano", AuraChipState.Default, onClick = {})
            AuraChip("Voz", AuraChipState.Selected, onClick = {})
            AuraChip("Preview", AuraChipState.Preview, onClick = {})
        }
    }
}
