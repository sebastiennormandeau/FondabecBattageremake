package com.fondabec.battage.report

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.core.content.FileProvider
import com.fondabec.battage.R
import com.fondabec.battage.data.PhotoEntity
import com.fondabec.battage.data.PileEntity
import com.fondabec.battage.data.PileShape
import com.fondabec.battage.data.ProjectEntity
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Exporte un rapport de projet au format PDF.
 * Version Finale V2 : Logo encore plus gros et centré.
 */
object PdfReportExporter {

    private const val TAG = "PdfReportExporter"

    // Dimensions de la page (A4 en points)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f

    // --- MODIFICATION : Hauteur augmentée à 120f pour un plus gros logo ---
    private const val HEADER_HEIGHT = 120f
    private const val FOOTER_HEIGHT = 40f

    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

    // Couleur principale pour l'identité visuelle
    private val FONDABEC_BLUE = Color.rgb(0, 51, 102)

    // Définition des styles de peinture
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = FONDABEC_BLUE
        textSize = 16f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 10f
    }
    private val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 9f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 9f
    }
    private val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 8f
    }
    private val tableHeaderBgPaint = Paint().apply {
        color = FONDABEC_BLUE
        style = Paint.Style.FILL
    }
    private val photoTitlePaint = TextPaint(bodyPaint).apply {
        textSize = 8f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        textAlign = Paint.Align.CENTER
    }

    // Variables d'état du document
    private lateinit var pdfDocument: PdfDocument
    private lateinit var currentPage: PdfDocument.Page
    private lateinit var currentCanvas: Canvas
    private var pageNumber = 0
    private var cursorY = 0f

    // Références pour les en-têtes/pieds de page
    private var logoBitmap: Bitmap? = null
    private var projectForHeader: ProjectEntity? = null
    private val reportDate = SimpleDateFormat("d MMMM yyyy", Locale.CANADA_FRENCH).format(Date())

    suspend fun exportProjectReport(
        context: Context,
        project: ProjectEntity,
        piles: List<PileEntity>,
        photos: List<PhotoEntity>
    ): Uri = withContext(Dispatchers.IO) {

        Log.d(TAG, "Début génération rapport. Nombre de photos demandées : ${photos.size}")

        // Initialisation
        pdfDocument = PdfDocument()
        projectForHeader = project

        // --- MODIFICATION : Taille max du logo passée à 100f ---
        logoBitmap = getLogoBitmap(context)?.let { scaleBitmap(it, maxHeight = 100f) }

        val photoBitmaps = downloadPhotoBitmaps(photos)
        Log.d(TAG, "Nombre de photos téléchargées avec succès : ${photoBitmaps.size}")

        // Génération du contenu
        startNewPage()
        drawProjectInfoSection(project, piles)
        drawPilesTableSection(piles)
        drawPhotosSection(photoBitmaps)

        // Finalisation
        finishCurrentPage()
        val file = createPdfFile(context, project)
        try {
            FileOutputStream(file).use { pdfDocument.writeTo(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Échec de l'écriture du fichier PDF", e)
            throw e
        } finally {
            pdfDocument.close()
            logoBitmap?.recycle()
            photoBitmaps.values.forEach { it.recycle() }
            // Réinitialisation
            logoBitmap = null
            projectForHeader = null
            pageNumber = 0
        }

        return@withContext getUriForFile(context, file)
    }

    private fun startNewPage() {
        if (pageNumber > 0) {
            finishCurrentPage()
        }
        pageNumber++
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        currentPage = pdfDocument.startPage(pageInfo)
        currentCanvas = currentPage.canvas
        drawPageHeader()
        cursorY = HEADER_HEIGHT
    }

    private fun finishCurrentPage() {
        drawPageFooter()
        pdfDocument.finishPage(currentPage)
    }

    private fun checkPageBreak(neededHeight: Float) {
        if (cursorY + neededHeight > PAGE_HEIGHT - FOOTER_HEIGHT) {
            startNewPage()
        }
    }

    private fun drawPageHeader() {
        var textStartX = MARGIN + 20f

        logoBitmap?.let { bmp ->
            // Le centrage est calculé dynamiquement ici, ça s'adaptera tout seul.
            val logoY = (HEADER_HEIGHT - bmp.height) / 2f
            currentCanvas.drawBitmap(bmp, MARGIN, logoY, null)
            textStartX = MARGIN + bmp.width + 25f
        }

        val title = "Rapport de Battage de Pieux"
        currentCanvas.drawText(title, textStartX, MARGIN + 15f, titlePaint)

        projectForHeader?.let {
            val projectName = it.name.trim().ifBlank { "Projet #${it.id}" }
            currentCanvas.drawText(projectName, textStartX, MARGIN + 35f, subtitlePaint)
        }

        val dateWidth = subtitlePaint.measureText(reportDate)
        currentCanvas.drawText(reportDate, PAGE_WIDTH - MARGIN - dateWidth, MARGIN + 15f, subtitlePaint)

        currentCanvas.drawLine(MARGIN, HEADER_HEIGHT - 10, PAGE_WIDTH - MARGIN, HEADER_HEIGHT - 10, footerPaint)
    }

    private fun drawPageFooter() {
        val pageText = "Page $pageNumber"
        val textWidth = footerPaint.measureText(pageText)
        currentCanvas.drawText(pageText, PAGE_WIDTH - MARGIN - textWidth, PAGE_HEIGHT - MARGIN / 2, footerPaint)
    }

    private fun drawProjectInfoSection(project: ProjectEntity, piles: List<PileEntity>) {
        cursorY += 20f
        val projectTitle = "Détails du Projet"
        currentCanvas.drawText(projectTitle, MARGIN, cursorY, titlePaint)
        cursorY += 20f

        val totalPiles = piles.size
        val implantedPiles = piles.count { it.implanted }
        val avgDepth = piles.filter { it.implanted }.map { it.depthFt }.average()
        val avgDepthText = if (avgDepth.isNaN()) "N/A" else String.format(Locale.CANADA, "%.1f ft", avgDepth)

        val info = """
            Ville: ${project.city.trim().ifBlank { "N/A" }}
            Nombre de pieux: $totalPiles (dont $implantedPiles implantés)
            Profondeur moyenne: $avgDepthText
        """.trimIndent()

        val textLayout = StaticLayout.Builder.obtain(info, 0, info.length, subtitlePaint, CONTENT_WIDTH.toInt()).build()
        currentCanvas.save()
        currentCanvas.translate(MARGIN, cursorY)
        textLayout.draw(currentCanvas)
        currentCanvas.restore()
        cursorY += textLayout.height + 20f
    }

    private fun drawPilesTableSection(piles: List<PileEntity>) {
        if (piles.isEmpty()) return

        cursorY += 20f
        checkPageBreak(50f)
        currentCanvas.drawText("Liste des Pieux", MARGIN, cursorY, titlePaint)
        cursorY += 20f

        drawPilesTableHeader()

        piles.forEach { pile ->
            checkPageBreak(20f)

            val fullNo = pile.pileNo.trim()
            val numberToDraw = if (fullNo.contains('-')) fullNo.substringAfterLast('-', "") else fullNo.ifBlank { "(auto)" }

            val gauge = pile.gaugeIn.trim().ifBlank { "—" }
            val depth = if (pile.depthFt == 0.0) "—" else String.format(Locale.CANADA, "%.2f", pile.depthFt)
            val status = if (pile.implanted) "Implanté" else "Non implanté"
            val rebattage = if (pile.rebattage) "Oui" else "Non"

            drawPileNumberWithShape(currentCanvas, MARGIN + 5, cursorY, pile, numberToDraw, bodyPaint)

            currentCanvas.drawText(gauge, MARGIN + 85, cursorY, bodyPaint)
            currentCanvas.drawText(depth, MARGIN + 205, cursorY, bodyPaint)
            currentCanvas.drawText(status, MARGIN + 305, cursorY, bodyPaint)
            currentCanvas.drawText(rebattage, MARGIN + 405, cursorY, bodyPaint)
            cursorY += 20f
        }
    }

    private fun drawPilesTableHeader() {
        val headerY = cursorY
        currentCanvas.drawRect(MARGIN, headerY, PAGE_WIDTH - MARGIN, headerY + 15f, tableHeaderBgPaint)
        cursorY += 12f

        currentCanvas.drawText("PIEU N°", MARGIN + 5, cursorY, headerPaint)
        currentCanvas.drawText("CALIBRE (PO)", MARGIN + 85, cursorY, headerPaint)
        currentCanvas.drawText("PROFONDEUR (FT)", MARGIN + 205, cursorY, headerPaint)
        currentCanvas.drawText("STATUT", MARGIN + 305, cursorY, headerPaint)
        currentCanvas.drawText("REBATTAGE", MARGIN + 405, cursorY, headerPaint)

        cursorY += 25f
    }

    private fun drawHexagon(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val hexPath = Path()
        for (i in 0..5) {
            val angle = 60.0 * i * 3.14159 / 180.0
            val hx = cx + (radius * cos(angle)).toFloat()
            val hy = cy + (radius * sin(angle)).toFloat()
            if (i == 0) {
                hexPath.moveTo(hx, hy)
            } else {
                hexPath.lineTo(hx, hy)
            }
        }
        hexPath.close()
        canvas.drawPath(hexPath, paint)
    }

    private fun drawPileNumberWithShape(canvas: Canvas, x: Float, y: Float, pile: PileEntity, text: String, paint: TextPaint) {
        if (pile.shape.isBlank()) {
            canvas.drawText(text, x + 30f, y, paint)
            return
        }

        val shape = try { PileShape.valueOf(pile.shape) } catch (e: Exception) { PileShape.CIRCLE }

        val textPaint = TextPaint(paint).apply {
            textAlign = Paint.Align.CENTER
            textSize = 7f
        }
        val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.BLACK
            strokeWidth = 1f
        }

        val cx = x + 30f
        val cy = y - 8f
        val shapeRadius = 8f
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2

        when (shape) {
            PileShape.CIRCLE -> canvas.drawCircle(cx, cy, shapeRadius, shapePaint)
            PileShape.SQUARE -> canvas.drawRect(cx - shapeRadius, cy - shapeRadius, cx + shapeRadius, cy + shapeRadius, shapePaint)
            PileShape.DIAMOND -> {
                val path = Path()
                path.moveTo(cx, cy - shapeRadius)
                path.lineTo(cx + shapeRadius, cy)
                path.lineTo(cx, cy + shapeRadius)
                path.lineTo(cx - shapeRadius, cy)
                path.close()
                canvas.drawPath(path, shapePaint)
            }
            PileShape.TRIANGLE -> {
                val path = Path()
                path.moveTo(cx, cy - shapeRadius)
                path.lineTo(cx + shapeRadius, cy + shapeRadius)
                path.lineTo(cx - shapeRadius, cy + shapeRadius)
                path.close()
                canvas.drawPath(path, shapePaint)
            }
            PileShape.HEXAGON -> {
                drawHexagon(canvas, cx, cy, shapeRadius, shapePaint)
            }
            PileShape.SQUARE_HEX -> {
                canvas.drawRect(cx - shapeRadius, cy - shapeRadius, cx + shapeRadius, cy + shapeRadius, shapePaint)
                drawHexagon(canvas, cx, cy, shapeRadius * 0.7f, shapePaint)
            }
            PileShape.CIRCLE_HEX -> {
                canvas.drawCircle(cx, cy, shapeRadius, shapePaint)
                drawHexagon(canvas, cx, cy, shapeRadius * 0.7f, shapePaint)
            }
            PileShape.SQUARE_SQUARE -> {
                canvas.drawRect(cx - shapeRadius, cy - shapeRadius, cx + shapeRadius, cy + shapeRadius, shapePaint)
                canvas.drawRect(cx - shapeRadius * 0.5f, cy - shapeRadius * 0.5f, cx + shapeRadius * 0.5f, cy + shapeRadius * 0.5f, shapePaint)
            }
            PileShape.CIRCLE_CIRCLE -> {
                canvas.drawCircle(cx, cy, shapeRadius, shapePaint)
                canvas.drawCircle(cx, cy, shapeRadius * 0.5f, shapePaint)
            }
            PileShape.TRIANGLE_TRIANGLE -> {
                // Dessine le triangle extérieur
                val outerPath = Path()
                outerPath.moveTo(cx, cy - shapeRadius)
                outerPath.lineTo(cx + shapeRadius, cy + shapeRadius)
                outerPath.lineTo(cx - shapeRadius, cy + shapeRadius)
                outerPath.close()
                canvas.drawPath(outerPath, shapePaint)

                // CORRECTION: Dessine un triangle intérieur correctement centré
                val innerRadius = shapeRadius * 0.5f
                val innerOffsetY = shapeRadius * 0.25f // Petit décalage vers le bas pour centrer
                val innerPath = Path()
                innerPath.moveTo(cx, cy - innerRadius + innerOffsetY)
                innerPath.lineTo(cx + innerRadius, cy + innerRadius + innerOffsetY)
                innerPath.lineTo(cx - innerRadius, cy + innerRadius + innerOffsetY)
                innerPath.close()
                canvas.drawPath(innerPath, shapePaint)
            }
        }

        if (text.isNotBlank()) {
            canvas.drawText(text, cx, textY, textPaint)
        }
    }

    private fun drawPhotosSection(photoBitmapMap: Map<PhotoEntity, Bitmap>) {
        if (photoBitmapMap.isEmpty()) return

        cursorY += 20f
        checkPageBreak(30f)
        currentCanvas.drawText("Photos du Chantier", MARGIN, cursorY, titlePaint)
        cursorY += 20f

        val photoWidth = CONTENT_WIDTH
        val photoHeight = photoWidth * 0.55f

        val rowHeightNeeded = photoHeight + 35f

        photoBitmapMap.entries.forEach { (photo, bmp) ->

            checkPageBreak(rowHeightNeeded)

            val scaledBmp = scaleAndCropBitmap(bmp, photoWidth, photoHeight)
            currentCanvas.drawBitmap(scaledBmp, MARGIN, cursorY, null)
            scaledBmp.recycle()

            val name = File(photo.storagePath).nameWithoutExtension
            val titleY = cursorY + photoHeight + 10f

            currentCanvas.drawText(name, MARGIN + photoWidth / 2, titleY, photoTitlePaint)

            cursorY += rowHeightNeeded
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxHeight: Float): Bitmap {
        val ratio = maxHeight / bitmap.height
        val newWidth = bitmap.width * ratio
        return Bitmap.createScaledBitmap(bitmap, newWidth.toInt(), maxHeight.toInt(), true)
    }

    private fun scaleAndCropBitmap(source: Bitmap, targetWidth: Float, targetHeight: Float): Bitmap {
        val sourceWidth = source.width.toFloat()
        val sourceHeight = source.height.toFloat()

        val xScale = targetWidth / sourceWidth
        val yScale = targetHeight / sourceHeight
        val scale = xScale.coerceAtLeast(yScale)

        val scaledWidth = scale * sourceWidth
        val scaledHeight = scale * sourceHeight

        val left = (targetWidth - scaledWidth) / 2
        val top = (targetHeight - scaledHeight) / 2

        val targetRect = RectF(left, top, left + scaledWidth, top + scaledHeight)

        val dest = Bitmap.createBitmap(targetWidth.toInt(), targetHeight.toInt(), source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        canvas.drawBitmap(source, null, targetRect, null)
        return dest
    }

    private suspend fun downloadPhotoBitmaps(photos: List<PhotoEntity>): Map<PhotoEntity, Bitmap> = withContext(Dispatchers.IO) {
        if (photos.isEmpty()) return@withContext emptyMap()
        val storage = Firebase.storage
        val maxFileSize = 50L * 1024 * 1024 // 10 MB

        photos.map { photo ->
            async {
                try {
                    val bytes = storage.getReference(photo.storagePath).getBytes(maxFileSize).await()
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bitmap ->
                        photo to bitmap
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "ERREUR CRITIQUE: Échec téléchargement photo: ${photo.storagePath}", e)
                    null
                }
            }
        }.awaitAll().filterNotNull().toMap()
    }

    private fun getLogoBitmap(context: Context): Bitmap? {
        return try {
            BitmapFactory.decodeResource(context.resources, R.drawable.fondabec_logo)
        } catch (e: Exception) {
            Log.e(TAG, "Impossible de charger le logo", e)
            null
        }
    }

    private fun createPdfFile(context: Context, project: ProjectEntity): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val safeName = sanitizeFilePart(project.name.ifBlank { "projet_${project.id}" })
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.CANADA_FRENCH).format(Date())
        return File(dir, "Rapport_${safeName}_$stamp.pdf")
    }

    private fun getUriForFile(context: Context, file: File): Uri {
        val authority = "${context.packageName}.provider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    fun shareReportByEmail(context: Context, pdfUri: Uri, projectName: String) {
        val subject = "Rapport de projet: ${projectName.trim()}"
        val body = "Veuillez trouver ci-joint le rapport de projet au format PDF."

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Partager le rapport via...")
        if (context !is Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun sanitizeFilePart(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_.-]"), "_").take(50)
    }
}