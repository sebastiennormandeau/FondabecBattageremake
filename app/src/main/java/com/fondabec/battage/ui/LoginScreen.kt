package com.fondabec.battage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.fondabec.battage.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(authVm: AuthViewModel) {

    val ui by authVm.state.collectAsState()

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    // ✅ Nouveau: afficher/masquer mot de passe
    var showPassword by rememberSaveable { mutableStateOf(false) }

    val busy = ui.busy

    Scaffold(
        topBar = { TopAppBar(title = { Text("Connexion") }) }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Fondabec Battage — Accès sécurisé",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    if (ui.error != null) authVm.clearError()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                singleLine = true,
                enabled = !busy
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    if (ui.error != null) authVm.clearError()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Mot de passe") },
                singleLine = true,
                enabled = !busy,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(
                        onClick = { showPassword = !showPassword },
                        enabled = !busy
                    ) {
                        Icon(
                            imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showPassword) "Masquer le mot de passe" else "Afficher le mot de passe"
                        )
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            if (ui.error != null) {
                Text(
                    text = ui.error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = { authVm.signIn(email, password) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) {
                if (busy) CircularProgressIndicator() else Text("Se connecter")
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = { authVm.register(email, password) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) {
                Text("Créer un compte")
            }
        }
    }
}
