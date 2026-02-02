package com.fondabec.battage.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

object ImageHelper {

    // Taille maximale souhaitée (en pixels). 1280 est parfait pour un rapport PDF.
    private const val MAX_WIDTH_HEIGHT = 1920
    // Qualité de compression (0-100). 70 offre un excellent ratio qualité/poids.
    private const val COMPRESSION_QUALITY = 90

    /**
     * Prend une Uri (photo galerie/caméra), la redimensionne et la compresse.
     * Retourne un ByteArray prêt à être envoyé à Firebase.
     */
    fun compressImage(context: Context, imageUri: Uri): ByteArray? {
        try {
            // 1. On lit juste les dimensions sans charger l'image (pour économiser la RAM)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            // 2. On calcule le facteur de réduction (inSampleSize)
            options.inSampleSize = calculateInSampleSize(options, MAX_WIDTH_HEIGHT, MAX_WIDTH_HEIGHT)
            options.inJustDecodeBounds = false

            // 3. On charge l'image redimensionnée en mémoire
            val bitmap = context.contentResolver.openInputStream(imageUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            return bitmap?.let {
                val outputStream = ByteArrayOutputStream()
                // 4. On compresse en JPEG
                it.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, outputStream)
                it.recycle() // On libère la mémoire du bitmap immédiatement
                outputStream.toByteArray()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // Calcul savant pour réduire la taille de l'image efficacement
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}