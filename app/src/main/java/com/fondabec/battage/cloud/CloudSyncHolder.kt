package com.fondabec.battage.cloud

import android.content.Context
import com.fondabec.battage.data.AppDatabase
import com.google.firebase.auth.FirebaseAuth

object CloudSyncHolder {

    @Volatile private var service: CloudSyncService? = null

    /**
     * Initialise le CloudSyncService (singleton).
     * Important: on ne le recrée pas à chaque Activity.
     */
    fun init(context: Context, db: AppDatabase) {
        if (service != null) return
        synchronized(this) {
            if (service == null) {
                service = CloudSyncService(context.applicationContext, db)
            }
        }
    }

    /**
     * Démarre la logique de sync:
     * - écoute Auth
     * - démarre/arrête les listeners Firestore selon user connecté
     */
    fun start() {
        service?.start()
    }

    fun sync(): CloudSyncService? = service

    /**
     * Gate \"écriture\" côté app:
     * - admin -> OK
     * - ownerUid blank (legacy) -> OK
     * - ownerUid == current uid -> OK
     */
    fun canWrite(ownerUid: String): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val isAdmin = service?.isAdminCached() == true
        return isAdmin || ownerUid.isBlank() || ownerUid == uid
    }
}
