package com.pdfreader.app;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DocumentAnalyzerTest {

    @Test
    public void findsPageEdgesAndIgnoresTextInside() {
        int w = 160;
        int h = 120;
        byte[] luma = new byte[w * h];
        fill(luma, (byte) 28);
        int left = 24, top = 22, right = 136, bottom = 98;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                luma[y * w + x] = (byte) 220;
            }
        }
        for (int y = 40; y < 88; y += 7) {
            for (int x = 36; x < 120; x++) {
                if (((x / 4) % 2) == 0) luma[y * w + x] = 30;
            }
        }

        float[] corners = DocumentAnalyzer.detectOnLuma(luma, w, h);
        assertNotNull(corners);
        assertNear(corners[0], left / (float) w, 0.06f);
        assertNear(corners[1], top / (float) h, 0.07f);
        assertNear(corners[2], (right - 1) / (float) w, 0.06f);
        assertNear(corners[3], top / (float) h, 0.07f);
        assertNear(corners[4], (right - 1) / (float) w, 0.06f);
        assertNear(corners[5], (bottom - 1) / (float) h, 0.07f);
        assertNear(corners[6], left / (float) w, 0.06f);
        assertNear(corners[7], (bottom - 1) / (float) h, 0.07f);
    }

    @Test
    public void ignoresABrightFrameAroundTheRealPage() {
        int w = 160;
        int h = 120;
        byte[] luma = new byte[w * h];
        fill(luma, (byte) 32);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (x < 3 || y < 3 || x >= w - 3 || y >= h - 3) luma[y * w + x] = (byte) 250;
            }
        }
        int left = 40, top = 28, right = 124, bottom = 96;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                luma[y * w + x] = (byte) 210;
            }
        }

        float[] corners = DocumentAnalyzer.detectOnLuma(luma, w, h);
        assertNotNull(corners);
        assertTrue(corners[0] > 0.15f);
        assertTrue(corners[1] > 0.12f);
        assertTrue(corners[2] < 0.90f);
        assertTrue(corners[5] < 0.90f);
    }

    private static void fill(byte[] luma, byte value) {
        for (int i = 0; i < luma.length; i++) luma[i] = value;
    }

    private static void assertNear(float actual, float expected, float tolerance) {
        assertTrue("expected " + expected + " but was " + actual,
                Math.abs(actual - expected) <= tolerance);
    }
}
