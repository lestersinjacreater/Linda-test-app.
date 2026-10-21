package com.linda.app

import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings shows this, so the team can tell which build a phone has (build.gradle.kts explains the numbers). */
class VersionTest {
    @Test fun versionNameNamesTheBuildAndCommit() {
        val name = BuildConfig.VERSION_NAME
        val ci = Regex("0\\.1\\.[0-9]+ \\([0-9a-f]{7}\\)")
        assertTrue(name, ci.matches(name) || name == "0.1.0 (local)")
        if (System.getenv("GITHUB_RUN_NUMBER") != null) {
            assertTrue("a CI build must carry its run number: $name", ci.matches(name))
            assertTrue(BuildConfig.VERSION_CODE == System.getenv("GITHUB_RUN_NUMBER")!!.toInt())
        }
    }
}
