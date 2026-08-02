package com.tanvoid0.portallauncher.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.tanvoid0.portallauncher.ui.launcher.clickableCell
import com.tanvoid0.portallauncher.ui.theme.PortalLauncherTheme
import org.junit.Rule
import org.junit.Test

/**
 * What TalkBack actually hears.
 *
 * These assertions run against Compose's **merged** semantics tree — the one screen
 * readers consume. `uiautomator dump` cannot check any of this: it reports the
 * unmerged tree, where labels always sit on separate nodes whether merging is on or
 * not. This was the accessibility question PRODUCTION_PLAN §8 left explicitly open.
 */
class SemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private fun role(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    @Test
    fun portalRowWithoutOnClickExposesNoClickAction() {
        // The claim in PortalRow's docs: an informational row must expose no click
        // action rather than a disabled one.
        rule.setContent {
            PortalLauncherTheme {
                PortalRow(title = "App categories", subtitle = "Long-press any app")
            }
        }
        rule.onNodeWithText("App categories").assertHasNoClickAction()
    }

    @Test
    fun portalRowWithOnClickIsOneMergedButton() {
        rule.setContent {
            PortalLauncherTheme {
                PortalRow(title = "Hidden apps", subtitle = "Bring them back", onClick = {})
            }
        }
        // One node carrying both texts and the click — not a label next to a
        // separately-focusable target.
        rule.onNode(hasText("Hidden apps") and hasText("Bring them back"))
            .assertHasClickAction()
        rule.onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun selectableRowIsOneSelectableRadioTarget() {
        rule.setContent {
            PortalLauncherTheme {
                SelectableRow(title = "Study", selected = true, onSelect = {})
            }
        }
        // The whole row is the target; the RadioButton inside has onClick = null so
        // it must not surface as a second focusable thing.
        rule.onNode(hasText("Study") and role(Role.RadioButton)).assertIsSelected()
        rule.onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @Test
    fun clickableCellMergesIconAndLabelIntoOneButton() {
        rule.setContent {
            PortalLauncherTheme {
                Box(modifier = Modifier.clickableCell(onClick = {})) {
                    Text("Maps")
                }
            }
        }
        rule.onNode(hasText("Maps") and role(Role.Button)).assertHasClickAction()
        rule.onAllNodes(hasClickAction()).assertCountEquals(1)
    }
}
