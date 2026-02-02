package com.fondabec.battage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import com.fondabec.battage.auth.AuthViewModel
import com.fondabec.battage.cloud.CloudSyncHolder
import com.fondabec.battage.data.AppDatabase
import com.fondabec.battage.data.MapPointRepository
import com.fondabec.battage.data.PhotoRepository
import com.fondabec.battage.data.PileHotspotRepository
import com.fondabec.battage.data.PileRepository
import com.fondabec.battage.data.ProjectDocumentRepository
import com.fondabec.battage.data.ProjectRepository
import com.fondabec.battage.data.SettingsRepository
import com.fondabec.battage.ui.AppRoot
import com.fondabec.battage.ui.AuthGate
import com.fondabec.battage.ui.theme.FondabecBattageTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getInstance(applicationContext)

        // Cloud sync (écoute Auth + Firestore listeners)
        CloudSyncHolder.init(applicationContext, db)
        CloudSyncHolder.start()

        // Repos
        val projectRepo = ProjectRepository(dao = db.projectDao())

        val pileRepo = PileRepository(
            dao = db.pileDao(),
            projectDao = db.projectDao()
        )

        val hotspotRepo = PileHotspotRepository(
            dao = db.pileHotspotDao(),
            projectDao = db.projectDao(),
            pileDao = db.pileDao()
        )

        val mapPointRepo = MapPointRepository(dao = db.mapPointDao())
        val photoRepo = PhotoRepository(dao = db.photoDao())

        // NOUVEAU REPO POUR LES DOCS
        val documentRepo = ProjectDocumentRepository(
            dao = db.projectDocumentDao(),
            projectDao = db.projectDao()
        )

        val settingsRepo = SettingsRepository(applicationContext)

        val vm = ViewModelProvider(
            this,
            MainViewModelFactory(
                projectRepo,
                pileRepo,
                hotspotRepo,
                mapPointRepo,
                photoRepo,
                documentRepo, // <--- AJOUTÉ
                settingsRepo
            )
        )[MainViewModel::class.java]

        val authVm = ViewModelProvider(this)[AuthViewModel::class.java]

        setContent {
            val state by vm.state.collectAsState()

            FondabecBattageTheme(darkTheme = state.isDarkMode) {
                AuthGate(authVm = authVm) {
                    AppRoot(
                        vm = vm,
                        onLogout = { authVm.signOut() }
                    )
                }
            }
        }
    }
}