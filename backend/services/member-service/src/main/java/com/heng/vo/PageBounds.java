package com.heng.vo;
/** Normalize at the service boundary, including non-HTTP callers. */
public final class PageBounds {
    private PageBounds() { }
    public static long page(long value) { return Math.max(1, Math.min(value, 1000000)); }
    public static long size(long value) { return Math.max(1, Math.min(value, 50)); }
}
