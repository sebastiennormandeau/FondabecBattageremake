package com.fondabec.battage

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fondabec.battage.cloud.CloudSyncHolder
import com.fondabec.battage.data.MapPointRepository
import com.fondabec.battage.data.PhotoRepository
import com.fondabec.battage.data.PileHotspotRepository
import com.fondabec.battage.data.PileRepository
import com.fondabec.battage.data.ProjectDocumentEntity
import com.fondabec.battage.data.ProjectDocumentRepository
import com.fondabec.battage.data.ProjectRepository
import com.fondabec.battage.data.ProjectSummary
import com.fondabec.battage.data.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Start : Screen
    data object Home : Screen
    data object DepthMap : Screen

    data class ProjectDetail(val projectId: Long) : Screen

    data class PileDetail(
        val projectId: Long,
        val pileId: Long,
        val returnToPlan: Boolean = false,
        val returnPlanPageIndex: Int = 0
    ) : Screen

    data class ProjectPlan(
        val projectId: Long,
        val pageIndex: Int = 0
    ) : Screen

    // --- NOUVEL ÉCRAN : Visionneuse PDF ---
    data class PdfViewer(
        val projectId: Long,
        val storagePath: String, // Le chemin dans le cloud
        val title: String
    ) : Screen
}

data class AppUiState(
    val screen: Screen = Screen.Start,
    val projectSummaries: List<ProjectSummary> = emptyList(),
    val isDarkMode: Boolean = false
)

class MainViewModel(
    private val projectRepo: ProjectRepository,
    private val pileRepo: PileRepository,
    private val hotspotRepo: PileHotspotRepository,
    private val mapPointRepo: MapPointRepository,
    private val photoRepo: PhotoRepository,
    private val documentRepo: ProjectDocumentRepository, // DOIT ÊTRE PRÉSENT
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        AppUiState(
            screen = Screen.Start,
            isDarkMode = settingsRepo.getDarkMode()
        )
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    // Feedback pour l'upload (optionnel mais utile)
    private val _uploadStatus = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            projectRepo.observeProjectSummaries().collect { list ->
                _state.update { it.copy(projectSummaries = list) }
            }
        }
    }

    // --- Start / Navigation

    fun goStart() { _state.update { it.copy(screen = Screen.Start) } }
    fun goCarnet() { _state.update { it.copy(screen = Screen.Home) } }
    fun goDepthMap() { _state.update { it.copy(screen = Screen.DepthMap) } }

    fun setDarkMode(enabled: Boolean) {
        settingsRepo.setDarkMode(enabled)
        _state.update { it.copy(isDarkMode = enabled) }
    }

    // --- Projects

    fun createProject() {
        viewModelScope.launch {
            val id = projectRepo.createProject("Nouveau projet")
            _state.update { it.copy(screen = Screen.ProjectDetail(id)) }
        }
    }

    fun openProject(id: Long) { _state.update { it.copy(screen = Screen.ProjectDetail(id)) } }
    fun backHome() { _state.update { it.copy(screen = Screen.Home) } }

    fun updateProject(projectId: Long, name: String, city: String) {
        viewModelScope.launch { projectRepo.updateProject(projectId, name, city) }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            projectRepo.deleteProject(projectId)
            backHome()
        }
    }

    fun observeProject(projectId: Long) = projectRepo.observeProject(projectId)
    fun observePiles(projectId: Long) = pileRepo.observePilesForProject(projectId)
    fun observePile(pileId: Long) = pileRepo.observePile(pileId)

    // --- Documents Techniques ---

    fun observeDocuments(projectId: Long): Flow<List<ProjectDocumentEntity>> =
        documentRepo.observeDocuments(projectId)

    fun uploadTechnicalDocument(projectId: Long, uri: Uri, title: String) {
        viewModelScope.launch {
            _uploadStatus.value = "Envoi..."
            documentRepo.addDocument(projectId, uri, title)
        }
    }

    fun deleteTechnicalDocument(document: ProjectDocumentEntity) {
        viewModelScope.launch {
            documentRepo.deleteDocument(document)
        }
    }

    // --- NOUVEAU : Fonction pour ouvrir la visionneuse ---
    fun openDocumentViewer(projectId: Long, storagePath: String, title: String) {
        _state.update {
            it.copy(screen = Screen.PdfViewer(projectId, storagePath, title))
        }
    }

    // --- Piles

    fun addPile(projectId: Long) {
        viewModelScope.launch {
            val pileId = pileRepo.addPile(projectId)
            _state.update { it.copy(screen = Screen.PileDetail(projectId, pileId, returnToPlan = false)) }
        }
    }

    fun addPiles(projectId: Long, count: Int, gaugeIn: String) {
        viewModelScope.launch {
            pileRepo.addPiles(projectId, count, gaugeIn)
            _state.update { it.copy(screen = Screen.ProjectDetail(projectId)) }
        }
    }

    fun addPilesByGauge(projectId: Long, qtyByGauge: Map<String, Int>) {
        viewModelScope.launch {
            pileRepo.addPilesByGauge(projectId, qtyByGauge)
            _state.update { it.copy(screen = Screen.ProjectDetail(projectId)) }
        }
    }

    fun openPile(projectId: Long, pileId: Long, returnToPlan: Boolean = false) {
        _state.update { it.copy(screen = Screen.PileDetail(projectId, pileId, returnToPlan, returnPlanPageIndex = 0)) }
    }

    // --- Plan PDF

    fun openPlan(projectId: Long) {
        _state.update { it.copy(screen = Screen.ProjectPlan(projectId, pageIndex = 0)) }
    }

    fun setPlanPdfUri(projectId: Long, planPdfUri: String) {
        viewModelScope.launch { projectRepo.setPlanPdfPath(projectId, planPdfUri) }
    }

    fun uploadPlanPdf(projectId: Long, pdfUri: String) {
        CloudSyncHolder.sync()?.uploadPlanPdfAndSync(projectId, pdfUri)
    }

    fun removePlanPdf(projectId: Long) {
        CloudSyncHolder.sync()?.removePlanPdf(projectId)
    }

    // --- Photos ---

    fun observePhotos(projectId: Long) = photoRepo.observePhotosForProject(projectId)

    fun addPhoto(projectId: Long, photoUri: String) {
        viewModelScope.launch { photoRepo.addPhoto(projectId, photoUri) }
    }

    fun updatePhoto(photoId: Long, includeInReport: Boolean) {
        viewModelScope.launch { photoRepo.updatePhoto(photoId, includeInReport) }
    }

    fun deletePhoto(photoId: Long) {
        viewModelScope.launch { photoRepo.deletePhoto(photoId) }
    }

    private fun backFromPile(projectId: Long, returnToPlan: Boolean, returnPlanPageIndex: Int) {
        _state.update {
            it.copy(
                screen = if (returnToPlan) Screen.ProjectPlan(projectId, pageIndex = returnPlanPageIndex)
                else Screen.ProjectDetail(projectId)
            )
        }
    }

    fun backFromPileScreen(projectId: Long, returnToPlan: Boolean, returnPlanPageIndex: Int) {
        backFromPile(projectId, returnToPlan, returnPlanPageIndex)
    }

    fun savePileAndBack(
        projectId: Long,
        pileId: Long,
        pileNo: String,
        gaugeIn: String,
        depthFt: Double,
        implanted: Boolean,
        returnToPlan: Boolean,
        returnPlanPageIndex: Int
    ) {
        viewModelScope.launch {
            pileRepo.updatePile(projectId, pileId, pileNo, gaugeIn, depthFt, implanted)
            backFromPile(projectId, returnToPlan, returnPlanPageIndex)
        }
    }

    fun deletePile(projectId: Long, pileId: Long, returnToPlan: Boolean, returnPlanPageIndex: Int) {
        viewModelScope.launch {
            pileRepo.deletePile(pileId)
            backFromPile(projectId, returnToPlan, returnPlanPageIndex)
        }
    }

    // --- Hotspots

    fun observeHotspots(projectId: Long, pageIndex: Int) =
        hotspotRepo.observeHotspotsForPage(projectId, pageIndex)

    fun addHotspot(projectId: Long, pageIndex: Int, xNorm: Float, yNorm: Float) {
        viewModelScope.launch {
            val pileId = pileRepo.addPile(projectId)
            hotspotRepo.addHotspot(projectId, pageIndex, xNorm, yNorm, pileId)
        }
    }

    fun onHotspotTap(projectId: Long, hotspotId: Long, currentPageIndex: Int) {
        viewModelScope.launch {
            val h = hotspotRepo.getHotspot(hotspotId) ?: return@launch
            val pileId = h.pileId ?: run {
                val newPileId = pileRepo.addPile(projectId)
                hotspotRepo.setPileId(hotspotId, newPileId)
                newPileId
            }

            _state.update {
                it.copy(
                    screen = Screen.PileDetail(
                        projectId = projectId,
                        pileId = pileId,
                        returnToPlan = true,
                        returnPlanPageIndex = currentPageIndex
                    )
                )
            }
        }
    }

    fun undoLastHotspot(projectId: Long, pageIndex: Int) {
        viewModelScope.launch {
            val pileId = hotspotRepo.undoLastHotspot(projectId, pageIndex)
            if (pileId != null) pileRepo.deletePile(pileId)
        }
    }

    // --- Map ---

    fun observeMapPoints() = mapPointRepo.observeAll()

    fun addMapPoint(name: String, addressLine: String, lat: Double, lng: Double, avgDepthFt: Double) {
        viewModelScope.launch {
            mapPointRepo.addPoint(name, addressLine, lat, lng, avgDepthFt)
        }
    }

    fun deleteMapPoint(id: Long) {
        viewModelScope.launch {
            mapPointRepo.deletePoint(id)
        }
    }

    fun observeMapProjects() = projectRepo.observeMapProjects()
    fun updateProjectLocation(
        projectId: Long,
        street: String,
        city: String,
        province: String,
        postalCode: String,
        country: String,
        latitude: Double,
        longitude: Double
    ) {
        viewModelScope.launch {
            projectRepo.updateProjectLocation(
                projectId = projectId,
                street = street,
                city = city,
                province = province,
                postalCode = postalCode,
                country = country,
                latitude = latitude,
                longitude = longitude
            )
        }
    }
}