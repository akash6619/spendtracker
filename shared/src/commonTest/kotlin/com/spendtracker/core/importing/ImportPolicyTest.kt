package com.spendtracker.core.importing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ImportPolicyTest {
    @Test
    fun defaultsToThreeMonthsWithoutRawMessageRetention() {
        val policy = ImportPolicy()

        assertEquals(3, policy.historyMonths)
        assertFalse(policy.retainRawMessageBody)
    }
}

