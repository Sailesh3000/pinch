package com.expensetracker.ingestion

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates the notification-listener service contract that Google Play review
 * checks. The manifest registration is the enforceable, deterministic part of
 * the notification-access story; timing/arrival behavior is covered manually
 * per `docs/instrumentation_test_plan.md`.
 */
@RunWith(AndroidJUnit4::class)
class NotificationListenerServiceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun listenerServiceInfo(): ServiceInfo {
        return context.packageManager.getServiceInfo(
            ComponentName(context.packageName, SERVICE_CLASS),
            PackageManager.GET_META_DATA,
        )
    }

    @Test
    fun service_isDeclaredInManifest() {
        assertNotNull(listenerServiceInfo())
    }

    @Test
    fun service_requiresNotificationListenerBindingPermission() {
        assertEquals(
            android.Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
            listenerServiceInfo().permission,
        )
    }

    @Test
    fun service_isNotExportedToOtherApps() {
        assertFalse(listenerServiceInfo().exported)
    }

    @Test
    fun service_classIsLoadable() {
        // Guards against R8 stripping the service class from release builds.
        val klass = Class.forName(SERVICE_CLASS)
        assertTrue(
            "Service must extend NotificationListenerService",
            android.service.notification.NotificationListenerService::class.java.isAssignableFrom(klass),
        )
    }

    private companion object {
        const val SERVICE_CLASS = "com.expensetracker.ingestion.TransactionNotificationListenerService"
    }
}