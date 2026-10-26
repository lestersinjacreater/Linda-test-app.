package com.linda.app.features.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckTest {
    /** A release page shaped like GitHub's, with the given assets. */
    private fun release(name: String, vararg assets: String): String {
        val list = JSONArray()
        for (a in assets) {
            list.put(
                JSONObject()
                    .put("name", a)
                    .put("size", 2325288)
                    .put("browser_download_url", "https://github.com/x/y/releases/download/latest-build/$a"),
            )
        }
        return JSONObject().put("name", name).put("tag_name", "latest-build").put("assets", list).toString()
    }

    @Test fun readsTheBuildNumberFromTheReleaseName() {
        assertEquals(179, UpdateCheck.buildNumber("Latest build #179 (3588ccc)"))
        assertNull(UpdateCheck.buildNumber("Latest build"))
    }

    @Test fun aNewerBuildIsAnUpdate() {
        val u = UpdateCheck.newer(release("Latest build #182 (abc1234)", "linda.apk", "linda-debug.apk"), installedBuild = 179)
        assertEquals(182, u?.build)
        assertEquals("https://github.com/x/y/releases/download/latest-build/linda.apk", u?.apkUrl)
        assertEquals(2325288L, u?.sizeBytes)
    }

    @Test fun theSameOrAnOlderBuildIsNot() {
        assertNull(UpdateCheck.newer(release("Latest build #179 (abc1234)", "linda.apk"), installedBuild = 179))
        assertNull(UpdateCheck.newer(release("Latest build #150 (abc1234)", "linda.apk"), installedBuild = 179))
    }

    @Test fun noSmallApkOrABrokenPageMeansNoUpdate() {
        assertNull(UpdateCheck.newer(release("Latest build #182 (abc1234)", "linda-debug.apk"), installedBuild = 179))
        assertNull(UpdateCheck.newer("<html>rate limited</html>", installedBuild = 179))
    }

    @Test fun sizeInMegabytes() {
        assertEquals("2.2", UpdateCheck.megabytes(2_325_288))
    }
}
