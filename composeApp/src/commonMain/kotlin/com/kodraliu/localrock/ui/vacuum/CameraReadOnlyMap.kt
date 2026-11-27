package com.kodraliu.localrock.ui.vacuum

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kodraliu.localrock.shared.vacuum.map.ParsedMap

/** Reuse the normal map renderer without wiring room, obstacle or navigation actions. */
@Composable
internal fun CameraReadOnlyMap(map: ParsedMap?, floor: Int, modifier: Modifier, sidePadding: Dp) {
    var is3D by remember { mutableStateOf(false) }
    var yaw by remember { mutableStateOf(0f) }
    var tilt by remember { mutableStateOf(35f) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Last available map · read-only", Modifier.weight(1f),
                color = Color.LightGray, style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { is3D = !is3D; yaw = 0f; tilt = 35f }) {
                Text(if (is3D) "2D" else "3D")
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(horizontal = sidePadding),
            contentAlignment = Alignment.Center) {
            if (map == null) Text("Map unavailable", color = Color.White)
            else {
                // A new viewport (fold / unfold / rotation) starts fully framed.
                val viewport = remember(maxWidth, maxHeight) { LiveMapViewport() }
                VacuumMapImage(
                    map = map,
                    selectedRoomIds = emptyList(),
                    onRoomTap = {},
                    onObstacleTap = {},
                    is3D = is3D,
                    yawDegrees = yaw,
                    onYawBy = { yaw = (yaw + it) % 360f },
                    tiltDegrees = tilt,
                    onTiltBy = { tilt = (tilt + it).coerceIn(MAP_3D_MIN_TILT, MAP_3D_MAX_TILT) },
                    modifier = Modifier.fillMaxSize(),
                    viewportScope = floor,
                    providedMapViewport = viewport,
                    cameraReadOnlyFit = true,
                )
            }
        }
    }
}
