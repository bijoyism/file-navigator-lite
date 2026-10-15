package com.bijoyism.filenavigator.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {
    @Test
    fun matchesExtensionWithoutLeadingDot() {
        val rule =
            OrganizationRule(
                name = "APK files",
                extensions = setOf(".apk"),
            )

        assertTrue(
            RuleMatcher.matches(
                "installer.APK",
                "application/vnd.android.package-archive",
                rule,
            ),
        )
        assertFalse(
            RuleMatcher.matches(
                "installer.zip",
                "application/zip",
                rule,
            ),
        )
    }

    @Test
    fun supportsWildcardMimeTypes() {
        val rule =
            OrganizationRule(
                name = "Images",
                mimeTypes = setOf("image/*"),
            )

        assertTrue(
            RuleMatcher.matches(
                "photo.unknown",
                "image/jpeg",
                rule,
            ),
        )
        assertFalse(
            RuleMatcher.matches(
                "notes.txt",
                "text/plain",
                rule,
            ),
        )
    }

    @Test
    fun allConfiguredFilterGroupsMustMatch() {
        val rule =
            OrganizationRule(
                name = "Screenshots",
                extensions = setOf("png", "jpg"),
                filenameKeywords = setOf("screenshot", "screen shot"),
            )

        assertTrue(
            RuleMatcher.matches(
                "Screenshot_2026.png",
                "image/png",
                rule,
            ),
        )
        assertFalse(
            RuleMatcher.matches(
                "holiday.png",
                "image/png",
                rule,
            ),
        )
        assertFalse(
            RuleMatcher.matches(
                "Screenshot_2026.pdf",
                "application/pdf",
                rule,
            ),
        )
    }

    @Test
    fun emptyRuleDoesNotMatchEverything() {
        val rule = OrganizationRule(name = "Empty")

        assertFalse(
            RuleMatcher.matches(
                "anything.txt",
                "text/plain",
                rule,
            ),
        )
    }

    @Test
    fun appliesMinimumAndMaximumSizeFilters() {
        val rule =
            OrganizationRule(
                name = "Small PDFs",
                extensions = setOf("pdf"),
                minBytes = 1_000,
                maxBytes = 8_000,
            )

        assertTrue(
            RuleMatcher.matches(
                "file.pdf",
                "application/pdf",
                4_000L,
                rule,
            ),
        )
        assertFalse(
            RuleMatcher.matches(
                "file.pdf",
                "application/pdf",
                100L,
                rule,
            ),
        )
        assertFalse(
            RuleMatcher.matches(
                "file.pdf",
                "application/pdf",
                10_000L,
                rule,
            ),
        )
        assertFalse(
            RuleMatcher.matches(
                "file.pdf",
                "application/pdf",
                null,
                rule,
            ),
        )
    }

    @Test
    fun validatesMissingFoldersAndFilters() {
        val missingFoldersResult =
            OrganizationRule(
                name = "Missing",
                extensions = setOf("pdf"),
            ).isValid()

        val hasMissingFolderError =
            missingFoldersResult?.contains("source and destination") ?: false

        assertTrue(hasMissingFolderError)

        val missingFiltersResult =
            OrganizationRule(
                name = "No filters",
                sourceTreeUri = "source",
                destinationTreeUri = "destination",
            ).isValid()

        val hasMissingFiltersError =
            missingFiltersResult?.contains("at least one") ?: false

        assertTrue(hasMissingFiltersError)
    }
}