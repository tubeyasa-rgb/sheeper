package com.ar5as.mashwar;
public final class PriceTest {
    public static void main(String[] args) {
        check("١٢٣٫٤٥", 123.45); check("۱۲۳٫۴۵", 123.45); check("1,234.50", 1234.50); check("١٬٢٣٤٫٥٠",1234.5);
        try { Prices.parsePrice("abc"); throw new AssertionError("accepted invalid value"); } catch (NumberFormatException expected) { }
        System.out.println("Price normalization: 5 checks passed");
    }
    private static void check(String input, double expected) { if (Prices.parsePrice(input) != expected) throw new AssertionError(input); }
}
