package com.fondabec.battage.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.fondabec.battage.data.PhotoEntity
import com.fondabec.battage.data.PileEntity
import com.fondabec.battage.data.ProjectDocumentEntity
import com.fondabec.battage.data.ProjectEntity
import com.fondabec.battage.location.ProjectLocationService
import com.fondabec.battage.report.PdfReportExporter
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.round

private enum class QuickAddMode { TOTAL, BY_GAUGE }

private data class ParsedLocation(
    val street: String,
    val city: String,
    val province: String,
    val postalCode: String,
    val country: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: Long,
    observeProject: () -> Flow<ProjectEntity?>,
    observePiles: () -> Flow<List<PileEntity>>,
    observePhotos: () -> Flow<List<PhotoEntity>>,
    observeDocuments: () -> Flow<List<ProjectDocumentEntity>>,
    onBack: () -> Unit,
    onSaveProject: (name: String, city: String) -> Unit,
    onUpdateProjectLocation: (
        street: String,
        city: String,
        province: String,
        postalCode: String,
        country: String,
        latitude: Double,
        longitude: Double
    ) -> Unit,
    onDeleteProject: () -> Unit,
    onAddPile: () -> Unit,
    onOpenPile: (Long) -> Unit,
    onQuickAddTotal: (count: Int, gaugeIn: String) -> Unit,
    onQuickAddByGauge: (qtyByGauge: Map<String, Int>) -> Unit,
    onOpenPlan: () -> Unit,
    onUploadPlanPdf: (pdfUri: String) -> Unit,
    onRemovePlanPdf: () -> Unit,
    onAddPhoto: (photoUri: String) -> Unit,
    onUpdatePhoto: (photoId: Long, includeInReport: Boolean) -> Unit,
    onDeletePhoto: (photoId: Long) -> Unit,

    // NOUVEAUX CALLBACKS POUR LES DOCS
    onUploadTechnicalDocument: (uri: Uri, title: String) -> Unit,
    onDeleteTechnicalDocument: (doc: ProjectDocumentEntity) -> Unit,
    onViewTechnicalDocument: (doc: ProjectDocumentEntity) -> Unit // <--- AJOUTÉ
) {
    val project by observeProject().collectAsStateWithLifecycle(initialValue = null)
    val piles by observePiles().collectAsStateWithLifecycle(initialValue = emptyList())
    val photos by observePhotos().collectAsStateWithLifecycle(initialValue = emptyList())
    val documents by observeDocuments().collectAsStateWithLifecycle(initialValue = emptyList())

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }

    val defaultProjectName = "Nouveau projet"

    var nameField by remember(projectId) { mutableStateOf(TextFieldValue("")) }
    var locationText by remember(projectId) { mutableStateOf("") }

    var nameEditedByUser by remember(projectId) { mutableStateOf(false) }
    var locationEditedByUser by remember(projectId) { mutableStateOf(false) }

    var showPdfDialog by remember { mutableStateOf(false) }

    // --- Gestion ajout doc technique ---
    var showDocNameDialog by remember { mutableStateOf(false) }
    var tempDocUri by remember { mutableStateOf<Uri?>(null) }
    var newDocName by remember { mutableStateOf("") }

    val techDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                tempDocUri = uri
                newDocName = "Nouveau document"
                showDocNameDialog = true
            }
        }
    )

    if (showDocNameDialog) {
        AlertDialog(
            onDismissRequest = { showDocNameDialog = false },
            title = { Text("Nom du document") },
            text = {
                OutlinedTextField(
                    value = newDocName,
                    onValueChange = { newDocName = it },
                    label = { Text("Titre (ex: Étude de sol)") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    tempDocUri?.let { uri ->
                        onUploadTechnicalDocument(uri, newDocName.ifBlank { "Document" })
                        Toast.makeText(context, "Envoi en cours...", Toast.LENGTH_SHORT).show()
                    }
                    showDocNameDialog = false
                }) { Text("Ajouter") }
            },
            dismissButton = {
                TextButton(onClick = { showDocNameDialog = false }) { Text("Annuler") }
            }
        )
    }
    // -----------------------------------

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                onUploadPlanPdf(uri.toString())
                Toast.makeText(context, "Plan PDF téléversé.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Aucun fichier PDF sélectionné.", Toast.LENGTH_SHORT).show()
            }
        }
    )

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                onAddPhoto(uri.toString())
                Toast.makeText(context, "Photo ajoutée.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Aucune image sélectionnée.", Toast.LENGTH_SHORT).show()
            }
        }
    )

    if (showPdfDialog) {
        AlertDialog(
            onDismissRequest = { showPdfDialog = false },
            title = { Text("Gérer le plan PDF") },
            text = {
                Column {
                    TextButton(onClick = {
                        showPdfDialog = false
                        onOpenPlan()
                    }) { Text("Voir le plan") }
                    TextButton(onClick = {
                        showPdfDialog = false
                        pdfPickerLauncher.launch("application/pdf")
                    }) { Text("Remplacer le plan") }
                    TextButton(onClick = {
                        showPdfDialog = false
                        onRemovePlanPdf()
                    }) { Text("Retirer le plan") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPdfDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    fun formatLocation(p: ProjectEntity): String {
        val parts = buildList {
            val st = p.street.trim()
            val city = p.city.trim()
            val prov = p.province.trim()
            val pc = p.postalCode.trim()
            val country = p.country.trim()

            if (st.isNotBlank()) add(st)
            if (city.isNotBlank()) add(city)

            val provPc = listOf(prov, pc).filter { it.isNotBlank() }.joinToString(" ")
            if (provPc.isNotBlank()) add(provPc)

            if (country.isNotBlank() && country.lowercase() != "canada") add(country)
        }

        return if (parts.isNotEmpty()) parts.joinToString(", ")
        else p.city.trim()
    }

    LaunchedEffect(project?.id) {
        val p = project ?: return@LaunchedEffect
        nameEditedByUser = false
        locationEditedByUser = false

        nameField = TextFieldValue(if (p.name.trim() == defaultProjectName) "" else p.name)
        locationText = formatLocation(p)
    }

    LaunchedEffect(project?.street, project?.city, project?.province, project?.postalCode, project?.country) {
        val p = project ?: return@LaunchedEffect
        if (!locationEditedByUser) {
            locationText = formatLocation(p)
        }
    }

    LaunchedEffect(project?.name) {
        val p = project ?: return@LaunchedEffect
        if (!nameEditedByUser) {
            nameField = TextFieldValue(if (p.name.trim() == defaultProjectName) "" else p.name)
        }
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun runGpsDetection() {
        scope.launch {
            try {
                val det = ProjectLocationService.detect(context)
                if (det == null || det.city.isBlank()) {
                    Toast.makeText(
                        context,
                        "Localisation indisponible. Active GPS / réseau puis réessaie.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                onUpdateProjectLocation(
                    det.street,
                    det.city,
                    det.province,
                    det.postalCode,
                    det.country,
                    det.latitude,
                    det.longitude
                )

                locationEditedByUser = false
                locationText = listOf(
                    det.street.trim().takeIf { it.isNotBlank() },
                    det.city.trim().takeIf { it.isNotBlank() },
                    listOf(det.province.trim(), det.postalCode.trim())
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .takeIf { it.isNotBlank() }
                ).filterNotNull().joinToString(", ")

                Toast.makeText(context, "Localisation appliquée.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Erreur GPS: ${e.message ?: "erreur"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true)

        if (!granted) {
            Toast.makeText(context, "Permission localisation refusée.", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        runGpsDetection()
    }

    fun requestGpsLocation() {
        if (hasLocationPermission()) {
            runGpsDetection()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    fun parseLocation(text: String): ParsedLocation {
        val raw = text.trim()

        if (!raw.contains(",")) {
            return ParsedLocation(
                street = "",
                city = raw,
                province = "",
                postalCode = "",
                country = ""
            )
        }

        val parts = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }

        val street = parts.getOrNull(0).orEmpty()
        val city = parts.getOrNull(1).orEmpty()

        val third = parts.getOrNull(2).orEmpty()
        val fourth = parts.getOrNull(3).orEmpty()

        val postalRegex = Regex("\\b([A-Za-z]\\d[A-Za-z])\\s?(\\d[A-Za-z]\\d)\\b")
        val postalMatch = postalRegex.find(raw)
        val postal = postalMatch?.let { (it.groupValues[1] + " " + it.groupValues[2]).uppercase() } ?: ""

        val provRegex = Regex("\\b([A-Za-z]{2})\\b")
        val prov = provRegex.find(third)?.groupValues?.getOrNull(1)?.uppercase() ?: ""

        val country = when {
            fourth.isNotBlank() -> fourth
            raw.contains("canada", ignoreCase = true) -> "Canada"
            else -> ""
        }

        return ParsedLocation(
            street = street,
            city = city,
            province = prov,
            postalCode = postal,
            country = country
        )
    }

    val total = piles.size
    val implantedCount = piles.count { it.implanted }
    val avgDepth = if (piles.isEmpty()) 0.0 else piles.map { it.depthFt }.average()
    val avgDepth1 = round(avgDepth * 10.0) / 10.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projet #$projectId") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Retour") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item {
                OutlinedTextField(
                    value = nameField,
                    onValueChange = {
                        nameField = it
                        nameEditedByUser = true
                    },
                    label = { Text("Nom du projet") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = locationText,
                        onValueChange = {
                            locationText = it
                            locationEditedByUser = true
                        },
                        label = { Text("Localisation (chantier)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = { requestGpsLocation() },
                        modifier = Modifier.widthIn(min = 90.dp)
                    ) {
                        Text("GPS")
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val p = project

                        val parsed = parseLocation(locationText)
                        val cityForCompat = parsed.city.ifBlank { locationText.trim() }

                        onSaveProject(nameField.text, cityForCompat)

                        if (p != null) {
                            val hasStructured =
                                parsed.street.isNotBlank() ||
                                        parsed.province.isNotBlank() ||
                                        parsed.postalCode.isNotBlank() ||
                                        parsed.country.isNotBlank()

                            if (hasStructured) {
                                onUpdateProjectLocation(
                                    parsed.street,
                                    cityForCompat,
                                    parsed.province,
                                    parsed.postalCode,
                                    parsed.country,
                                    p.latitude,
                                    p.longitude
                                )
                            }
                        }

                        Toast.makeText(context, "Projet enregistré.", Toast.LENGTH_SHORT).show()
                    },
                    enabled = nameField.text.trim().isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Enregistrer projet") }
            }

            item {
                val hasPlan = project?.planPdfPath?.isNotBlank() == true
                Button(
                    onClick = {
                        if (hasPlan) {
                            showPdfDialog = true
                        } else {
                            pdfPickerLauncher.launch("application/pdf")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (hasPlan) "Gérer le plan PDF (Plan de battage)" else "Associer un plan PDF (Plan de battage)")
                }
            }

            // --- SECTION DOCUMENTS TECHNIQUES ---
            item {
                Text("Documents Techniques", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }

            if (documents.isNotEmpty()) {
                items(documents, key = { it.id }) { doc ->
                    ListItem(
                        leadingContent = {
                            Icon(Icons.Default.Description, contentDescription = null)
                        },
                        headlineContent = { Text(doc.title) },
                        supportingContent = { Text("Ajouté le: ${java.text.SimpleDateFormat("dd/MM/yyyy").format(java.util.Date(doc.addedAtEpochMs))}") },
                        trailingContent = {
                            Row {
                                // --- NOUVEAU BOUTON : VOIR DANS L'APP ---
                                IconButton(onClick = { onViewTechnicalDocument(doc) }) {
                                    Icon(Icons.Default.Visibility, contentDescription = "Voir")
                                }
                                IconButton(onClick = { onDeleteTechnicalDocument(doc) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                                }
                            }
                        }
                    )
                }
            }

            item {
                Button(
                    onClick = { techDocLauncher.launch("application/pdf") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ajouter un document technique")
                }
            }
            // ------------------------------------

            item {
                val p = project
                val enabled = p != null && !isExporting
                Button(
                    onClick = {
                        val pr = p ?: return@Button
                        isExporting = true
                        scope.launch {
                            try {
                                val photosForReport = photos.filter { it.includeInReport }
                                val uri = PdfReportExporter.exportProjectReport(context, pr, piles, photosForReport)
                                PdfReportExporter.shareReportByEmail(
                                    context = context,
                                    pdfUri = uri,
                                    projectName = pr.name.ifBlank { "Projet ${pr.id}" }
                                )
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Échec de l'exportation du rapport: ${e.message ?: "erreur"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isExporting) "Génération du rapport…" else "Exporter rapport PDF")
                }
            }

            item { Text("Résumé", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top=8.dp)) }
            item { Text("• Profondeur moyenne: $avgDepth1 ft") }
            item { Text("• Pieux implantés: $implantedCount / $total") }

            // --- Photos ---
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Photos", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Text("Ajouter une photo")
                    }
                }
            }

            if (photos.isNotEmpty()) {
                item {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 128.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().height(300.dp).padding(top = 8.dp)
                    ) {
                        items(photos, key = { it.id }) { photo ->
                            PhotoItem(
                                photo = photo,
                                onUpdate = onUpdatePhoto,
                                onDelete = onDeletePhoto
                            )
                        }
                    }
                }
            }

            item { Text("Pieux ($total)", style = MaterialTheme.typography.titleLarge) }

            if (piles.isEmpty()) {
                item { Text("Aucun pieu. Ajoute-en un.") }
            } else {
                items(piles, key = { it.id }) { pile ->
                    val title = pile.pileNo.ifBlank { "Pieu" }
                    val implantedLabel = if (pile.implanted) "Implanté" else "Non implanté"
                    val g = pile.gaugeIn.ifBlank { "-" }
                    val sub = "Calibre: $g in | Prof.: ${pile.depthFt} ft | $implantedLabel"

                    ListItem(
                        headlineContent = { Text(title) },
                        supportingContent = { Text(sub) },
                        modifier = Modifier.clickable { onOpenPile(pile.id) }
                    )
                }
            }

            item { TextButton(onClick = onDeleteProject) { Text("Supprimer le projet") } }
            item { Text("") }
        }
    }
}


@Composable
private fun PhotoItem(
    photo: PhotoEntity,
    onUpdate: (photoId: Long, includeInReport: Boolean) -> Unit,
    onDelete: (photoId: Long) -> Unit
) {
    val imageUrl by produceState<Uri?>(initialValue = null, photo.storagePath) {
        value = try {
            FirebaseStorage.getInstance().getReference(photo.storagePath).downloadUrl.await()
        } catch (e: Exception) {
            null
        }
    }

    Card {
        Box(modifier = Modifier.aspectRatio(1f)) {
            val painter = rememberAsyncImagePainter(model = imageUrl)
            if (imageUrl != null) {
                Image(
                    painter = painter,
                    contentDescription = "Photo du projet",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            Checkbox(
                checked = photo.includeInReport,
                onCheckedChange = { onUpdate(photo.id, it) },
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
            )

            IconButton(
                onClick = { onDelete(photo.id) },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = Color.White)
            }
        }
    }
}