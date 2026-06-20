package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_entries")
data class StockEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val riceType: String,
    val riceType1: String,
    val riceType2: String,
    val riceType3: String,
    val riceType4: String,
    val daraStatus: String,
    val partyName: String,
    val status: String,
    val station: String,
    val weight: Double,
    val bags: Int,
    val location: String,
    val localPhotoPaths: String = "", // Comma-separated file paths of photos saved in internal filesDir
    val uploadedPhotoUrls: String = "", // Comma-separated ImgBB links
    val syncStatus: String = "PENDING", // PENDING, SYNCING, SUCCESS, FAILED
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toPhotoPathList(): List<String> =
        if (localPhotoPaths.isEmpty()) emptyList() else localPhotoPaths.split(",")

    fun toPhotoUrlList(): List<String> =
        if (uploadedPhotoUrls.isEmpty()) emptyList() else uploadedPhotoUrls.split(",")
}
