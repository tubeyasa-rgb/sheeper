package com.ar5as.mashwar

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripFlowDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<ComposeActivity>()
    @Test fun manualQuoteAndFeedback() {
        compose.onNodeWithText("إضافة عرض").performClick()
        compose.onNodeWithText("نقطة الانطلاق").performScrollTo().performTextInput("origin fixture")
        compose.onNodeWithText("الوجهة").performScrollTo().performTextInput("destination fixture")
        compose.onNodeWithText("التطبيق").performScrollTo().performTextInput("test provider")
        compose.onNodeWithText("فئة الخدمة").performScrollTo().performTextInput("economy")
        compose.onNodeWithText("السعر بالجنيه").performScrollTo().performTextInput("120")
        compose.onNodeWithText("تأكيد وحفظ العرض").performScrollTo().performClick()
        compose.onNodeWithText("تسجيل نتيجة الرحلة").performScrollTo().performClick()
        compose.onNodeWithText("السعر المقبول جنيه — اختياري").performScrollTo().performTextInput("125")
        compose.onNodeWithText("المدفوع فعليًا جنيه — اختياري").performScrollTo().performTextInput("130")
        compose.onNodeWithText("نعم").performScrollTo().performClick()
        compose.onNodeWithText("حفظ النتيجة").performScrollTo().performClick()
        compose.onNodeWithText("نتيجة الرحلة:",substring=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("مدة الرحلة والزحمة").performScrollTo().performClick()
        compose.onNodeWithText("المدة المتوقعة بالدقائق").performScrollTo().performTextInput("25")
        compose.onNodeWithText("المسافة بالكيلومتر · اختياري").performScrollTo().performTextInput("10")
        compose.onNodeWithText("تأكيد بيانات المسار واستخدامها").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("السعر ÷ المسافة:",substring=true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("السعر ÷ المسافة:",substring=true).performScrollTo().assertIsDisplayed()
    }
}
