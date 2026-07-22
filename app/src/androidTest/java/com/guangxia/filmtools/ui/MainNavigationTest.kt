package com.guangxia.filmtools.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.rule.GrantPermissionRule
import com.guangxia.filmtools.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class MainNavigationTest {
    private val permissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

    @Test
    fun bottomNavigationOpensAllTools() {
        composeRule.onNodeWithText("闪光").performClick()
        composeRule.onNodeWithText("距离、光圈与功率联动").assertIsDisplayed()
        composeRule.onNodeWithText("倒易律").performClick()
        composeRule.onNodeWithText("长曝光补偿与色彩提示").assertIsDisplayed()
        composeRule.onNodeWithText("修正结果").assertIsDisplayed()
        composeRule.onNodeWithText("Kodak · Portra 400").performClick()
        composeRule.onNodeWithText("Portra 160").assertIsDisplayed()
        composeRule.onNodeWithText("Portra 160").performClick()
        composeRule.onNodeWithText("Kodak · Portra 160").assertIsDisplayed()
        composeRule.onAllNodesWithText("胶卷").onLast().performClick()
        composeRule.onNodeWithText("相机与在机胶卷").assertIsDisplayed()
        composeRule.onNodeWithText("测光").performClick()
        composeRule.onNodeWithText("读取场景反射光").assertIsDisplayed()
    }
}
