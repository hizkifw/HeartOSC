package red.kitsu.heartosc.wear.sensor

import org.junit.Assert.assertEquals
import org.junit.Test

class WearHeartRateManagerPermissionTest {

    @Test
    fun testHealthReadHeartRatePermissionConstant() {
        assertEquals(
            "android.permission.health.READ_HEART_RATE",
            WearHeartRateManager.PERMISSION_HEALTH_READ_HEART_RATE
        )
    }

    @Test
    fun testHealthReadHealthDataInBackgroundPermissionConstant() {
        assertEquals(
            "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND",
            WearHeartRateManager.PERMISSION_HEALTH_READ_HEALTH_DATA_IN_BACKGROUND
        )
    }

    @Test
    fun testHasHealthReadHeartRatePermissionReturnsFalseOnLowApi() {
        val isLowApi = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.BAKLAVA
        if (isLowApi) {
            val dummyContext = object : android.content.ContextWrapper(null) {}
            val result = WearHeartRateManager.hasHealthReadHeartRatePermission(dummyContext)
            assertEquals(false, result)
        }
    }
}
