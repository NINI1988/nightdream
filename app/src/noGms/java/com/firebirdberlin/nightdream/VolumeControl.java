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

public final class VolumeControl {
    private final float startX;
    private final float startY;
    private final float screenWidth;
    private final float edgeWidth;
    private final float topInset;
    private final float touchSlop;
    private final float stepSize;
    private float previousY;
    private boolean directionChosen;
    private boolean active;

    public VolumeControl(float startX, float startY, float screenWidth, float edgeWidth,
                         float topInset, float touchSlop, float stepSize) {
        this.startX = startX;
        this.startY = startY;
        this.screenWidth = screenWidth;
        this.edgeWidth = edgeWidth;
        this.topInset = topInset;
        this.touchSlop = touchSlop;
        this.stepSize = stepSize;
        previousY = startY;
    }

    public int move(float x, float y) {
        float dx = x - startX;
        float dy = y - startY;
        if (!directionChosen) {
            if (Math.max(Math.abs(dx), Math.abs(dy)) < touchSlop) return 0;
            directionChosen = true;
            active = startX >= screenWidth - edgeWidth && startY >= topInset
                    && Math.abs(dy) > Math.abs(dx);
        }
        if (!active) return 0;
        int steps = swipeSteps(previousY, y, stepSize);
        previousY -= steps * stepSize;
        return steps;
    }

    public boolean isActive() {
        return active;
    }

    public void cancel() {
        directionChosen = true;
        active = false;
    }

    public static int swipeSteps(float previousY, float currentY, float stepSize) {
        return (int) ((previousY - currentY) / stepSize);
    }

    public static int effectiveVolume(int streamVolume, float playerVolume) {
        return Math.round(streamVolume * playerVolume);
    }

    public static final class FadeInState {
        private boolean cancelled;

        public void reset() {
            cancelled = false;
        }

        public void cancel() {
            cancelled = true;
        }

        public boolean shouldFadeIn() {
            return !cancelled;
        }
    }
}
