package com.voicerpg.engine.ui.story

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundAspectTest {

    @Test
    fun `fromDimensions classifies phone portrait, unfolded foldable square, and landscape`() {
        // Typical 9:16 / 9:20 phones (folded outer screen)
        assertEquals(BackgroundAspect.PORTRAIT, BackgroundAspect.fromDimensions(720, 1280))
        assertEquals(BackgroundAspect.PORTRAIT, BackgroundAspect.fromDimensions(1080, 2400))
        assertEquals(BackgroundAspect.PORTRAIT, BackgroundAspect.fromDimensions(412, 915))

        // Unfolded foldables (Pixel 9 Pro Fold, Galaxy Z Fold) in both upright and rotated orientations
        assertEquals(BackgroundAspect.SQUARE, BackgroundAspect.fromDimensions(2076, 2152))
        assertEquals(BackgroundAspect.SQUARE, BackgroundAspect.fromDimensions(2152, 2076))
        assertEquals(BackgroundAspect.SQUARE, BackgroundAspect.fromDimensions(1812, 2176))
        assertEquals(BackgroundAspect.SQUARE, BackgroundAspect.fromDimensions(2176, 1812))
        assertEquals(BackgroundAspect.SQUARE, BackgroundAspect.fromDimensions(1024, 1024))

        // Widescreen landscape phones / tablets
        assertEquals(BackgroundAspect.LANDSCAPE, BackgroundAspect.fromDimensions(1376, 768))
        assertEquals(BackgroundAspect.LANDSCAPE, BackgroundAspect.fromDimensions(2400, 1080))
        assertEquals(BackgroundAspect.LANDSCAPE, BackgroundAspect.fromDimensions(1280, 720))
    }

    @Test
    fun `variantPath inserts _p or _s without double-suffixing`() {
        assertEquals(
            "story/chappaqua_server_room_p.jpg",
            BackgroundAspect.variantPath("story/chappaqua_server_room.jpg", BackgroundAspect.PORTRAIT)
        )
        assertEquals(
            "story/chappaqua_server_room_s.jpg",
            BackgroundAspect.variantPath("story/chappaqua_server_room.jpg", BackgroundAspect.SQUARE)
        )
        assertEquals(
            "story/chappaqua_server_room.jpg",
            BackgroundAspect.variantPath("story/chappaqua_server_room.jpg", BackgroundAspect.LANDSCAPE)
        )

        // Already suffixed paths are left untouched
        assertEquals(
            "story/chappaqua_server_room_p.jpg",
            BackgroundAspect.variantPath("story/chappaqua_server_room_p.jpg", BackgroundAspect.SQUARE)
        )
        assertEquals(
            "story/chappaqua_server_room_s.jpg",
            BackgroundAspect.variantPath("story/chappaqua_server_room_s.jpg", BackgroundAspect.PORTRAIT)
        )

        // Paths without an extension are left untouched
        assertEquals(
            "story/noext",
            BackgroundAspect.variantPath("story/noext", BackgroundAspect.SQUARE)
        )
    }
}
