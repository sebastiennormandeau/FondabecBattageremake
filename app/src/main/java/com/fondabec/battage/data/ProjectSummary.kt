package com.fondabec.battage.data

data class ProjectSummary(
    val id: Long,
    val name: String,
    val city: String,
    val startDateEpochMs: Long,
    val avgDepthFt: Double,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
