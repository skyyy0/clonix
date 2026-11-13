package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_logs")
data class SandboxAuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cloneId: String,
    val eventType: String,
    val title: String,
    val detail: String,
    val timestamp: Long = System.currentTimeMillis()
)
