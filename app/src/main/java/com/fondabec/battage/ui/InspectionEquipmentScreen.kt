package com.fondabec.battage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionEquipmentScreen(
    onSelectEquipment: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fiche d'inspection") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Retour") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Veuillez sélectionner un équipement à inspecter :")

            Button(onClick = { onSelectEquipment("Batteuse") }, modifier = Modifier.fillMaxWidth()) {
                Text("Batteuse")
            }

            Button(onClick = { onSelectEquipment("Excavatrice avec vibro") }, modifier = Modifier.fillMaxWidth()) {
                Text("Excavatrice avec vibro")
            }

            Button(onClick = { onSelectEquipment("Chargeur") }, modifier = Modifier.fillMaxWidth()) {
                Text("Chargeur")
            }
        }
    }
}
