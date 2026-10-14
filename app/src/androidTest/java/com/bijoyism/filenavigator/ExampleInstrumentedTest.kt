package com.bijoyism.filenavigator

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun packageNameIsCorrect() {
        assertEquals("com.bijoyism.filenavigator", BuildConfig.APPLICATION_ID)
    }
}
