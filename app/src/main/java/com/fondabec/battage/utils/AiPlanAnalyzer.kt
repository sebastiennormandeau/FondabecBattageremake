package com.fondabec.battage.utils

import android.graphics.Bitmap
import com.google.firebase.Firebase
import com.google.firebase.vertexai.type.content
import com.google.firebase.vertexai.type.generationConfig
import com.google.firebase.vertexai.vertexAI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import org.json.JSONObject

data class AiPieu(
    val id_pieu: String,
    val calibre: String?,
    val classement_forme: String?,
    val coord_x: Float,
    val coord_y: Float
)

object AiPlanAnalyzer {

    suspend fun analyzePlanForPilesWithVertexAI(bitmap: Bitmap, pdfText: String?): List<AiPieu>? = withContext(Dispatchers.IO) {
        val generativeModel = Firebase.vertexAI.generativeModel(
            modelName = "gemini-2.0-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                temperature = 0.0f
                maxOutputTokens = 8192
            },
            systemInstruction = content {
                text("""
                    Tu es un ingénieur expert en fondations. Ton objectif est d'extraire la position de TOUS les pieux visibles sur le plan (l'IMAGE fournie).
                    Voici la représentation visuelle typique des pieux:
                    1. Sur le plan, l'emplacement physique exact d'un pieu est indiqué par un petit POINT NOIR (solid black dot).
                    2. À côté de (ou par-dessus) ce point noir, il y a une FORME GÉOMÉTRIQUE (ex: hexagone, cercle, carré, triangle).
                    3. À L'INTÉRIEUR de cette forme se trouve un NUMÉRO (ex: 1, 2, 17, etc.). Ce numéro est le 'id_pieu'.
                    
                    Je te fournis également le TEXTE COMPLET extrait du plan. Ce texte contient souvent des Tableaux associant les formes ou les numéros à des calibres (ex: HSS 5.5, HSS 7) et capacités portantes.
                    
                    TA MISSION:
                    Examine l'image attentivement. Pour CHAQUE pieu trouvé (point noir + forme + numéro), renvoie un objet JSON dans un tableau nommé 'pieux'.
                    Chaque objet doit contenir :
                    1) 'id_pieu' (le numéro exact à l'intérieur de la forme).
                    2) 'calibre' (le diamètre, si déductible du texte grâce au numéro ou à la forme, sinon vide).
                    3) 'classement_forme' (la forme géométrique dessinée: hexagone, cercle, etc.).
                    4) 'coord_x' et 5) 'coord_y' 
                    
                    CRUCIAL POUR LES COORDONNÉES:
                    'coord_x' et 'coord_y' doivent cibler le centre exact du POINT NOIR associé à la forme. Utilise une échelle normalisée de 0 à 1000 (0,0 en haut à gauche; 1000,1000 en bas à droite de l'image).
                    Fais un balayage méticuleux de toute l'image. Retourne UNIQUEMENT du JSON valide.
                """.trimIndent())
            }
        )

        Log.d("AiPlan", "Démarrage de l'analyse Vertex AI...")
        val response = generativeModel.generateContent(
            content {
                if (!pdfText.isNullOrBlank()) {
                    text("Contexte texte du PDF:\n$pdfText")
                }
                image(bitmap)
                text("Extraire les pieux de l'image.")
            }
        )
        
        val jsonString = response.text ?: run {
            Log.e("AiPlan", "Réponse de Gemini vide")
            throw Exception("Réponse de l'IA vide")
        }
        Log.d("AiPlan", "Réponse Gemini brute reçue: ${jsonString.take(200)}...")
        
        // Nettoyage de la chaîne JSON (enlève les balises Markdown si présentes)
        var cleanJson = jsonString.trim()
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.removePrefix("```json")
        } else if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.removePrefix("```")
        }
        if (cleanJson.endsWith("```")) {
            cleanJson = cleanJson.removeSuffix("```")
        }
        cleanJson = cleanJson.trim()
        
        val jsonObject = JSONObject(cleanJson)
        val pieuxArray = jsonObject.getJSONArray("pieux")
        val list = mutableListOf<AiPieu>()
        
        for (i in 0 until pieuxArray.length()) {
            val p = pieuxArray.getJSONObject(i)
            list.add(AiPieu(
                id_pieu = p.optString("id_pieu", ""),
                calibre = p.optString("calibre", null),
                classement_forme = p.optString("classement_forme", null),
                coord_x = p.optDouble("coord_x", 0.0).toFloat(),
                coord_y = p.optDouble("coord_y", 0.0).toFloat()
            ))
        }
        Log.d("AiPlan", "Analyse terminée: ${list.size} pieux extraits")
        list
    }

    /**
     * Convertit les coordonnées brutes retournées par Gemini (ex: 0 à 1000) 
     * en coordonnées absolues normalisées (0.0f à 1.0f) compatibles avec votre vue Android.
     */
    fun convertAiCoordToNorm(aiX: Float, aiY: Float, isGrid1000: Boolean = true): Pair<Float, Float> {
        return if (isGrid1000) {
            val xNorm = (aiX / 1000f).coerceIn(0f, 1f)
            val yNorm = (aiY / 1000f).coerceIn(0f, 1f)
            Pair(xNorm, yNorm)
        } else {
            Pair(aiX.coerceIn(0f, 1f), aiY.coerceIn(0f, 1f))
        }
    }
}
