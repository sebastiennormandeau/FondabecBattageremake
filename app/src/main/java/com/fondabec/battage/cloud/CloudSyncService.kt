package com.fondabec.battage.cloud

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.fondabec.battage.data.AppDatabase
import com.fondabec.battage.data.MapPointEntity
import com.fondabec.battage.data.PhotoEntity
import com.fondabec.battage.data.PileEntity
import com.fondabec.battage.data.PileHotspotEntity
import com.fondabec.battage.data.ProjectEntity
import com.fondabec.battage.utils.ImageHelper // Assurez-vous que l'import correspond à votre package
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CloudSyncService(
    private val context: Context,
    private val db: AppDatabase
) {
    private val tag = "CloudSync"
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var authListener: FirebaseAuth.AuthStateListener? = null

    private var projectsListener: ListenerRegistration? = null
    private var mapPointsListener: ListenerRegistration? = null
    private var adminListener: ListenerRegistration? = null

    // projectRemoteId -> (pilesListener, hotspotsListener, photosListener)
    private val subListeners = mutableMapOf<String, Triple<ListenerRegistration, ListenerRegistration, ListenerRegistration>>()

    @Volatile private var isAdmin: Boolean = false
    fun isAdminCached(): Boolean = isAdmin

    fun start() {
        if (authListener != null) return

        Log.d(tag, "start() - attach auth listener")

        authListener = FirebaseAuth.AuthStateListener { fb ->
            val user = fb.currentUser
            if (user == null) {
                Log.d(tag, "Auth: signed out -> stop listeners")
                stopFirestoreListeners()
            } else {
                Log.d(tag, "Auth: signed in uid=${user.uid} -> start listeners")
                startFirestoreListeners(user.uid)
            }
        }
        auth.addAuthStateListener(authListener!!)

        auth.currentUser?.uid?.let { startFirestoreListeners(it) }
    }

    private fun startFirestoreListeners(uid: String) {
        stopFirestoreListeners()

        Log.d(tag, "startFirestoreListeners(uid=$uid)")

        // --- Admin flag (si doc admins/{uid} existe et enabled=true)
        adminListener = firestore.collection("admins").document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(tag, "adminListener error", err)
                    isAdmin = false
                    return@addSnapshotListener
                }
                val enabled = snap?.getBoolean("enabled") == true
                val newAdmin = (snap != null && snap.exists() && enabled)
                if (newAdmin != isAdmin) {
                    isAdmin = newAdmin
                    Log.d(tag, "Admin cached changed -> $isAdmin (enabled=${snap?.exists() == true})")
                } else {
                    isAdmin = newAdmin
                }
            }

        // --- Projects (globaux; la sécurité est dans les rules + canWrite côté app)
        projectsListener = firestore.collection("projects")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(tag, "projectsListener error", err)
                    return@addSnapshotListener
                }
                val s = snap ?: return@addSnapshotListener
                for (change in s.documentChanges) {
                    val doc = change.document
                    val remoteId = doc.id
                    when (change.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED -> {
                            scope.launch { applyProjectFromRemote(remoteId, doc.data) }
                            ensureProjectSubListeners(remoteId)
                        }
                        DocumentChange.Type.REMOVED -> {
                            scope.launch { removeProjectByRemoteId(remoteId) }
                            removeProjectSubListeners(remoteId)
                        }
                    }
                }
            }

        // --- Map points (global)
        mapPointsListener = firestore.collection("map_points")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(tag, "mapPointsListener error", err)
                    return@addSnapshotListener
                }
                val s = snap ?: return@addSnapshotListener
                for (change in s.documentChanges) {
                    val doc = change.document
                    val remoteId = doc.id
                    when (change.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED -> scope.launch {
                            applyMapPointFromRemote(remoteId, doc.data)
                        }
                        DocumentChange.Type.REMOVED -> scope.launch {
                            db.mapPointDao().deleteByRemoteId(remoteId)
                        }
                    }
                }
            }
    }

    private fun stopFirestoreListeners() {
        projectsListener?.remove(); projectsListener = null
        mapPointsListener?.remove(); mapPointsListener = null
        adminListener?.remove(); adminListener = null
        isAdmin = false

        for ((_, triple) in subListeners) {
            triple.first.remove()
            triple.second.remove()
            triple.third.remove()
        }
        subListeners.clear()
    }

    private fun ensureProjectSubListeners(projectRemoteId: String) {
        if (subListeners.containsKey(projectRemoteId)) return

        val pilesReg = firestore.collection("projects").document(projectRemoteId)
            .collection("piles")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(tag, "pilesListener($projectRemoteId) error", err)
                    return@addSnapshotListener
                }
                val s = snap ?: return@addSnapshotListener
                for (change in s.documentChanges) {
                    val doc = change.document
                    val remotePileId = doc.id
                    when (change.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED -> scope.launch {
                            applyPileFromRemote(projectRemoteId, remotePileId, doc.data)
                        }
                        DocumentChange.Type.REMOVED -> scope.launch {
                            val localProjectId =
                                db.projectDao().getLocalIdByRemoteId(projectRemoteId) ?: return@launch
                            db.pileDao().deleteByRemoteId(localProjectId, remotePileId)
                        }
                    }
                }
            }

        val hotspotsReg = firestore.collection("projects").document(projectRemoteId)
            .collection("hotspots")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(tag, "hotspotsListener($projectRemoteId) error", err)
                    return@addSnapshotListener
                }
                val s = snap ?: return@addSnapshotListener
                for (change in s.documentChanges) {
                    val doc = change.document
                    val remoteHotspotId = doc.id
                    when (change.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED -> scope.launch {
                            applyHotspotFromRemote(projectRemoteId, remoteHotspotId, doc.data)
                        }
                        DocumentChange.Type.REMOVED -> scope.launch {
                            val localProjectId =
                                db.projectDao().getLocalIdByRemoteId(projectRemoteId) ?: return@launch
                            db.pileHotspotDao().deleteByRemoteId(localProjectId, remoteHotspotId)
                        }
                    }
                }
            }

        val photosReg = firestore.collection("projects").document(projectRemoteId)
            .collection("photos")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(tag, "photosListener($projectRemoteId) error", err)
                    return@addSnapshotListener
                }
                val s = snap ?: return@addSnapshotListener
                for (change in s.documentChanges) {
                    val doc = change.document
                    val remotePhotoId = doc.id
                    when (change.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED -> scope.launch {
                            applyPhotoFromRemote(projectRemoteId, remotePhotoId, doc.data)
                        }
                        DocumentChange.Type.REMOVED -> scope.launch {
                            val localProjectId =
                                db.projectDao().getLocalIdByRemoteId(projectRemoteId) ?: return@launch
                            db.photoDao().deleteByRemoteId(localProjectId, remotePhotoId)
                        }
                    }
                }
            }

        subListeners[projectRemoteId] = Triple(pilesReg, hotspotsReg, photosReg)
    }

    private fun removeProjectSubListeners(projectRemoteId: String) {
        val triple = subListeners.remove(projectRemoteId) ?: return
        triple.first.remove()
        triple.second.remove()
        triple.third.remove()
    }

    // -------------------------
    // APPLY REMOTE -> ROOM
    // -------------------------

    private suspend fun applyProjectFromRemote(remoteId: String, data: Map<String, Any?>) {
        val ownerUid = data.str("ownerUid")
        val name = data.str("name")
        val planPdfPath = data.str("planPdfPath")
        val street = data.str("street")
        val city = data.str("city")
        val province = data.str("province")
        val postalCode = data.str("postalCode")
        val country = data.str("country")
        val latitude = data.double("latitude")
        val longitude = data.double("longitude")

        val startDate = data.long("startDateEpochMs")
        val createdAt = data.long("createdAtEpochMs")
        val updatedAt = data.long("updatedAtEpochMs")

        val projectDao = db.projectDao()
        val localId = projectDao.getLocalIdByRemoteId(remoteId)

        if (localId == null) {
            val now = System.currentTimeMillis()
            projectDao.insert(
                ProjectEntity(
                    name = name.ifBlank { "Projet" },
                    planPdfPath = planPdfPath,
                    city = city,
                    street = street,
                    province = province,
                    postalCode = postalCode,
                    country = country,
                    latitude = latitude,
                    longitude = longitude,
                    startDateEpochMs = if (startDate == 0L) now else startDate,
                    createdAtEpochMs = if (createdAt == 0L) now else createdAt,
                    updatedAtEpochMs = if (updatedAt == 0L) now else updatedAt,
                    remoteId = remoteId,
                    ownerUid = ownerUid
                )
            )
        } else {
            val local = projectDao.getById(localId) ?: return
            if (updatedAt != 0L && local.updatedAtEpochMs > updatedAt) return

            projectDao.updateFromRemote(
                projectId = localId,
                name = name.ifBlank { local.name },
                planPdfPath = planPdfPath,
                street = street,
                city = city,
                province = province,
                postalCode = postalCode,
                country = country,
                latitude = latitude,
                longitude = longitude,
                startDateEpochMs = if (startDate == 0L) local.startDateEpochMs else startDate,
                createdAtEpochMs = if (createdAt == 0L) local.createdAtEpochMs else createdAt,
                updatedAtEpochMs = if (updatedAt == 0L) local.updatedAtEpochMs else updatedAt,
                remoteId = remoteId,
                ownerUid = ownerUid
            )
        }
    }

    private suspend fun removeProjectByRemoteId(remoteId: String) {
        val projectDao = db.projectDao()
        val localId = projectDao.getLocalIdByRemoteId(remoteId) ?: return
        projectDao.deleteById(localId)
    }

    private suspend fun applyPileFromRemote(projectRemoteId: String, pileRemoteId: String, data: Map<String, Any?>) {
        val projectId = db.projectDao().getLocalIdByRemoteId(projectRemoteId) ?: return

        val ownerUid = data.str("ownerUid")
        val pileNo = data.str("pileNo")
        val gaugeIn = data.str("gaugeIn")
        val depthFt = data.double("depthFt")
        val implanted = data.bool("implanted")
        val createdAt = data.long("createdAtEpochMs")
        val updatedAt = data.long("updatedAtEpochMs")

        val pileDao = db.pileDao()
        val localId = pileDao.getLocalIdByRemoteId(projectId, pileRemoteId)

        if (localId == null) {
            pileDao.insert(
                PileEntity(
                    projectId = projectId,
                    pileNo = pileNo,
                    gaugeIn = gaugeIn,
                    depthFt = depthFt,
                    implanted = implanted,
                    createdAtEpochMs = createdAt,
                    updatedAtEpochMs = updatedAt,
                    remoteId = pileRemoteId,
                    ownerUid = ownerUid
                )
            )
        } else {
            val local = pileDao.getById(localId) ?: return
            if (updatedAt != 0L && local.updatedAtEpochMs > updatedAt) return

            pileDao.updateFromRemote(
                pileId = localId,
                pileNo = pileNo,
                gaugeIn = gaugeIn,
                depthFt = depthFt,
                implanted = implanted,
                createdAtEpochMs = if (createdAt == 0L) local.createdAtEpochMs else createdAt,
                updatedAtEpochMs = if (updatedAt == 0L) local.updatedAtEpochMs else updatedAt,
                remoteId = pileRemoteId,
                ownerUid = ownerUid
            )
        }
    }

    private suspend fun applyHotspotFromRemote(projectRemoteId: String, hotspotRemoteId: String, data: Map<String, Any?>) {
        val projectId = db.projectDao().getLocalIdByRemoteId(projectRemoteId) ?: return

        val ownerUid = data.str("ownerUid")
        val pageIndex = data.long("pageIndex").toInt()
        val xNorm = data.float("xNorm")
        val yNorm = data.float("yNorm")
        val createdAt = data.long("createdAtEpochMs")
        val updatedAt = data.long("updatedAtEpochMs")
        val pileRemoteId = data.str("pileRemoteId").ifBlank { null }

        val pileId = if (pileRemoteId != null) {
            db.pileDao().getLocalIdByRemoteId(projectId, pileRemoteId)
        } else null

        val dao = db.pileHotspotDao()
        val localId = dao.getLocalIdByRemoteId(projectId, hotspotRemoteId)

        if (localId == null) {
            dao.insert(
                PileHotspotEntity(
                    projectId = projectId,
                    pageIndex = pageIndex,
                    pileId = pileId,
                    xNorm = xNorm,
                    yNorm = yNorm,
                    createdAtEpochMs = createdAt,
                    updatedAtEpochMs = updatedAt,
                    remoteId = hotspotRemoteId,
                    ownerUid = ownerUid,
                    pileRemoteId = pileRemoteId
                )
            )
        } else {
            val local = dao.getById(localId) ?: return
            if (updatedAt != 0L && local.updatedAtEpochMs > updatedAt) return

            dao.updateFromRemote(
                hotspotId = localId,
                pageIndex = pageIndex,
                pileId = pileId ?: local.pileId,
                xNorm = xNorm,
                yNorm = yNorm,
                createdAtEpochMs = if (createdAt == 0L) local.createdAtEpochMs else createdAt,
                updatedAtEpochMs = if (updatedAt == 0L) local.updatedAtEpochMs else updatedAt,
                remoteId = hotspotRemoteId,
                ownerUid = ownerUid,
                pileRemoteId = pileRemoteId
            )
        }
    }

    private suspend fun applyPhotoFromRemote(projectRemoteId: String, photoRemoteId: String, data: Map<String, Any?>) {
        val projectId = db.projectDao().getLocalIdByRemoteId(projectRemoteId) ?: return

        val ownerUid = data.str("ownerUid")
        val storagePath = data.str("storagePath")
        val includeInReport = data.bool("includeInReport")
        val createdAt = data.long("createdAtEpochMs")
        val updatedAt = data.long("updatedAtEpochMs")

        val photoDao = db.photoDao()
        val localId = photoDao.getLocalIdByRemoteId(projectId, photoRemoteId)

        if (localId == null) {
            photoDao.insert(
                PhotoEntity(
                    projectId = projectId,
                    storagePath = storagePath,
                    includeInReport = includeInReport,
                    createdAtEpochMs = createdAt,
                    updatedAtEpochMs = updatedAt,
                    remoteId = photoRemoteId,
                    ownerUid = ownerUid
                )
            )
        } else {
            // For photos, we can just replace the local data with the remote data
            photoDao.insert(
                PhotoEntity(
                    id = localId,
                    projectId = projectId,
                    storagePath = storagePath,
                    includeInReport = includeInReport,
                    createdAtEpochMs = createdAt,
                    updatedAtEpochMs = updatedAt,
                    remoteId = photoRemoteId,
                    ownerUid = ownerUid
                )
            )
        }
    }

    private suspend fun applyMapPointFromRemote(remoteId: String, data: Map<String, Any?>) {
        val ownerUid = data.str("ownerUid")
        val name = data.str("name")
        val addressLine = data.str("addressLine")
        val lat = data.double("latitude")
        val lng = data.double("longitude")
        val avg = data.double("avgDepthFt")
        val createdAt = data.long("createdAtEpochMs")
        val updatedAt = data.long("updatedAtEpochMs")

        val dao = db.mapPointDao()
        val localId = dao.getLocalIdByRemoteId(remoteId)
        if (localId == null) {
            dao.insert(
                MapPointEntity(
                    name = name,
                    addressLine = addressLine,
                    latitude = lat,
                    longitude = lng,
                    avgDepthFt = avg,
                    createdAtEpochMs = createdAt,
                    updatedAtEpochMs = updatedAt,
                    remoteId = remoteId,
                    ownerUid = ownerUid
                )
            )
        } else {
            dao.updateFromRemote(
                id = localId,
                name = name,
                addressLine = addressLine,
                latitude = lat,
                longitude = lng,
                avgDepthFt = avg,
                createdAtEpochMs = createdAt,
                updatedAtEpochMs = updatedAt,
                remoteId = remoteId,
                ownerUid = ownerUid
            )
        }
    }

    // -------------------------
    // PUSH ROOM -> REMOTE
    // -------------------------

    fun pushProject(projectId: Long) {
        scope.launch {
            try {
                val p = db.projectDao().getById(projectId) ?: return@launch
                if (p.remoteId.isBlank()) return@launch

                val payload = hashMapOf<String, Any?>(
                    "ownerUid" to p.ownerUid,
                    "name" to p.name,
                    "planPdfPath" to p.planPdfPath,
                    "street" to p.street,
                    "city" to p.city,
                    "province" to p.province,
                    "postalCode" to p.postalCode,
                    "country" to p.country,
                    "latitude" to p.latitude,
                    "longitude" to p.longitude,
                    "startDateEpochMs" to p.startDateEpochMs,
                    "createdAtEpochMs" to p.createdAtEpochMs,
                    "updatedAtEpochMs" to p.updatedAtEpochMs
                )

                firestore.collection("projects")
                    .document(p.remoteId)
                    .set(payload, SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.w(tag, "pushProject failed", e)
            }
        }
    }

    fun uploadPlanPdfAndSync(projectId: Long, planPdfUri: String) {
        scope.launch {
            try {
                val project = db.projectDao().getById(projectId) ?: return@launch
                val user = auth.currentUser ?: return@launch
                if (project.remoteId.isBlank()) {
                    Log.w(tag, "Cannot upload plan, project is not synced yet (no remoteId)")
                    return@launch
                }

                // 1. Upload
                val storagePath = "plans/${user.uid}/${project.remoteId}.pdf"
                val storageRef = storage.reference.child(storagePath)
                storageRef.putFile(planPdfUri.toUri()).await()

                // 2. Update local DB
                val now = System.currentTimeMillis()
                db.projectDao().setPlanPdfPath(projectId, storagePath, now)

                // 3. Push project to sync path
                pushProject(projectId)
                Log.d(tag, "Plan PDF for project $projectId uploaded to $storagePath and synced")

            } catch(e: Exception) {
                Log.e(tag, "uploadPlanPdfAndSync failed for projectId=$projectId", e)
            }
        }
    }

    fun removePlanPdf(projectId: Long) {
        scope.launch {
            try {
                val project = db.projectDao().getById(projectId) ?: return@launch
                if (project.planPdfPath.isNotBlank()) {
                    storage.reference.child(project.planPdfPath).delete().await()
                }

                // Update local DB
                val now = System.currentTimeMillis()
                db.projectDao().setPlanPdfPath(projectId, "", now)

                // Push project to sync path
                pushProject(projectId)
                Log.d(tag, "Plan PDF for project $projectId removed and synced")
            } catch (e: Exception) {
                Log.e(tag, "removePlanPdf failed for projectId=$projectId", e)
            }
        }
    }

    fun pushPile(pileId: Long) {
        scope.launch {
            try {
                val pile = db.pileDao().getById(pileId) ?: return@launch
                if (pile.remoteId.isBlank()) return@launch

                val project = db.projectDao().getById(pile.projectId) ?: return@launch
                if (project.remoteId.isBlank()) return@launch

                val payload = hashMapOf<String, Any?>(
                    "ownerUid" to pile.ownerUid,
                    "pileNo" to pile.pileNo,
                    "gaugeIn" to pile.gaugeIn,
                    "depthFt" to pile.depthFt,
                    "implanted" to pile.implanted,
                    "createdAtEpochMs" to pile.createdAtEpochMs,
                    "updatedAtEpochMs" to pile.updatedAtEpochMs
                )

                firestore.collection("projects").document(project.remoteId)
                    .collection("piles").document(pile.remoteId)
                    .set(payload, SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.w(tag, "pushPile failed", e)
            }
        }
    }

    fun pushHotspot(hotspotId: Long) {
        scope.launch {
            try {
                val h = db.pileHotspotDao().getById(hotspotId) ?: return@launch
                if (h.remoteId.isBlank()) return@launch

                val project = db.projectDao().getById(h.projectId) ?: return@launch
                if (project.remoteId.isBlank()) return@launch

                val pileRemoteId = h.pileId?.let { db.pileDao().getById(it)?.remoteId } ?: h.pileRemoteId

                val payload = hashMapOf<String, Any?>(
                    "ownerUid" to h.ownerUid,
                    "pageIndex" to h.pageIndex,
                    "xNorm" to h.xNorm,
                    "yNorm" to h.yNorm,
                    "createdAtEpochMs" to h.createdAtEpochMs,
                    "updatedAtEpochMs" to h.updatedAtEpochMs,
                    "pileRemoteId" to (pileRemoteId ?: "")
                )

                firestore.collection("projects").document(project.remoteId)
                    .collection("hotspots").document(h.remoteId)
                    .set(payload, SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.w(tag, "pushHotspot failed", e)
            }
        }
    }

    /**
     * Upload optimisé : Compression de l'image avant l'envoi.
     * Utilise ImageHelper pour réduire la taille et éviter les crashs.
     */
    fun uploadPhotoAndSync(projectId: Long, photoUri: String) {
        scope.launch {
            try {
                val project = db.projectDao().getById(projectId) ?: return@launch
                val user = auth.currentUser ?: return@launch
                if (project.remoteId.isBlank()) {
                    Log.w(tag, "Cannot upload photo, project is not synced yet (no remoteId)")
                    return@launch
                }

                // 1. Compression de l'image via notre Helper
                val compressedData = ImageHelper.compressImage(context, photoUri.toUri())
                if (compressedData == null) {
                    Log.e(tag, "Échec compression image pour $photoUri")
                    return@launch
                }

                val photoRemoteId = UUID.randomUUID().toString()
                // On garde .jpg car notre compresseur sort du JPEG
                val storagePath = "project_photos/${user.uid}/${project.remoteId}/$photoRemoteId.jpg"
                val storageRef = storage.reference.child(storagePath)

                // 2. Upload des octets compressés (putBytes au lieu de putFile)
                storageRef.putBytes(compressedData).await()

                // 3. Mise à jour DB locale
                val now = System.currentTimeMillis()
                val newPhoto = PhotoEntity(
                    projectId = projectId,
                    storagePath = storagePath,
                    includeInReport = true,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                    remoteId = photoRemoteId,
                    ownerUid = user.uid
                )
                val localId = db.photoDao().insert(newPhoto)
                pushPhoto(localId)
                Log.d(tag, "Photo optimisée envoyée avec succès : $storagePath")

            } catch (e: Exception) {
                Log.e(tag, "uploadPhotoAndSync failed for projectId=$projectId", e)
            }
        }
    }

    fun updatePhoto(photoId: Long, includeInReport: Boolean) {
        scope.launch {
            val now = System.currentTimeMillis()
            db.photoDao().setIncludeInReport(photoId, includeInReport, now)
            pushPhoto(photoId)
        }
    }

    fun deletePhoto(photoId: Long) {
        scope.launch {
            try {
                val photo = db.photoDao().getById(photoId) ?: return@launch
                if (photo.storagePath.isNotBlank()) {
                    storage.reference.child(photo.storagePath).delete().await()
                }
                firestore.collection("projects").document(photo.remoteId)
                    .collection("photos").document(photo.remoteId).delete().await()
                db.photoDao().deleteById(photoId)
            } catch (e: Exception) {
                Log.e(tag, "deletePhoto failed for photoId=$photoId", e)
            }
        }
    }

    fun pushPhoto(photoId: Long) {
        scope.launch {
            try {
                val photo = db.photoDao().getById(photoId) ?: return@launch
                val project = db.projectDao().getById(photo.projectId) ?: return@launch
                if (project.remoteId.isBlank() || photo.remoteId.isBlank()) return@launch

                val payload = hashMapOf<String, Any?>(
                    "ownerUid" to photo.ownerUid,
                    "storagePath" to photo.storagePath,
                    "includeInReport" to photo.includeInReport,
                    "createdAtEpochMs" to photo.createdAtEpochMs,
                    "updatedAtEpochMs" to photo.updatedAtEpochMs,
                )

                firestore.collection("projects").document(project.remoteId)
                    .collection("photos").document(photo.remoteId)
                    .set(payload, SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.w(tag, "pushPhoto failed", e)
            }
        }
    }

    fun pushMapPoint(id: Long) {
        scope.launch {
            try {
                val p = db.mapPointDao().getById(id) ?: return@launch
                if (p.remoteId.isBlank()) return@launch

                val payload = hashMapOf<String, Any?>(
                    "ownerUid" to p.ownerUid,
                    "name" to p.name,
                    "addressLine" to p.addressLine,
                    "latitude" to p.latitude,
                    "longitude" to p.longitude,
                    "avgDepthFt" to p.avgDepthFt,
                    "createdAtEpochMs" to p.createdAtEpochMs,
                    "updatedAtEpochMs" to p.updatedAtEpochMs
                )

                firestore.collection("map_points")
                    .document(p.remoteId)
                    .set(payload, SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.w(tag, "pushMapPoint failed", e)
            }
        }
    }

    // -------------------------
    // DELETE REMOTE
    // (✅ requis pour tes Repository -> compile)
    // -------------------------

    fun deleteProject(projectRemoteId: String) {
        scope.launch {
            try {
                firestore.collection("projects").document(projectRemoteId).delete().await()
            } catch (e: Exception) {
                Log.w(tag, "deleteProject failed", e)
            }
        }
    }

    // Overload pratique: tu as parfois seulement le remoteId du projet
    fun deletePile(projectRemoteId: String, pileRemoteId: String) {
        scope.launch {
            try {
                firestore.collection("projects").document(projectRemoteId)
                    .collection("piles").document(pileRemoteId)
                    .delete().await()
            } catch (e: Exception) {
                Log.w(tag, "deletePile(remote) failed", e)
            }
        }
    }

    // Version utilisée quand tu as projectId local
    fun deletePile(projectId: Long, pileRemoteId: String) {
        scope.launch {
            try {
                val project = db.projectDao().getById(projectId) ?: return@launch
                if (project.remoteId.isBlank()) return@launch
                firestore.collection("projects").document(project.remoteId)
                    .collection("piles").document(pileRemoteId)
                    .delete().await()
            } catch (e: Exception) {
                Log.w(tag, "deletePile failed", e)
            }
        }
    }

    fun deleteHotspot(projectRemoteId: String, hotspotRemoteId: String) {
        scope.launch {
            try {
                firestore.collection("projects").document(projectRemoteId)
                    .collection("hotspots").document(hotspotRemoteId)
                    .delete().await()
            } catch (e: Exception) {
                Log.w(tag, "deleteHotspot(remote) failed", e)
            }
        }
    }

    fun deleteHotspot(projectId: Long, hotspotRemoteId: String) {
        scope.launch {
            try {
                val project = db.projectDao().getById(projectId) ?: return@launch
                if (project.remoteId.isBlank()) return@launch
                firestore.collection("projects").document(project.remoteId)
                    .collection("hotspots").document(hotspotRemoteId)
                    .delete().await()
            } catch (e: Exception) {
                Log.w(tag, "deleteHotspot failed", e)
            }
        }
    }

    fun deleteMapPoint(remoteId: String) {
        scope.launch {
            try {
                firestore.collection("map_points").document(remoteId).delete().await()
            } catch (e: Exception) {
                Log.w(tag, "deleteMapPoint failed", e)
            }
        }
    }

    // -------------------------
    // small parsing helpers
    // -------------------------

    private fun Map<String, Any?>.str(key: String): String = (this[key] as? String) ?: ""

    private fun Map<String, Any?>.long(key: String): Long =
        when (val v = this[key]) {
            is Number -> v.toLong()
            else -> 0L
        }

    private fun Map<String, Any?>.double(key: String): Double =
        when (val v = this[key]) {
            is Number -> v.toDouble()
            else -> 0.0
        }

    private fun Map<String, Any?>.float(key: String): Float =
        when (val v = this[key]) {
            is Number -> v.toFloat()
            else -> 0f
        }

    private fun Map<String, Any?>.bool(key: String): Boolean = (this[key] as? Boolean) ?: false
}