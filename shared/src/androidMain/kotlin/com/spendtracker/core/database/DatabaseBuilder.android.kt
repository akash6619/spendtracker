package com.spendtracker.core.database

import android.content.Context
import androidx.room3.Room

fun createSpendTrackerDatabase(
    context: Context,
    name: String = "spendtracker.db",
): SpendTrackerDatabase =
    Room.databaseBuilder<SpendTrackerDatabase>(
        context = context.applicationContext,
        name = name,
    ).buildSpendTrackerDatabase()
