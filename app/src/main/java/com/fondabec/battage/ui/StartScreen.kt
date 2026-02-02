package com.fondabec.battage.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.fondabec.battage.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(
    isDarkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
    onOpenCarnet: () -> Unit,
    onOpenMap: () -> Unit,
    onLogout: (() -> Unit)? = null
) {
    val overlay = MaterialTheme.colorScheme.background.copy(alpha = if (isDarkMode) 0.62f else 0.70f)
    val watermarkAlpha = if (isDarkMode) 0.32f else 0.22f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fondabec Battage") },
                actions = {
                    if (onLogout != null) {
                        TextButton(onClick = onLogout) {
                            Text("Déconnexion")
                        }
                    }
                }
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Image(
                painter = painterResource(id = R.drawable.fondabec_logo),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(watermarkAlpha),
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlay)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.fondabec_logo),
                    contentDescription = "Groupe Fondabec",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentScale = ContentScale.Fit
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mode sombre",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { onDarkModeChanged(it) }
                    )
                }

                Button(onClick = onOpenCarnet, modifier = Modifier.fillMaxWidth()) {
                    Text("Carnet de battage (projets)")
                }

                Button(onClick = onOpenMap, modifier = Modifier.fillMaxWidth()) {
                    Text("Carte profondeurs (bientôt)")
                }

                Spacer(Modifier.weight(1f))
            }
        }
    }
}
