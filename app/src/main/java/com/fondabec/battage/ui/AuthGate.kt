package com.fondabec.battage.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fondabec.battage.auth.AuthState
import com.fondabec.battage.auth.AuthViewModel

@Composable
fun AuthGate(
    authVm: AuthViewModel,
    content: @Composable () -> Unit
) {
    val st by authVm.state.collectAsState()

    when (st.auth) {
        is AuthState.SignedIn -> content()
        AuthState.SignedOut -> LoginScreen(authVm = authVm)
        AuthState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
    }
}
