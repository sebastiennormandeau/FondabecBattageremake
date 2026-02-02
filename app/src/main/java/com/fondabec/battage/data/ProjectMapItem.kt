package com.fondabec.battage.data

data class ProjectMapItem(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val avgDepthFt: Double
)
