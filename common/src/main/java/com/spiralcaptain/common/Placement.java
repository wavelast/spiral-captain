package com.spiralcaptain.common;

public record Placement(int x, int y, int width, int height, boolean anchorRight,
        boolean anchorBottom, boolean widescreen, int minWidth, int minHeight) {

    public static final int WIDE_X = 16;
    public static final int WIDE_Y = 9;

    public int[] content(int left, int top, int right, int bottom) {
        int contentWidth = Math.max(1, width - left - right);
        int contentHeight = Math.max(1, height - top - bottom);
        if (widescreen) {
            if ((long) contentWidth * WIDE_Y > (long) contentHeight * WIDE_X) {
                contentWidth = contentHeight * WIDE_X / WIDE_Y;
            } else {
                contentHeight = contentWidth * WIDE_Y / WIDE_X;
            }
        }
        if (contentWidth < minWidth || contentHeight < minHeight) {
            contentWidth = Math.max(contentWidth, minWidth);
            contentHeight = Math.max(contentHeight, minHeight);
            if (widescreen) {
                contentWidth = Math.max(contentWidth,
                        (contentHeight * WIDE_X + WIDE_Y - 1) / WIDE_Y);
                contentHeight = Math.max(contentHeight,
                        (contentWidth * WIDE_Y + WIDE_X - 1) / WIDE_X);
            }
        }
        int outerWidth = contentWidth + left + right;
        int outerHeight = contentHeight + top + bottom;
        int outerX = anchorRight ? x + width - outerWidth : x;
        int outerY = anchorBottom ? y + height - outerHeight : y;
        return new int[] {outerX + left, outerY + top, contentWidth, contentHeight};
    }

    public Placement grownBy(int left, int top, int right, int bottom) {
        return new Placement(x - left, y - top, width + left + right, height + top + bottom,
                anchorRight, anchorBottom, widescreen, minWidth, minHeight);
    }
}
