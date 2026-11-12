package es.robertodorado.robguard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabletNavigationSafeAreaTest {
    @Test fun phoneKeepsExistingBottomBarLayout() {
        assertFalse(tabletNeedsNavigationSafeArea(360))
        assertFalse(tabletNeedsNavigationSafeArea(599))
    }

    @Test fun tabletUsesSystemNavigationInsetsFrom600Dp() {
        assertTrue(tabletNeedsNavigationSafeArea(600))
        assertTrue(tabletNeedsNavigationSafeArea(800))
    }

    @Test fun sameTabletClassificationInPortraitAndLandscape() {
        // smallestScreenWidthDp es independiente de la orientación de pantalla.
        val tabletSmallestWidth = 752
        assertTrue(tabletNeedsNavigationSafeArea(tabletSmallestWidth))
        assertTrue(tabletNeedsNavigationSafeArea(tabletSmallestWidth))
    }
}
