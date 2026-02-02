package com.fondabec.battage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fondabec.battage.data.PileEntity
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PileDetailScreen(
    projectId: Long,
    pileId: Long,
    observePile: () -> Flow<PileEntity?>,
    onBack: () -> Unit,
    onSave: (pileNo: String, gaugeIn: String, depthFt: Double, implanted: Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val pile by observePile().collectAsStateWithLifecycle(initialValue = null)

    val gauges = listOf("4 1/2", "5 1/2", "7", "9 5/8")

    var initialized by remember(pileId) { mutableStateOf(false) }

    var pileNoInput by remember(pileId) { mutableStateOf("") }
    var gaugeIn by remember(pileId) { mutableStateOf("") }
    var implanted by remember(pileId) { mutableStateOf(false) }
    var depthField by remember(pileId) { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(pile) {
        val p = pile ?: return@LaunchedEffect
        if (!initialized) {
            pileNoInput = p.pileNo
            gaugeIn = p.gaugeIn
            implanted = p.implanted
            depthField = if (p.depthFt == 0.0) TextFieldValue("") else TextFieldValue(p.depthFt.toString())
            initialized = true
        }
    }

    var showGaugeDialog by remember { mutableStateOf(false) }

    val depthFt = depthField.text.replace(',', '.').toDoubleOrNull() ?: 0.0

    if (showGaugeDialog) {
        AlertDialog(
            onDismissRequest = { showGaugeDialog = false },
            title = { Text("Choisir un calibre") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(gauges.size) { idx ->
                        val g = gauges[idx]
                        OutlinedButton(
                            onClick = {
                                gaugeIn = g
                                showGaugeDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(g) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showGaugeDialog = false }) { Text("Annuler") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fiche pieu") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Retour") } },
                actions = { TextButton(onClick = onDelete) { Text("Supprimer") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = pileNoInput,
                    onValueChange = { pileNoInput = it },
                    label = { Text("No sur plan (ex: 3) — vide = auto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { Text("Calibre (pouces)") }

            item {
                OutlinedButton(
                    onClick = { showGaugeDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (gaugeIn.isBlank()) "Choisir…" else gaugeIn)
                        Text("▼")
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = depthField,
                    onValueChange = { v ->
                        val clean = v.text.filter { it.isDigit() || it == '.' || it == ',' }
                        depthField = TextFieldValue(
                            text = clean,
                            selection = TextRange(clean.length)
                        )
                    },
                    label = { Text("Profondeur (ft) ex: 23.5") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }

            item { Text("Implanté") }

            item {
                TextButton(onClick = { implanted = !implanted }, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = implanted, onCheckedChange = { implanted = it })
                    Text(if (implanted) " Oui" else " Non")
                }
            }

            item {
                Button(
                    onClick = { onSave(pileNoInput, gaugeIn, depthFt, implanted) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Enregistrer") }
            }
        }
    }
}
