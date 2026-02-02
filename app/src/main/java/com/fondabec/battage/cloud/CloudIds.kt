package com.fondabec.battage.cloud

import com.google.firebase.auth.FirebaseAuth
import java.util.UUID

object CloudIds {
    fun currentUidOrEmpty(): String = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    fun newRemoteId(): String = UUID.randomUUID().toString()
}
