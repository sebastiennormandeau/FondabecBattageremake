package com.fondabec.battage.report

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fondabec.battage.data.PileEntity
import com.fondabec.battage.data.ProjectEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val COLOR_PRIMARY_TEXT = Color(0xFF212121)
private val COLOR_SECONDARY_TEXT = Color(0xFF757575)
private val COLOR_DIVIDER = Color(0xFFBDBDBD)
private val COLOR_TABLE_HEADER_BG = Color(0xFFF5F5F5)
private val COLOR_TABLE_ZEBRA = Color(0xFFFAFAFA)

@Composable
fun ProjectReport(project: ProjectEntity, piles: List<PileEntity>, photoBitmaps: List<Bitmap>, logo: Bitmap?) {
    val dateFmt = SimpleDateFormat("d MMMM yyyy", Locale.CANADA_FRENCH)
    val dateTimeFmt = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.CANADA_FRENCH)
    val startDate = dateFmt.format(Date(project.startDateEpochMs))
    val generatedAt = dateTimeFmt.format(Date())

    val total = piles.size
    val implanted = piles.count { it.implanted }
    val avgDepth = if (piles.isEmpty()) 0.0 else piles.map { it.depthFt }.average()
    val avgDepth1 = String.format(Locale.CANADA, "%.1f", avgDepth)

    Column(modifier = Modifier.background(Color.White).padding(24.dp).fillMaxWidth()) {
        ReportHeader(logo, generatedAt)
        Spacer(Modifier.height(30.dp))

        Text(project.name.trim().ifBlank { "Projet #${project.id}" }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = COLOR_PRIMARY_TEXT)
        Spacer(Modifier.height(8.dp))
        Text("Début: $startDate   |   Ville: ${project.city.trim().ifBlank { "—" }}", fontSize = 10.sp, color = COLOR_SECONDARY_TEXT)
        Spacer(Modifier.height(4.dp))
        Text("Total: $total pieux   |   Implantés: $implanted   |   Profondeur moyenne: $avgDepth1 ft", fontSize = 10.sp, color = COLOR_SECONDARY_TEXT)

        Spacer(Modifier.height(24.dp))

        PileTable(piles)

        Spacer(Modifier.height(24.dp))

        if (photoBitmaps.isNotEmpty()) {
            PhotoSection(photoBitmaps)
        }
    }
}

@Composable
private fun ReportHeader(logo: Bitmap?, generatedAt: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        logo?.let {
            Image(bitmap = it.asImageBitmap(), contentDescription = "Logo", modifier = Modifier.height(50.dp), contentScale = ContentScale.Fit)
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text("Rapport de Projet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = COLOR_PRIMARY_TEXT, textAlign = TextAlign.Center)
            Text(generatedAt, fontSize = 10.sp, color = COLOR_SECONDARY_TEXT)
        }
    }
    Spacer(Modifier.height(20.dp))
    Divider(color = COLOR_DIVIDER)
}

@Composable
private fun PileTable(piles: List<PileEntity>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(COLOR_TABLE_HEADER_BG, shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text("PIEU", modifier = Modifier.width(150.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = COLOR_PRIMARY_TEXT)
            Text("CALIBRE", modifier = Modifier.width(100.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = COLOR_PRIMARY_TEXT)
            Text("PROFONDEUR (FT)", modifier = Modifier.weight(1f), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = COLOR_PRIMARY_TEXT)
        }

        if (piles.isEmpty()) {
            Text("Aucun pieu pour ce projet.", modifier = Modifier.padding(16.dp), fontSize = 10.sp, color = COLOR_SECONDARY_TEXT)
        } else {
            piles.forEachIndexed { index, pile ->
                val bgColor = if (index % 2 == 1) COLOR_TABLE_ZEBRA else Color.Transparent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val no = pile.pileNo.trim().ifBlank { "(auto)" }
                    val g = pile.gaugeIn.trim().ifBlank { "—" }
                    val d = if (pile.depthFt == 0.0) "—" else String.format(Locale.CANADA, "%.2f", pile.depthFt)

                    Text(no, modifier = Modifier.width(150.dp), fontSize = 9.sp, color = COLOR_PRIMARY_TEXT)
                    Text(g, modifier = Modifier.width(100.dp), fontSize = 9.sp, color = COLOR_PRIMARY_TEXT)
                    Text(d, modifier = Modifier.weight(1f), fontSize = 9.sp, color = COLOR_PRIMARY_TEXT)
                }
            }
        }
    }
}

@Composable
private fun PhotoSection(bitmaps: List<Bitmap>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Photos", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = COLOR_PRIMARY_TEXT)
        Spacer(Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            bitmaps.forEach { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Photo de projet",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}
