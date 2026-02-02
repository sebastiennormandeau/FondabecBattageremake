package com.fondabec.battage.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val uid: String, val email: String?) : AuthState
}

data class AuthUiState(
    val auth: AuthState = AuthState.Loading,
    val busy: Boolean = false,
    val error: String? = null
)

class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val listener = FirebaseAuth.AuthStateListener { fa ->
        val u = fa.currentUser
        _state.update {
            it.copy(
                auth = if (u == null) AuthState.SignedOut else AuthState.SignedIn(u.uid, u.email),
                busy = false,
                error = null
            )
        }
    }

    init {
        auth.addAuthStateListener(listener)
        val u = auth.currentUser
        _state.update {
            it.copy(auth = if (u == null) AuthState.SignedOut else AuthState.SignedIn(u.uid, u.email))
        }
    }

    override fun onCleared() {
        auth.removeAuthStateListener(listener)
        super.onCleared()
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun signIn(email: String, password: String) {
        val e = email.trim()
        if (e.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Email et mot de passe requis.") }
            return
        }

        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                auth.signInWithEmailAndPassword(e, password).await()
            } catch (t: Throwable) {
                _state.update { it.copy(busy = false, error = t.message ?: "Connexion impossible.") }
            }
        }
    }

    fun register(email: String, password: String) {
        val e = email.trim()
        if (e.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "Email et mot de passe requis.") }
            return
        }
        if (password.length < 6) {
            _state.update { it.copy(error = "Mot de passe trop court (min 6 caractères).") }
            return
        }

        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                auth.createUserWithEmailAndPassword(e, password).await()
            } catch (t: Throwable) {
                _state.update { it.copy(busy = false, error = t.message ?: "Création de compte impossible.") }
            }
        }
    }

    fun signOut() {
        auth.signOut()
    }
}
