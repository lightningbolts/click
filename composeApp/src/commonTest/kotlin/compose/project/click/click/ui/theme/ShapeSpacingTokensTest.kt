package compose.project.click.click.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.components.AppScreenDefaults
import compose.project.click.click.ui.components.ClickButtonVariant
import compose.project.click.click.ui.components.ClickFieldTokens
import compose.project.click.click.ui.components.ClickScreenSpacing
import compose.project.click.click.ui.components.GlassCornerRadius
import compose.project.click.click.ui.components.clickButtonMinHeight
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins shape and spacing tokens to iOS `Click/DesignSystem/Spacing.swift`. */
class ShapeSpacingTokensTest {
    @Test
    fun spacing_matchesIosClickSpacing() {
        assertEquals(2.dp, ClickSpacing.xxs)
        assertEquals(4.dp, ClickSpacing.xs)
        assertEquals(8.dp, ClickSpacing.sm)
        assertEquals(16.dp, ClickSpacing.md)
        assertEquals(24.dp, ClickSpacing.lg)
        assertEquals(32.dp, ClickSpacing.xl)
        assertEquals(48.dp, ClickSpacing.xxl)
        assertEquals(16.dp, ClickSpacing.screenGutter)
        assertEquals(16.dp, ClickSpacing.surfacePadding)
    }

    @Test
    fun radius_matchesIosClickRadius() {
        assertEquals(12.dp, ClickRadius.compact)
        assertEquals(14.dp, ClickRadius.field)
        assertEquals(18.dp, ClickRadius.messageBubble)
        assertEquals(26.dp, ClickRadius.surface)
        assertEquals(30.dp, ClickRadius.prominent)
    }

    @Test
    fun shapes_useTheMatchingRadius() {
        val shapes = ClickShapes()
        assertEquals(RoundedCornerShape(12.dp), shapes.compact)
        assertEquals(RoundedCornerShape(14.dp), shapes.field)
        assertEquals(RoundedCornerShape(18.dp), shapes.messageBubble)
        assertEquals(RoundedCornerShape(26.dp), shapes.surface)
        assertEquals(RoundedCornerShape(30.dp), shapes.prominent)
        assertEquals(RoundedCornerShape(percent = 50), shapes.capsule)
    }

    @Test
    fun adoptedComponents_readTheTokens() {
        assertEquals(ClickRadius.field, ClickFieldTokens.CornerRadius)
        assertEquals(ClickRadius.field, ClickFieldTokens.CompactCornerRadius)
        assertEquals(ClickSpacing.lg, ClickScreenSpacing.Section)
        assertEquals(ClickSpacing.sm, ClickScreenSpacing.RowGap)
        assertEquals(ClickSpacing.sm, ClickScreenSpacing.Compact)
    }

    @Test
    fun metrics_matchIosClickMetrics() {
        assertEquals(50.dp, ClickMetrics.primaryActionHeight)
        assertEquals(44.dp, ClickMetrics.secondaryActionHeight)
    }

    @Test
    fun clickButton_usesIosActionHeights() {
        assertEquals(50.dp, clickButtonMinHeight(ClickButtonVariant.Primary))
        assertEquals(50.dp, clickButtonMinHeight(ClickButtonVariant.Accent))
        assertEquals(50.dp, clickButtonMinHeight(ClickButtonVariant.Destructive))
        assertEquals(44.dp, clickButtonMinHeight(ClickButtonVariant.Secondary))
    }

    @Test
    fun sharedCards_useSurfaceRadius() {
        assertEquals(ClickRadius.surface, androidPlatformStyle.cardCornerRadius)
        assertEquals(ClickRadius.surface, GlassCornerRadius)
    }

    @Test
    fun screenGutter_isIosGutter() {
        assertEquals(16.dp, ClickScreenSpacing.Horizontal)
        assertEquals(ClickSpacing.screenGutter, ClickScreenSpacing.Horizontal)
        assertEquals(ClickSpacing.screenGutter, AppScreenDefaults.HorizontalPadding)
    }
}
