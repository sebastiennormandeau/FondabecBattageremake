package com.fondabec.battage.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fondabec.battage.data.MapPointEntity
import com.fondabec.battage.data.ProjectMapItem
import com.fondabec.battage.location.GeocodingService
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.maps.android.heatmaps.WeightedLatLng
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private enum class MarkerKind { PROJECT, HISTORY }

private data class MarkerUi(
    val kind: MarkerKind,
    val id: Long, // projectId ou mapPointId
    val title: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val avgDepthFt: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepthMapScreen(
    observeProjects: () -> Flow<List<ProjectMapItem>>,
    observeHistoryPoints: () -> Flow<List<MapPointEntity>>,
    onAddHistoryPoint: (name: String, addressLine: String, lat: Double, lng: Double, avgDepthFt: Double) -> Unit,
    onDeleteHistoryPoint: (id: Long) -> Unit,
    onOpenProject: (Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val projects by observeProjects().collectAsState(initial = emptyList())
    val history by observeHistoryPoints().collectAsState(initial = emptyList())

    val markers = remember(projects, history) {
        val p = projects
            .filter { it.latitude != 0.0 && it.longitude != 0.0 }
            .map {
                MarkerUi(
                    kind = MarkerKind.PROJECT,
                    id = it.id,
                    title = it.name.ifBlank { "Projet ${it.id}" },
                    address = "",
                    lat = it.latitude,
                    lng = it.longitude,
                    avgDepthFt = it.avgDepthFt
                )
            }

        val h = history
            .filter { it.latitude != 0.0 && it.longitude != 0.0 }
            .map {
                MarkerUi(
                    kind = MarkerKind.HISTORY,
                    id = it.id,
                    title = it.name.ifBlank { "Chantier" },
                    address = it.addressLine,
                    lat = it.latitude,
                    lng = it.longitude,
                    avgDepthFt = it.avgDepthFt
                )
            }

        (p + h)
    }

    val maxDepth = remember(markers) { max(1.0, markers.maxOfOrNull { it.avgDepthFt } ?: 1.0) }

    var heatmapEnabled by rememberSaveable { mutableStateOf(true) }
    var selected by remember { mutableStateOf<MarkerUi?>(null) }

    // --- Ajout historique en 2 étapes : PICK -> FORM
    var addPicking by rememberSaveable { mutableStateOf(false) }
    var addForm by rememberSaveable { mutableStateOf(false) }

    var draftName by rememberSaveable { mutableStateOf("") }
    var draftAddr by rememberSaveable { mutableStateOf("") }
    var draftAvg by rememberSaveable { mutableStateOf("") }
    var draftLatLng by remember { mutableStateOf<LatLng?>(null) }
    var draftBusy by rememberSaveable { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState()
    val uiSettings = remember {
        MapUiSettings(
            compassEnabled = true,
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false
        )
    }
    val properties = remember { MapProperties(isMyLocationEnabled = false) }

    fun resetDraft() {
        draftName = ""
        draftAddr = ""
        draftAvg = ""
        draftLatLng = null
        draftBusy = false
    }

    fun centerAll() {
        if (markers.isEmpty()) return
        val bounds = LatLngBounds.builder().apply {
            markers.forEach { include(LatLng(it.lat, it.lng)) }
        }.build()
        scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120)) }
    }

    LaunchedEffect(markers) { centerAll() }

    val heatmapProvider = remember(markers) {
        if (markers.isEmpty()) return@remember null
        HeatmapTileProvider.Builder()
            .weightedData(markers.map { WeightedLatLng(LatLng(it.lat, it.lng), max(1.0, it.avgDepthFt)) })
            .radius(50)
            .build()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Carte des projets") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Retour") }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Heatmap", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = heatmapEnabled, onCheckedChange = { heatmapEnabled = it })
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { centerAll() }) { Text("Centrer") }
                        Spacer(Modifier.width(6.dp))
                        TextButton(
                            onClick = {
                                // Entrer en mode PICK (carte interactive)
                                selected = null
                                resetDraft()
                                addPicking = true
                                addForm = false
                                Toast.makeText(context, "Long-press sur la carte pour placer le point.", Toast.LENGTH_SHORT).show()
                            }
                        ) { Text("Ajouter") }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = properties,
                uiSettings = uiSettings,
                onMapLongClick = { latLng ->
                    if (!addPicking) return@GoogleMap
                    draftLatLng = latLng

                    scope.launch {
                        draftBusy = true
                        val line = GeocodingService.reverseGeocodeLine(context, latLng.latitude, latLng.longitude)
                        if (!line.isNullOrBlank()) draftAddr = line
                        draftBusy = false

                        // Après placement -> ouvrir la fiche
                        addPicking = false
                        addForm = true
                    }
                }
            ) {
                if (heatmapEnabled && heatmapProvider != null) {
                    TileOverlay(tileProvider = heatmapProvider, transparency = 0.35f, visible = true)
                }

                markers.forEach { m ->
                    val hue = when (m.kind) {
                        MarkerKind.HISTORY -> 210f // bleu
                        MarkerKind.PROJECT -> depthHue(m.avgDepthFt, maxDepth)
                    }
                    Marker(
                        state = MarkerState(position = LatLng(m.lat, m.lng)),
                        title = m.title,
                        snippet = if (m.address.isNotBlank()) m.address else "—",
                        icon = BitmapDescriptorFactory.defaultMarker(hue),
                        onClick = { selected = m; true }
                    )
                }

                // Marker "draft" visible pendant PICK (pour feedback)
                val d = draftLatLng
                if (addPicking && d != null) {
                    Marker(
                        state = MarkerState(position = d),
                        title = "Nouveau point",
                        snippet = "Long-press pour repositionner",
                        icon = BitmapDescriptorFactory.defaultMarker(60f)
                    )
                }
            }

            // Overlay instructions quand on est en mode PICK
            if (addPicking) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(10.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Placement du point", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("Long-press sur la carte pour placer le chantier.", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    addPicking = false
                                    resetDraft()
                                }
                            ) { Text("Annuler") }

                            val d = draftLatLng
                            Button(
                                onClick = {
                                    if (d == null) {
                                        Toast.makeText(context, "Place d'abord le point (long-press).", Toast.LENGTH_SHORT).show()
                                    } else {
                                        addPicking = false
                                        addForm = true
                                    }
                                }
                            ) { Text("Continuer") }
                        }
                    }
                }
            }

            // “Aucun point”
            if (markers.isEmpty() && !addPicking) {
                Card(
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Aucun point à afficher.")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Ajoute une localisation dans un projet, ou utilise « Ajouter » pour un chantier historique.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    // --- Bottom sheet: FORM (après placement du point)
    if (addForm) {
        ModalBottomSheet(onDismissRequest = { addForm = false; resetDraft() }) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Ajouter chantier (historique)", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = draftAddr,
                    onValueChange = { draftAddr = it },
                    label = { Text("Localisation / adresse (optionnel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = draftAvg,
                        onValueChange = { draftAvg = it.replace(',', '.') },
                        label = { Text("Profondeur moyenne (ft)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                draftBusy = true
                                val target = GeocodingService.forwardGeocodeFirst(context, draftAddr)
                                if (target != null) {
                                    draftLatLng = target
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(target, 14f))
                                } else {
                                    Toast.makeText(context, "Adresse introuvable.", Toast.LENGTH_SHORT).show()
                                }
                                draftBusy = false
                            }
                        },
                        enabled = !draftBusy && draftAddr.trim().isNotBlank()
                    ) { Text("Trouver") }
                }

                Spacer(Modifier.height(10.dp))

                val ll = draftLatLng
                Text(
                    if (ll == null) "📍 Aucun point placé."
                    else "📍 Position: ${"%.6f".format(ll.latitude)}, ${"%.6f".format(ll.longitude)}",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        // Revenir en mode PICK pour replacer le point
                        addForm = false
                        addPicking = true
                        Toast.makeText(context, "Long-press pour déplacer le point.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Replacer sur la carte") }

                Spacer(Modifier.height(14.dp))

                val avg = draftAvg.trim().toDoubleOrNull()
                val canSave = draftName.trim().isNotBlank() && ll != null && avg != null

                Button(
                    onClick = {
                        val latLng = ll ?: return@Button
                        val depth = avg ?: return@Button
                        onAddHistoryPoint(
                            draftName.trim(),
                            draftAddr.trim(),
                            latLng.latitude,
                            latLng.longitude,
                            depth
                        )
                        addForm = false
                        resetDraft()
                        Toast.makeText(context, "Point ajouté.", Toast.LENGTH_SHORT).show()
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Sauvegarder") }

                Spacer(Modifier.height(18.dp))
            }
        }
    }

    // --- Bottom sheet: détail marker (tap marker)
    val sel = selected
    if (!addPicking && !addForm && sel != null) {
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(sel.title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(if (sel.address.isNotBlank()) sel.address else "Adresse non renseignée")
                Spacer(Modifier.height(8.dp))
                Text("Profondeur moyenne: ${"%.1f".format(sel.avgDepthFt)} ft")

                Spacer(Modifier.height(14.dp))

                if (sel.kind == MarkerKind.PROJECT) {
                    Button(
                        onClick = { selected = null; onOpenProject(sel.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Ouvrir le projet") }
                } else {
                    Button(
                        onClick = { onDeleteHistoryPoint(sel.id); selected = null },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Supprimer ce point") }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun depthHue(avgDepth: Double, maxDepth: Double): Float {
    val n = (avgDepth / maxDepth).coerceIn(0.0, 1.0).toFloat()
    val hue = 120f * (1f - n) // 120 vert -> 0 rouge
    return min(120f, max(0f, hue))
}
