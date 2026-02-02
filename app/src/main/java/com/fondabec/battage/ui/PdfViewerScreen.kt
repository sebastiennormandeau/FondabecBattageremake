package com.fondabec.battage.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    storagePath: String,
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // États pour le PDF Renderer
    var pfd by remember(storagePath) { mutableStateOf<ParcelFileDescriptor?>(null) }
    var renderer by remember(storagePath) { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember(storagePath) { mutableStateOf(0) }
    var pageIndex by remember(storagePath) { mutableStateOf(0) }

    // États pour l'affichage (Bitmap)
    var bitmap by remember(storagePath, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(storagePath, pageIndex) { mutableStateOf(false) }
    var error by remember(storagePath, pageIndex) { mutableStateOf<String?>(null) }

    // États pour le Zoom/Pan
    var scale by remember(storagePath, pageIndex) { mutableStateOf(1f) }
    var offset by remember(storagePath, pageIndex) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    fun resetView() {
        scale = 1f
        offset = Offset.Zero
    }

    // 1. Chargement du fichier PDF (Téléchargement Firebase -> Cache Local)
    DisposableEffect(storagePath) {
        val scope = kotlinx.coroutines.MainScope()
        val job = scope.launch {
            bitmap = null
            error = null
            isLoading = true
            pageCount = 0
            resetView()

            // Nettoyage préventif
            try { renderer?.close(); pfd?.close() } catch (_: Exception) { }
            renderer = null; pfd = null

            if (storagePath.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    try {
                        // On crée un nom de fichier local unique pour le cache
                        val localFileName = "view_" + storagePath.hashCode() + ".pdf"
                        val localFile = File(context.cacheDir, localFileName)

                        // Si pas en cache, on télécharge depuis Firebase Storage
                        if (!localFile.exists()) {
                            val storageRef = FirebaseStorage.getInstance().getReference(storagePath)
                            storageRef.getFile(localFile).await()
                        }

                        // Ouverture du PDF
                        val newPfd = ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
                        val newRenderer = PdfRenderer(newPfd)

                        withContext(Dispatchers.Main) {
                            pfd = newPfd
                            renderer = newRenderer
                            pageCount = newRenderer.pageCount
                            pageIndex = 0
                            isLoading = false
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            error = e.message ?: "Impossible de charger le document."
                            isLoading = false
                        }
                    }
                }
            } else {
                isLoading = false
                error = "Chemin du fichier invalide."
            }
        }

        onDispose {
            job.cancel()
            try { renderer?.close(); pfd?.close() } catch (_: Exception) { }
        }
    }

    // 2. Rendu de la page en Bitmap
    LaunchedEffect(renderer, pageIndex) {
        val r = renderer ?: return@LaunchedEffect
        if (pageCount <= 0) return@LaunchedEffect

        isLoading = true
        error = null
        bitmap = null
        resetView()

        val (bmp, err) = withContext(Dispatchers.IO) {
            try {
                val page = r.openPage(pageIndex)
                try {
                    // Qualité d'affichage
                    val targetMaxPx = 2500f
                    val sx = targetMaxPx / page.width.toFloat()
                    val sy = targetMaxPx / page.height.toFloat()
                    val scaleFactor = min(3.0f, min(sx, sy)).coerceAtLeast(1.0f)

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
                null to (e.message ?: "Erreur d'affichage de la page")
            }
        }

        bitmap = bmp
        error = err
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Fermer") } }
            )
        }
    ) { padding ->
        if (error != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Erreur: $error", color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        if (isLoading && bitmap == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val bmp = bitmap ?: return@Scaffold
        val image = bmp.asImageBitmap()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFEEEEEE))
                .clipToBounds()
        ) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { containerSize = it }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale
                            val panSpeed = scale
                            offset += Offset(pan.x * panSpeed, pan.y * panSpeed)
                        }
                    }
            )

            if (pageCount > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        MiniBtn("◀", enabled = pageIndex > 0) { pageIndex-- }
                        Text("${pageIndex + 1} / $pageCount", style = MaterialTheme.typography.labelMedium)
                        MiniBtn("▶", enabled = pageIndex < pageCount - 1) { pageIndex++ }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniBtn(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(32.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}