package com.fondabec.battage.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.util.Log
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fondabec.battage.data.PileEntity
import com.fondabec.battage.data.PileHotspotEntity
import com.fondabec.battage.data.ProjectEntity
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.sqrt
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.rememberCoroutineScope

private enum class PlanMode { NAV, ADD, DEL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectPlanScreen(
    projectId: Long,
    initialPageIndex: Int,
    observeProject: () -> Flow<ProjectEntity?>,
    observePiles: () -> Flow<List<PileEntity>>,
    observeHotspotsForPage: (pageIndex: Int) -> Flow<List<PileHotspotEntity>>,
    onAddHotspot: (pageIndex: Int, xNorm: Float, yNorm: Float) -> Unit,
    onAddAiDetectedPiles: (pageIndex: Int, aiPieux: List<com.fondabec.battage.utils.AiPieu>) -> Unit,
    onDeleteHotspot: (hotspotId: Long) -> Unit,
    onHotspotTap: (hotspotId: Long, currentPageIndex: Int) -> Unit,
    onUndoLastHotspot: (pageIndex: Int) -> Unit,
    onBack: () -> Unit,
    initialScale: Float = 1f,
    initialOffsetX: Float = 0f,
    initialOffsetY: Float = 0f,
    onSaveViewState: (Float, Float, Float) -> Unit = { _, _, _ -> }
) {
    val project by observeProject().collectAsStateWithLifecycle(initialValue = null)
    val planPath = project?.planPdfPath.orEmpty()

    val piles by observePiles().collectAsStateWithLifecycle(initialValue = emptyList())
    val pileById = remember(piles) { piles.associateBy { it.id } }

    val context = LocalContext.current
    val density = LocalDensity.current

    var pfd by remember(planPath) { mutableStateOf<ParcelFileDescriptor?>(null) }
    var renderer by remember(planPath) { mutableStateOf<PdfRenderer?>(null) }

    var pageCount by remember(planPath) { mutableStateOf(0) }

    var pageIndex by remember(planPath, initialPageIndex) { mutableStateOf(initialPageIndex) }

    var bitmap by remember(planPath, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(planPath, pageIndex) { mutableStateOf(false) }
    var error by remember(planPath, pageIndex) { mutableStateOf<String?>(null) }

    var scale by remember(projectId) { mutableStateOf(initialScale) }
    var offsetX by remember(projectId) { mutableStateOf(initialOffsetX) }
    var offsetY by remember(projectId) { mutableStateOf(initialOffsetY) }

    val currentScale by androidx.compose.runtime.rememberUpdatedState(scale)
    val currentOffsetX by androidx.compose.runtime.rememberUpdatedState(offsetX)
    val currentOffsetY by androidx.compose.runtime.rememberUpdatedState(offsetY)

    DisposableEffect(Unit) {
        onDispose {
            onSaveViewState(currentScale, currentOffsetX, currentOffsetY)
        }
    }

    var controlsVisible by remember(planPath) { mutableStateOf(true) }
    var mode by remember(planPath) { mutableStateOf(PlanMode.NAV) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val hotspots by observeHotspotsForPage(pageIndex).collectAsStateWithLifecycle(initialValue = emptyList())

    fun resetView() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    val coroutineScope = rememberCoroutineScope()
    var aiError by remember(planPath, pageIndex) { mutableStateOf<String?>(null) }
    var isAiProcessing by remember(planPath, pageIndex) { mutableStateOf(false) }
    // Le trigger automatique de l'IA a été retiré.

    DisposableEffect(planPath) {
        val scope = kotlinx.coroutines.MainScope()
        val job = scope.launch {
            bitmap = null
            error = null
            isLoading = true
            pageCount = 0
            mode = PlanMode.NAV

            try {
                renderer?.close()
                pfd?.close()
            } catch (_: Exception) {
            }
            renderer = null
            pfd = null

            if (planPath.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    try {
                        val remoteId = project?.remoteId
                        if (remoteId.isNullOrBlank()) {
                            throw IllegalStateException("ID distant du projet non disponible.")
                        }

                        val cacheDir = context.filesDir
                        val localFile = File(cacheDir, "$remoteId.pdf")

                        if (!localFile.exists()) {
                            val storageRef = FirebaseStorage.getInstance().getReference(planPath)
                            storageRef.getFile(localFile).await()
                        }

                        val newPfd = ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
                        val newRenderer = PdfRenderer(newPfd)

                        withContext(Dispatchers.Main) {
                            pfd = newPfd
                            renderer = newRenderer
                            pageCount = newRenderer.pageCount
                            pageIndex = pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                            isLoading = false
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            error = e.message ?: "Erreur de chargement du PDF"
                            isLoading = false
                        }
                    }
                }
            } else {
                isLoading = false
            }
        }

        onDispose {
            job.cancel()
            try {
                renderer?.close()
                pfd?.close()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(renderer, pageIndex) {
        val r = renderer ?: return@LaunchedEffect
        if (pageCount <= 0) return@LaunchedEffect
        if (pageIndex !in 0 until pageCount) return@LaunchedEffect

        isLoading = true
        error = null
        bitmap = null

        val (bmp, err) = withContext(Dispatchers.IO) {
            try {
                val page = r.openPage(pageIndex)
                try {
                    val targetMaxPx = 3400f
                    val maxPixels = 14_000_000f

                    val sx = targetMaxPx / page.width.toFloat()
                    val sy = targetMaxPx / page.height.toFloat()
                    var scaleFactor = min(4.0f, min(sx, sy)).coerceAtLeast(0.15f)

                    val basePixels = page.width.toFloat() * page.height.toFloat()
                    val pixelsAtScale = basePixels * (scaleFactor * scaleFactor)
                    if (pixelsAtScale > maxPixels) {
                        val safeScale = sqrt(maxPixels / basePixels)
                        scaleFactor = min(scaleFactor, safeScale).coerceAtLeast(0.15f)
                    }

                    val bmpW = (page.width * scaleFactor).toInt().coerceAtLeast(1)
                    val bmpH = (page.height * scaleFactor).toInt().coerceAtLeast(1)

                    val out = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    out.eraseColor(android.graphics.Color.WHITE)
                    page.render(out, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    out to null
                } finally {
                    page.close()
                }
            } catch (e: Exception) {
                null to (e.message ?: "Erreur rendu PDF")
            }
        }

        bitmap = bmp
        error = err
        isLoading = false
    }

    val navHitSlopPx = remember(density) { with(density) { 18.dp.toPx() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.name ?: "Plan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (bitmap != null) {
                        if (isAiProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).padding(end = 16.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = {
                                isAiProcessing = true
                                aiError = null
                                coroutineScope.launch {
                                    Log.d("AiPlan", "Invocation MANUELLE de Vertex AI avec contexte texte extrait du PDF...")
                                    try {
                                        val pdfText = withContext(Dispatchers.IO) {
                                            val file = java.io.File(context.filesDir, "${project?.remoteId}.pdf")
                                            if (file.exists()) {
                                                try {
                                                    val reader = PdfReader(file.absolutePath)
                                                    val sb = StringBuilder()
                                                    val pages = reader.numberOfPages
                                                    for (i in 1..pages) {
                                                        sb.append(PdfTextExtractor.getTextFromPage(reader, i)).append("\n")
                                                    }
                                                    reader.close()
                                                    sb.toString()
                                                } catch (e: Exception) {
                                                    Log.e("AiPlan", "Erreur extraction texte PDF", e)
                                                    null
                                                }
                                            } else null
                                        }
                                        
                                        val detectedPiles = com.fondabec.battage.utils.AiPlanAnalyzer.analyzePlanForPilesWithVertexAI(bitmap!!, pdfText)
                                        if (detectedPiles != null && detectedPiles.isNotEmpty()) {
                                            onAddAiDetectedPiles(pageIndex, detectedPiles)
                                        } else {
                                            aiError = "Aucun pieu détecté sur le plan actuel. (Le texte extrait contenait-il une légende ou liste claire?)"
                                        }
                                    } catch (e: Exception) {
                                        Log.e("AiPlan", "Erreur AI manuelle", e)
                                        aiError = "Erreur: ${e.message}"
                                    } finally {
                                        isAiProcessing = false
                                    }
                                }
                            }) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = "Détecter les pieux avec l'IA")
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (aiError != null) {
            AlertDialog(
                onDismissRequest = { aiError = null },
                title = { Text("Erreur d'analyse IA") },
                text = { Text(aiError ?: "") },
                confirmButton = {
                    TextButton(onClick = { aiError = null }) { Text("OK") }
                }
            )
        }
        if (planPath.isBlank()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Aucun plan PDF associé.")
                Button(onClick = {
                    Toast.makeText(context, "Veuillez associer un plan depuis l'écran de détails du projet.", Toast.LENGTH_SHORT).show()
                    onBack()
                }) { Text("Associer un plan") }
            }
            return@Scaffold
        }

        if (error != null && bitmap == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Erreur: ${error.orEmpty()}")
                Button(onClick = {
                    Toast.makeText(context, "Veuillez remplacer le plan depuis l'écran de détails du projet.", Toast.LENGTH_SHORT).show()
                    onBack()
                }) { Text("Remplacer le PDF") }
            }
            return@Scaffold
        }

        if (isLoading && bitmap == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }
            return@Scaffold
        }

        val bmp = bitmap ?: run {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("Aucune page rendue.")
            }
            return@Scaffold
        }

        val image = bmp.asImageBitmap()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF101010))
                .clipToBounds()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { containerSize = it }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
                    .pointerInput(mode, pageIndex, hotspots, containerSize, scale, image) {
                        detectTapGestures { tap ->
                            if (containerSize.width <= 0 || containerSize.height <= 0) return@detectTapGestures

                            val fit = imageFitInfo(image, containerSize)
                            val inside = tap.x in fit.left..(fit.left + fit.dispW) &&
                                    tap.y in fit.top..(fit.top + fit.dispH)
                            if (!inside) return@detectTapGestures

                            val xNorm = ((tap.x - fit.left) / fit.dispW).coerceIn(0f, 1f)
                            val yNorm = ((tap.y - fit.top) / fit.dispH).coerceIn(0f, 1f)

                            if (mode == PlanMode.ADD) {
                                onAddHotspot(pageIndex, xNorm, yNorm)
                            } else {
                                if (hotspots.isEmpty()) return@detectTapGestures
                                val threshold = (navHitSlopPx / scale.coerceAtLeast(1f)).coerceAtLeast(8f)

                                var bestId: Long? = null
                                var bestD2 = Float.POSITIVE_INFINITY
                                for (h in hotspots) {
                                    val hx = fit.left + h.xNorm * fit.dispW
                                    val hy = fit.top + h.yNorm * fit.dispH
                                    val dx = tap.x - hx
                                    val dy = tap.y - hy
                                    val d2 = dx * dx + dy * dy
                                    if (d2 < bestD2) {
                                        bestD2 = d2
                                        bestId = h.id
                                    }
                                }
                                if (bestId != null && bestD2 <= threshold * threshold) {
                                    if (mode == PlanMode.DEL) {
                                        onDeleteHotspot(bestId)
                                    } else {
                                        onHotspotTap(bestId, pageIndex)
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(planPath, pageIndex) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 8f)
                            scale = newScale
                            val panSpeed = (1.35f + (newScale - 1f) * 0.28f).coerceIn(1.35f, 2.8f)
                            offsetX += pan.x * panSpeed
                            offsetY += pan.y * panSpeed
                        }
                    }
            ) {
                Image(bitmap = image, contentDescription = "Plan PDF", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())

                val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (containerSize.width <= 0 || containerSize.height <= 0) return@Canvas
                    val fit = imageFitInfo(image, containerSize)

                    val s = scale.coerceAtLeast(1f)
                    val minOuter = 1.2.dp.toPx() // Was 1.8
                    val maxOuter = 3.2.dp.toPx() // Was 4.8
                    val outer = (maxOuter / s).coerceIn(minOuter, maxOuter)

                    val ringStroke = (0.8.dp.toPx() / s).coerceIn(0.4.dp.toPx(), 0.8.dp.toPx()) // Was 1.2, 0.6
                    val arm = outer * 1.15f
                    val crossStroke = (1.0.dp.toPx() / s).coerceIn(0.5.dp.toPx(), 1.0.dp.toPx()) // Was 1.4, 0.7
                    val haloStroke = (crossStroke * 1.9f).coerceAtLeast(crossStroke + 1f)

                    hotspots.forEach { h ->
                        val x = fit.left + h.xNorm * fit.dispW
                        val y = fit.top + h.yNorm * fit.dispH
                        val c = Offset(x, y)

                        val pile = h.pileId?.let { pileById[it] }
                        
                        val hasDepth = pile?.depthFt ?: 0.0 > 0.0
                        val green = Color(0xFF4CAF50)
                        val blue = Color(0xFF2196F3)

                        if (pile?.rebattage == true) {
                            if (hasDepth) {
                                // Moitié vert, moitié bleu
                                val rectSize = outer * 2
                                drawArc(green, startAngle = -90f, sweepAngle = 180f, useCenter = false, topLeft = Offset(c.x - outer, c.y - outer), size = Size(rectSize, rectSize), style = Stroke(ringStroke))
                                drawArc(blue, startAngle = 90f, sweepAngle = 180f, useCenter = false, topLeft = Offset(c.x - outer, c.y - outer), size = Size(rectSize, rectSize), style = Stroke(ringStroke))
                            } else {
                                // Bleu
                                drawCircle(blue, radius = outer, center = c, style = Stroke(ringStroke))
                            }
                        } else {
                             val ringColor = when {
                                mode == PlanMode.DEL -> Color.Red
                                pile == null -> Color(0xFFE57373) // Pas encore de pieu associé
                                pile.implanted && hasDepth -> green
                                pile.implanted -> Color(0xFFFFD54F) // Jaune
                                else -> Color(0xFFB0BEC5) // Gris
                            }

                            // 1. DESSINER LA PASTILLE DE FOND PLEINE
                            drawCircle(
                                color = ringColor.copy(alpha = 0.9f),
                                radius = outer * 1.5f,
                                center = c
                            )
                            
                            // 2. CONTOUR 
                            drawCircle(
                                color = Color.White,
                                radius = outer * 1.5f,
                                center = c,
                                style = Stroke(ringStroke * 1.5f)
                            )

                            // 3. AFFICHAGE DE L'ID DU PIEU
                            val label = pile?.pileNo?.takeIf { it.isNotBlank() } ?: h.id.toString()
                            
                            val textSizePx = with(density) { (14.dp / s).toPx() }.coerceAtLeast(12f)
                            
                            val textStyle = TextStyle(
                                color = if (ringColor == Color(0xFFFFD54F)) Color.Black else Color.White,
                                fontSize = with(density) { textSizePx.toSp() },
                                fontWeight = FontWeight.ExtraBold
                            )
                            
                            val textLayoutResult = textMeasurer.measure(label, style = textStyle)
                            
                            val textOffset = Offset(
                                c.x - (textLayoutResult.size.width / 2f),
                                c.y - (textLayoutResult.size.height / 2f)
                            )
                            
                            drawText(
                                textMeasurer = textMeasurer,
                                text = label,
                                style = textStyle,
                                topLeft = textOffset
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Page ${if (pageCount == 0) 0 else pageIndex + 1}/$pageCount", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f))
                        MiniToggle("NAV", selected = mode == PlanMode.NAV) { mode = PlanMode.NAV }
                        MiniToggle("ADD", selected = mode == PlanMode.ADD) { mode = PlanMode.ADD }
                        MiniToggle("DEL", selected = mode == PlanMode.DEL) { mode = PlanMode.DEL }
                        MiniTextButton("FIT") { resetView() }
                        MiniTextButton(if (controlsVisible) "▴" else "▾") { controlsVisible = !controlsVisible }
                    }

                    if (controlsVisible) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            MiniTextButton("◀", enabled = pageIndex > 0) {
                                resetView()
                                pageIndex = (pageIndex - 1).coerceAtLeast(0)
                            }
                            MiniTextButton("▶", enabled = pageCount > 0 && pageIndex < pageCount - 1) {
                                resetView()
                                pageIndex = (pageIndex + 1).coerceAtMost((pageCount - 1).coerceAtLeast(0))
                            }
                            Spacer(Modifier.weight(1f))
                            MiniTextButton("Undo", destructive = true) { onUndoLastHotspot(pageIndex) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniToggle(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent
    val shape = RoundedCornerShape(8.dp)
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.heightIn(min = 28.dp).background(bg, shape)
    ) { Text(text, style = MaterialTheme.typography.labelMedium) }
}

@Composable
private fun MiniTextButton(text: String, enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = when {
        destructive -> ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        else -> ButtonDefaults.textButtonColors()
    }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = Modifier.heightIn(min = 28.dp)
    ) { Text(text, style = MaterialTheme.typography.labelMedium) }
}

private data class FitInfo(val left: Float, val top: Float, val dispW: Float, val dispH: Float)

private fun imageFitInfo(bmp: androidx.compose.ui.graphics.ImageBitmap, container: IntSize): FitInfo {
    val cw = container.width.toFloat().coerceAtLeast(1f)
    val ch = container.height.toFloat().coerceAtLeast(1f)
    val bw = bmp.width.toFloat().coerceAtLeast(1f)
    val bh = bmp.height.toFloat().coerceAtLeast(1f)
    val s = min(cw / bw, ch / bh)
    val dispW = bw * s
    val dispH = bh * s
    val left = (cw - dispW) / 2f
    val top = (ch - dispH) / 2f
    return FitInfo(left, top, dispW, dispH)
}
