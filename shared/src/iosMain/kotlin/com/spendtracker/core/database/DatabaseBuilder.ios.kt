package com.spendtracker.core.database

import androidx.room3.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun createSpendTrackerDatabase(): SpendTrackerDatabase {
    val documents = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    ) ?: error("Documents directory unavailable")
    return Room.databaseBuilder<SpendTrackerDatabase>(
        name = "${documents.path}/spendtracker.db",
    ).buildSpendTrackerDatabase()
}
