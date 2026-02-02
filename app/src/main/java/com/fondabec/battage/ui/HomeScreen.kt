package com.fondabec.battage.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fondabec.battage.data.ProjectSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.round

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    projects: List<ProjectSummary>,
    isDarkMode: Boolean,          // ✅ nouveau
    onBackToStart: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit
) {
    val fondabecGreen = Color(0xFFB6D400)

    val fabContainer = if (isDarkMode) Color.White else fondabecGreen
    val fabContent = Color.Black

    val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fondabec Battage") },
                navigationIcon = {
                    TextButton(onClick = onBackToStart) { Text("Accueil") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreate,
                containerColor = fabContainer,
                contentColor = fabContent,
                elevation = FloatingActionButtonDefaults.elevation()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nouveau projet")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(projects) { p ->
                val dateStr = Instant.ofEpochMilli(p.startDateEpochMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(dateFmt)

                val cityStr = if (p.city.isBlank()) "—" else p.city
                val avg = round(p.avgDepthFt * 10.0) / 10.0
                val sub = "Début: $dateStr | Ville: $cityStr | Prof. moy.: $avg ft"

                ListItem(
                    headlineContent = { Text(p.name) },
                    supportingContent = { Text(sub) },
                    modifier = Modifier
                        .clickable { onOpen(p.id) }
                        .padding(horizontal = 4.dp)
                )
            }
        }
    }
}
