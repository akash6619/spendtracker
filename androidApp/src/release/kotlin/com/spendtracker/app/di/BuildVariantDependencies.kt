package com.spendtracker.app.di

import com.spendtracker.core.repository.TransactionRepository

/**
 * Release-build counterpart to the debug dependency provider.
 * It always returns `null`, ensuring synthetic demo transactions and their entry
 * point cannot accidentally become available in production builds.
 */
object BuildVariantDependencies {
    fun createDemoRepository(): TransactionRepository? = null
}
