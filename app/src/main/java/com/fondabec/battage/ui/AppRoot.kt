package com.fondabec.battage.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.fondabec.battage.MainViewModel
import com.fondabec.battage.Screen

@Composable
fun AppRoot(
    vm: MainViewModel,
    onLogout: (() -> Unit)? = null
) {
    val state by vm.state.collectAsState()

    when (val s = state.screen) {

        Screen.Start -> StartScreen(
            isDarkMode = state.isDarkMode,
            onDarkModeChanged = { vm.setDarkMode(it) },
            onOpenCarnet = { vm.goCarnet() },
            onOpenMap = { vm.goDepthMap() },
            onOpenInspections = { vm.goInspections() },
            onLogout = onLogout
        )

        Screen.DepthMap -> DepthMapScreen(
            observeProjects = { vm.observeMapProjects() },
            observeHistoryPoints = { vm.observeMapPoints() },
            onAddHistoryPoint = { name, address, lat, lng, avg ->
                vm.addMapPoint(name, address, lat, lng, avg)
            },
            onDeleteHistoryPoint = { id -> vm.deleteMapPoint(id) },
            onOpenProject = { id -> vm.openProject(id) },
            onBack = { vm.goStart() }
        )

        Screen.Home -> HomeScreen(
            projects = state.projectSummaries,
            isDarkMode = state.isDarkMode,
            onBackToStart = { vm.goStart() },
            onCreate = { vm.createProject() },
            onOpen = { id -> vm.openProject(id) }
        )

        is Screen.ProjectDetail -> ProjectDetailScreen(
            projectId = s.projectId,
            observeProject = { vm.observeProject(s.projectId) },
            observePiles = { vm.observePiles(s.projectId) },
            observeGroupedPiles = { vm.observeGroupedPiles(s.projectId) },
            observePhotos = { vm.observePhotos(s.projectId) },
            observeDocuments = { vm.observeDocuments(s.projectId) },
            observeInspections = { vm.observeInspectionsForProject(s.projectId) },
            onBack = { vm.backHome() },
            onSaveProject = { name, city, plannedDepth -> vm.updateProject(s.projectId, name, city, plannedDepth) },
            onDeleteProject = { vm.deleteProject(s.projectId) },
            onAddPile = { vm.addPile(s.projectId) },
            onOpenPile = { pileId -> vm.openPile(s.projectId, pileId, returnToPlan = false) },
            onQuickAddTotal = { count, gaugeIn -> vm.addPiles(s.projectId, count, gaugeIn) },
            onQuickAddByGauge = { qtyByGauge -> vm.addPilesByGauge(s.projectId, qtyByGauge) },
            onUpdateProjectLocation = { street, city, province, postalCode, country, latitude, longitude ->
                vm.updateProjectLocation(
                    projectId = s.projectId,
                    street = street,
                    city = city,
                    province = province,
                    postalCode = postalCode,
                    country = country,
                    latitude = latitude,
                    longitude = longitude
                )
            },
            onOpenPlan = { vm.openPlan(s.projectId) },
            onUploadPlanPdf = { pdfUri -> vm.uploadPlanPdf(s.projectId, pdfUri) },
            onRemovePlanPdf = { vm.removePlanPdf(s.projectId) },
            onAddPhoto = { photoUri -> vm.addPhoto(s.projectId, photoUri) },
            onUpdatePhoto = { photoId, includeInReport -> vm.updatePhoto(photoId, includeInReport) },
            onDeletePhoto = { photoId -> vm.deletePhoto(photoId) },
            onUploadTechnicalDocument = { uri, title -> vm.uploadTechnicalDocument(s.projectId, uri, title) },
            onDeleteTechnicalDocument = { doc -> vm.deleteTechnicalDocument(doc) },
            onViewTechnicalDocument = { doc -> vm.openDocumentViewer(doc) },
            onExportInspection = { report -> vm.exportInspectionReport(report) },
            onDeleteInspection = { reportId -> vm.deleteInspection(reportId) }
        )

        is Screen.PileDetail -> PileDetailScreen(
            projectId = s.projectId,
            pileId = s.pileId,
            observePile = { vm.observePile(s.pileId) },
            onBack = { vm.backFromPileScreen(s.projectId, s.returnToPlan, s.returnPlanPageIndex) },
            onSave = { pileNo, gaugeIn, depthFt, implanted, rebattage, shape ->
                vm.savePileAndBack(
                    projectId = s.projectId,
                    pileId = s.pileId,
                    pileNo = pileNo,
                    gaugeIn = gaugeIn,
                    depthFt = depthFt,
                    implanted = implanted,
                    rebattage = rebattage,
                    shape = shape,
                    returnToPlan = s.returnToPlan,
                    returnPlanPageIndex = s.returnPlanPageIndex
                )
            },
            onDelete = { vm.deletePile(s.projectId, s.pileId, s.returnToPlan, s.returnPlanPageIndex) }
        )

        is Screen.ProjectPlan -> ProjectPlanScreen(
            projectId = s.projectId,
            initialPageIndex = s.pageIndex,
            observeProject = { vm.observeProject(s.projectId) },
            observePiles = { vm.observePiles(s.projectId) },
            observeHotspotsForPage = { page -> vm.observeHotspots(s.projectId, page) },
            onAddHotspot = { page, x, y -> vm.addHotspot(s.projectId, page, x, y) },
            onAddAiDetectedPiles = { page, aiPieux -> vm.addAiDetectedPiles(s.projectId, page, aiPieux) },
            onDeleteHotspot = { hotspotId -> vm.deleteHotspot(hotspotId) },
            onHotspotTap = { hotspotId, currentPage -> vm.onHotspotTap(s.projectId, hotspotId, currentPage) },
            onUndoLastHotspot = { page -> vm.undoLastHotspot(s.projectId, page) },
            onBack = { vm.openProject(s.projectId) },
            initialScale = vm.savedPlanScale,
            initialOffsetX = vm.savedPlanOffsetX,
            initialOffsetY = vm.savedPlanOffsetY,
            onSaveViewState = { scale, offsetX, offsetY ->
                vm.savedPlanScale = scale
                vm.savedPlanOffsetX = offsetX
                vm.savedPlanOffsetY = offsetY
            }
        )

        is Screen.PdfViewer -> PdfViewerScreen(
            storagePath = s.storagePath,
            title = s.title,
            onBack = { vm.openProject(s.projectId) }
        )

        is Screen.ImageViewer -> ImageViewerScreen(
            storagePath = s.storagePath,
            title = s.title,
            onBack = { vm.openProject(s.projectId) }
        )

        Screen.InspectionEquipmentSelection -> InspectionEquipmentScreen(
            onSelectEquipment = { equipmentType -> vm.openInspectionForm(equipmentType) },
            onBack = { vm.goStart() }
        )

        is Screen.InspectionForm -> InspectionFormScreen(
            equipmentType = s.equipmentType,
            projects = state.projectSummaries,
            onSave = { projectId, equipmentType, operatorName, machineHours, notes, points ->
                vm.saveInspection(projectId, equipmentType, operatorName, machineHours, notes, points)
            },
            onBack = { vm.goInspections() },
            onExport = {
                // This is tricky because we don't have the full report object here.
                // We would need to fetch it first, which is not ideal from the UI.
                // A better approach would be to have the export button on the list screen.
            }
        )
    }
}