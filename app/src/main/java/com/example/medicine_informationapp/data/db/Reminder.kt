package com.example.medicine_informationapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicineName: String,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: String, // e.g. "Mon, Wed, Fri" or "Everyday"
    val isEnabled: Boolean = true
)
