package com.ar5as.mashwar;

final class Prices {
    static double parsePrice(String input) {
        StringBuilder normalized = new StringBuilder();
        for (char c : input.trim().toCharArray()) {
            int digit = Character.digit(c, 10);
            if (digit >= 0) normalized.append(digit);
            else if (c == '.' || c == '٫') normalized.append('.');
            else if (c != ',' && c != '٬' && !Character.isWhitespace(c)) throw new NumberFormatException();
        }
        return Double.parseDouble(normalized.toString());
    }
}
