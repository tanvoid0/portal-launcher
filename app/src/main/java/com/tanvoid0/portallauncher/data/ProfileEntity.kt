package com.tanvoid0.portallauncher.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconResName: String,
    val type: String,
    val enabledAutomationIds: List<String>,
    val sortOrder: Int = 0
)
