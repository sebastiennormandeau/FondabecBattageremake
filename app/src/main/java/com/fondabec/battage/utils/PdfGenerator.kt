package com.fondabec.battage.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.fondabec.battage.R
import com.fondabec.battage.data.InspectionDao
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfGenerator {

    fun createInspectionPdf(context: Context, report: InspectionDao.FullInspectionReport): File? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 18f
            color = Color.BLACK
        }

        val headerPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 12f
            color = Color.BLACK
        }

        val bodyPaint = Paint().apply {
            textSize = 12f
            color = Color.BLACK
        }
        
        val nonConformePaint = Paint(bodyPaint).apply {
            color = Color.RED
        }

        // --- Logo ---
        val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.fondabec_logo)
        val logoWidth = 100
        val logoHeight = (logoBitmap.height.toFloat() / logoBitmap.width.toFloat() * logoWidth).toInt()
        canvas.drawBitmap(logoBitmap, null, Rect(40, 40, 40 + logoWidth, 40 + logoHeight), null)

        // --- Titre ---
        canvas.drawText("Rapport d'inspection", 200f, 60f, titlePaint)

        // --- Infos ---
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(report.report.dateEpochMs))
        canvas.drawText("Équipement: ${report.report.equipmentType}", 200f, 80f, headerPaint)
        canvas.drawText("Date: $dateStr", 200f, 95f, bodyPaint)
        canvas.drawText("Opérateur: ${report.report.operatorName}", 200f, 110f, bodyPaint)
        canvas.drawText("Heures machine: ${report.report.machineHours}", 200f, 125f, bodyPaint)

        // --- Points d'inspection ---
        var yPosition = 180f
        canvas.drawText("Points de vérification", 40f, yPosition, headerPaint)
        yPosition += 25f

        report.points.forEach {
            val status = when (it.status) {
                "CONFORME" -> "Conforme"
                "NON_CONFORME" -> "Non conforme"
                else -> "N/A"
            }
            val paint = if (it.status == "NON_CONFORME") nonConformePaint else bodyPaint
            canvas.drawText("• ${it.label}", 50f, yPosition, bodyPaint)
            canvas.drawText(status, 450f, yPosition, paint)
            yPosition += 20f
        }
        
        // --- Notes ---
        if (report.report.notes.isNotBlank()) {
            yPosition += 20f
            canvas.drawText("Notes:", 40f, yPosition, headerPaint)
            yPosition += 20f
            report.report.notes.split("\n").forEach {
                canvas.drawText(it, 50f, yPosition, bodyPaint)
                yPosition += 15f
            }
        }

        document.finishPage(page)

        return try {
            val sdfFile = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "Inspection_${report.report.equipmentType}_${sdfFile.format(Date())}.pdf"
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
            val fos = FileOutputStream(file)
            document.writeTo(fos)
            document.close()
            fos.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
