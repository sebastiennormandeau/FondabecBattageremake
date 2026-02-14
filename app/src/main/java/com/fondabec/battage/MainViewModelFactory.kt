package com.fondabec.battage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.fondabec.battage.data.InspectionRepository
import com.fondabec.battage.data.MapPointRepository
import com.fondabec.battage.data.PhotoRepository
import com.fondabec.battage.data.PileHotspotRepository
import com.fondabec.battage.data.PileRepository
import com.fondabec.battage.data.ProjectDocumentRepository
import com.fondabec.battage.data.ProjectRepository
import com.fondabec.battage.data.SettingsRepository

class MainViewModelFactory(
    private val projectRepo: ProjectRepository,
    private val pileRepo: PileRepository,
    private val hotspotRepo: PileHotspotRepository,
    private val mapPointRepo: MapPointRepository,
    private val photoRepo: PhotoRepository,
    private val documentRepo: ProjectDocumentRepository,
    private val inspectionRepo: InspectionRepository,
    private val settingsRepo: SettingsRepository,
    private val context: Context // Ajout du contexte
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(
                projectRepo,
                pileRepo,
                hotspotRepo,
                mapPointRepo,
                photoRepo,
                documentRepo,
                inspectionRepo,
                settingsRepo,
                context // Passage du contexte
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}