package com.bijoyism.filenavigator.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DestinationNameResolverTest {
    @Test
    fun uniqueNameNeverOverwritesAndPreservesExtension() {
        assertEquals(
            "report (2).pdf",
            DestinationNameResolver.resolve("report.pdf", setOf("report.pdf", "report (1).pdf"), ConflictPolicy.UNIQUE_NAME),
        )
    }

    @Test
    fun skipReturnsNullForDuplicateName() {
        assertNull(DestinationNameResolver.resolve("image.png", setOf("image.png"), ConflictPolicy.SKIP))
    }

    @Test
    fun replacementIsRefusedWhenItCannotBeMadeSafe() {
        assertThrows(IllegalStateException::class.java) {
            DestinationNameResolver.resolve("notes.txt", setOf("notes.txt"), ConflictPolicy.REPLACE)
        }
    }

    @Test
    fun matchingIsCaseInsensitiveForExistingNames() {
        assertEquals("archive (1).zip", DestinationNameResolver.resolve("archive.zip", setOf("ARCHIVE.ZIP"), ConflictPolicy.UNIQUE_NAME))
    }
}
