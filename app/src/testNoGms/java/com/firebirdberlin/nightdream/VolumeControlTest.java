/*
 * NightDream
 * Copyright (C) 2025 Stefan Fruhner
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.firebirdberlin.nightdream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VolumeControlTest {
    @Test
    public void swipesAdjustRelativelyInBothDirections() {
        assertEquals(2, VolumeControl.swipeSteps(200, 150, 24));
        assertEquals(-2, VolumeControl.swipeSteps(100, 150, 24));
        assertEquals(-2, VolumeControl.swipeSteps(400, 450, 24));
    }

    @Test
    public void shortMovementWaitsForAFullStep() {
        assertEquals(0, VolumeControl.swipeSteps(100, 123, 24));
        assertEquals(0, VolumeControl.swipeSteps(100, 77, 24));
        assertEquals(-1, VolumeControl.swipeSteps(100, 124, 24));
    }

    @Test
    public void cancellingFadePreservesTheEffectiveVolume() {
        assertEquals(4, VolumeControl.effectiveVolume(8, 0.5f));
        assertEquals(0, VolumeControl.effectiveVolume(8, 0f));
        assertEquals(8, VolumeControl.effectiveVolume(8, 1f));
        assertEquals(3, VolumeControl.effectiveVolume(10, 0.26f));
    }

    @Test
    public void repeatedLandscapeSwipesEachChangeVolume() {
        for (int i = 0; i < 3; i++) {
            VolumeControl swipe = new VolumeControl(1260, 100, 1280, 48, 48, 8, 24);
            assertEquals(-1, swipe.move(1260, 125));
            assertTrue(swipe.isActive());
        }
    }

    @Test
    public void movementAccumulatesAndReversesRelativeToLastStep() {
        VolumeControl swipe = new VolumeControl(1260, 100, 1280, 48, 48, 8, 24);
        assertEquals(0, swipe.move(1260, 115));
        assertEquals(-1, swipe.move(1260, 125));
        assertEquals(0, swipe.move(1260, 105));
        assertEquals(1, swipe.move(1260, 100));
    }

    @Test
    public void leftSideAndHorizontalSwipesDoNotControlVolume() {
        VolumeControl left = new VolumeControl(100, 100, 1280, 48, 48, 8, 24);
        assertEquals(0, left.move(100, 150));
        assertFalse(left.isActive());

        VolumeControl horizontal = new VolumeControl(1260, 100, 1280, 48, 48, 8, 24);
        assertEquals(0, horizontal.move(1220, 105));
        assertEquals(0, horizontal.move(1220, 200));
        assertFalse(horizontal.isActive());
    }

    @Test
    public void onlyRightEdgeBelowTopInsetStartsVolumeControl() {
        VolumeControl middle = new VolumeControl(640, 100, 1280, 48, 48, 8, 24);
        assertEquals(0, middle.move(640, 160));
        assertFalse(middle.isActive());

        VolumeControl notification = new VolumeControl(1260, 10, 1280, 48, 48, 8, 24);
        assertEquals(0, notification.move(1260, 70));
        assertFalse(notification.isActive());

        VolumeControl outsideEdge = new VolumeControl(1231, 100, 1280, 48, 48, 8, 24);
        assertEquals(0, outsideEdge.move(1231, 160));
        assertFalse(outsideEdge.isActive());

        VolumeControl edge = new VolumeControl(1232, 48, 1280, 48, 48, 8, 24);
        assertEquals(-1, edge.move(1232, 72));
        assertTrue(edge.isActive());

        VolumeControl portrait = new VolumeControl(700, 100, 720, 48, 48, 8, 24);
        assertEquals(-1, portrait.move(700, 124));
        assertTrue(portrait.isActive());
    }

    @Test
    public void cancelledMultitouchCannotRestartAsVolumeSwipe() {
        VolumeControl swipe = new VolumeControl(1260, 100, 1280, 48, 48, 8, 24);
        swipe.cancel();
        assertEquals(0, swipe.move(1260, 160));
        assertFalse(swipe.isActive());
    }

    @Test
    public void manualAdjustmentPreventsFadeRestartUntilNewPlayback() {
        VolumeControl.FadeInState fade = new VolumeControl.FadeInState();
        assertTrue(fade.shouldFadeIn());
        fade.cancel();
        assertFalse(fade.shouldFadeIn());
        assertFalse(fade.shouldFadeIn());
        fade.reset();
        assertTrue(fade.shouldFadeIn());
    }
}
