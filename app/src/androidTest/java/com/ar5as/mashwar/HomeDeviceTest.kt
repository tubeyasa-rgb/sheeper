package com.ar5as.mashwar

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun manualDestinationAndActionsAreAvailable() {
        var requestedLocation = false
        var openedMaps = false
        var finalDestination = ""
        compose.setContent {
            var origin by remember { mutableStateOf("") }
            var destination by remember { mutableStateOf("") }
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TripHome(origin, { origin = it }, destination, { destination = it; finalDestination = it }, false, "", false, false, emptyList(), "", { requestedLocation = true }, {}, {}, {}, { openedMaps = true }, {})
                }
            }
        }
        compose.onNodeWithText("الوجهة").performScrollTo().performTextInput("Cairo")
        compose.onNodeWithText("استخدم موقعي الحالي").performScrollTo().performClick()
        compose.onNodeWithText("افتح المسار في Google Maps").performScrollTo().performClick()
        assertEquals("Cairo", finalDestination)
        assertTrue(requestedLocation)
        assertTrue(openedMaps)
    }
}
