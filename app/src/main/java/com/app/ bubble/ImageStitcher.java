package com.app.bubble;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import java.util.List; 

public class ImageStitcher {

    /**
     * Stitches a list of bitmaps vertically, attempting to remove overlaps caused by scrolling.
     */
    public static Bitmap stitchImages(List<Bitmap> bitmaps) {
        if (bitmaps == null || bitmaps.isEmpty()) {
            return null;
        }
        if (bitmaps.size() == 1) {
            return bitmaps.get(0);
        }

        Bitmap result = bitmaps.get(0);

        for (int i = 1; i < bitmaps.size(); i++) {
            Bitmap nextBitmap = bitmaps.get(i);
            if (nextBitmap != null) {
                result = mergeTwoImages(result, nextBitmap);
            }
        }

        return result;
    }

    private static Bitmap mergeTwoImages(Bitmap top, Bitmap bottom) {
        // Find how many pixels at the bottom of 'top' match the top of 'bottom'
        int overlap = findVerticalOverlap(top, bottom);

        int width = Math.min(top.getWidth(), bottom.getWidth());
        // The new height is the sum of both minus the duplicate overlap part
        int height = top.getHeight() + bottom.getHeight() - overlap;

        // Limit height to avoid crashes (texture size limit usually 4096 or 8192)
        // If it gets too long, we stop growing to prevent crash.
        if (height > 8000) height = 8000;

        try {
            Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result);

            // Draw the top image
            canvas.drawBitmap(top, 0, 0, null);

            // Draw the bottom image, shifted up by the overlap amount so visuals align
            // relative to the bottom of the top image.
            // Formula: Y = top.getHeight() - overlap
            canvas.drawBitmap(bottom, 0, top.getHeight() - overlap, null);

            // Recycle old bitmaps to free memory.
            // IMPORTANT: Only recycle 'top' if it was an intermediate result (not the original first frame).
            // For safety in this context, we rely on the GC or caller to clean up originals, 
            // but explicit recycling here helps significantly with "Burst Mode".
            // top.recycle(); 
            // bottom.recycle();

            return result;
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            return top; // Fallback to just the top image if we run out of RAM
        }
    }

    /**
     * Scans for visual overlap between two bitmaps.
     * Returns the height (in pixels) of the overlapping region.
     */
    private static int findVerticalOverlap(Bitmap top, Bitmap bottom) {
        int width = Math.min(top.getWidth(), bottom.getWidth());
        int topHeight = top.getHeight();
        int bottomHeight = bottom.getHeight();

        // We assume the scroll isn't huge (e.g. not more than 50% of screen at a time)
        // We look at the bottom 30% of the top image
        int searchHeight = topHeight / 3; 
        
        // Safety check
        if (searchHeight > bottomHeight) searchHeight = bottomHeight;

        // Define a "signature" row from the bottom of the top image.
        // CHANGED: Instead of 10px from bottom, use 150px from bottom.
        // This avoids bottom navigation bars or static footers interfering with the match.
        int offsetFromBottom = 150;
        if (topHeight < 300) offsetFromBottom = 20; // fallback for small images

        int referenceRowY = topHeight - offsetFromBottom;
        int[] referencePixels = new int[width];
        top.getPixels(referencePixels, 0, width, 0, referenceRowY, width, 1);

        // Scan the top part of the bottom image to find this row
        int[] comparePixels = new int[width];
        
        for (int y = 0; y < searchHeight; y++) {
            bottom.getPixels(comparePixels, 0, width, 0, y, width, 1);

            if (arraysSimilar(referencePixels, comparePixels)) {
                // Match found!
                // The overlap is: (distance from ref row to bottom of top img) + (distance from top of bottom img to match)
                // Overlap = offsetFromBottom + y
                return offsetFromBottom + y;
            }
        }

        // No overlap found (moved too fast or completely different content)
        return 0;
    }

    /**
     * Compares two rows of pixels. Returns true if they are mostly similar.
     * We use a threshold because compression/rendering artifacts might make pixels slightly different.
     */
    private static boolean arraysSimilar(int[] row1, int[] row2) {
        int matches = 0;
        int totalChecked = 0;
        
        // Check every 5th pixel to save CPU
        for (int i = 0; i < row1.length; i += 5) {
            totalChecked++;
            if (row1[i] == row2[i]) {
                matches++;
            }
        }

        // CHANGED: Increased strictness from 0.8 (80%) to 0.9 (90%)
        // This ensures we don't accidentally stitch lines that just happen to have similar whitespace.
        return matches > (totalChecked * 0.9);
    }
}