package es.robertodorado.robguard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabletNavigationSafeAreaTest {
    @Test fun phoneKeepsExistingTopAndBottomBarLayout() {
        assertFalse(tabletNeedsNavigationSafeArea(360))
        assertFalse(tabletNeedsNavigationSafeArea(599))
    }

    @Test fun tabletUsesSystemBarsInsetsFrom600Dp() {
        assertTrue(tabletNeedsNavigationSafeArea(600))
        assertTrue(tabletNeedsNavigationSafeArea(800))
    }

    @Test fun widthBreakpointsAreStableAround600Dp() {
        // El mismo umbral controla statusBarsPadding() arriba y navigationBarsPadding() abajo.
        for (width in 0 until 600) assertFalse(tabletNeedsNavigationSafeArea(width))
        for (width in 600..1200 step 50) assertTrue(tabletNeedsNavigationSafeArea(width))
    }

    @Test fun sameTabletClassificationInPortraitAndLandscape() {
        // smallestScreenWidthDp es independiente de la orientación de pantalla.
        val tabletSmallestWidth = 752
        assertTrue(tabletNeedsNavigationSafeArea(tabletSmallestWidth))
        assertTrue(tabletNeedsNavigationSafeArea(tabletSmallestWidth))
    }
}
