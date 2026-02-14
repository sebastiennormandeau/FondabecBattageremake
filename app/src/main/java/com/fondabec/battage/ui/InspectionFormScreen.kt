package com.fondabec.battage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fondabec.battage.data.ProjectSummary

enum class InspectionStatus { CONFORME, NON_CONFORME, ND }

// Make status a val for immutability
data class InspectionPoint(val label: String, val status: InspectionStatus)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionFormScreen(
    equipmentType: String,
    projects: List<ProjectSummary>,
    onSave: (projectId: Long, equipmentType: String, operatorName: String, machineHours: Int, notes: String, points: List<InspectionPoint>) -> Unit,
    onBack: () -> Unit,
    onExport: () -> Unit
) {
    var selectedProject by remember { mutableStateOf<ProjectSummary?>(null) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    var operatorName by remember { mutableStateOf("") }
    var machineHours by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // Hold the list in a state variable
    var inspectionPoints by remember {
        val points = when (equipmentType) {
            "Batteuse" -> listOf(
                "Niveau d'huile moteur", "Système hydraulique", "Filtres (air, huile, carburant)",
                "État des chenilles/pneus", "Graissage des composantes", "Batterie et système électrique",
                "Marteau et tête de battage", "Cabine de l'opérateur", "Extincteur et trousse de secours"
            )
            "Excavatrice avec vibro" -> listOf(
                "Niveau d'huile moteur", "Système hydraulique", "Filtres",
                "État des chenilles", "Graissage", "Attaches rapides et godet",
                "Vibrofonceur (boyaux, pinces)", "Cabine et contrôles", "Sécurité (klaxon, alarme de recul)"
            )
            else -> listOf(
                "Niveau d'huile moteur", "Transmission", "Pneus et freins", "Système de levage",
                "Graissage", "Lumières et klaxon", "Cabine", "Équipement de sécurité"
            )
        }
        mutableStateOf(points.map { InspectionPoint(it, InspectionStatus.CONFORME) })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(equipmentType) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Retour") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = isDropdownExpanded,
                onExpandedChange = { isDropdownExpanded = !isDropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = selectedProject?.name ?: "Sélectionner un projet",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    label = { Text("Projet associé") }
                )
                ExposedDropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false })
                {
                    projects.forEach { project ->
                        DropdownMenuItem(
                            text = { Text(project.name) },
                            onClick = {
                                selectedProject = project
                                isDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = operatorName, onValueChange = { operatorName = it }, label = { Text("Opérateur") }, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = machineHours,
                    onValueChange = { machineHours = it.filter(Char::isDigit) },
                    label = { Text("Heures") },
                    modifier = Modifier.weight(0.5f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(inspectionPoints.size) { index ->
                    val point = inspectionPoints[index]
                    
                    fun updateStatus(newStatus: InspectionStatus) {
                        inspectionPoints = inspectionPoints.mapIndexed { i, p ->
                            if (i == index) p.copy(status = newStatus) else p
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(point.label, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Checkbox(checked = point.status == InspectionStatus.CONFORME, onCheckedChange = { if (it) updateStatus(InspectionStatus.CONFORME) })
                        Text("C")
                        Spacer(Modifier.width(4.dp))
                        Checkbox(checked = point.status == InspectionStatus.NON_CONFORME, onCheckedChange = { if (it) updateStatus(InspectionStatus.NON_CONFORME) })
                        Text("NC")
                        Spacer(Modifier.width(4.dp))
                        Checkbox(checked = point.status == InspectionStatus.ND, onCheckedChange = { if (it) updateStatus(InspectionStatus.ND) })
                        Text("N/D")
                    }
                }
            }

            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes (optionnel)") }, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        selectedProject?.id?.let {
                            onSave(it, equipmentType, operatorName, machineHours.toIntOrNull() ?: 0, notes, inspectionPoints)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = selectedProject != null
                ) {
                    Text("Enregistrer")
                }
                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Exporter")
                }
            }
        }
    }
}