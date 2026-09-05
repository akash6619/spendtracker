package com.spendtracker.core.database

import androidx.room3.Room
import java.io.File

fun createSpendTrackerDatabase(file: File): SpendTrackerDatabase =
    Room.databaseBuilder<SpendTrackerDatabase>(name = file.absolutePath)
        .buildSpendTrackerDatabase()
