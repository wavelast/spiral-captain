package com.spiralcaptain.app.model;

public record Screen(String device, int x, int y, int width, int height) {

    public int[] area() {
        return new int[] {x, y, width, height};
    }

    public boolean contains(double pointX, double pointY) {
        return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
    }
}
