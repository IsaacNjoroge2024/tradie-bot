package com.tradie.strategy.futures;

import java.util.Map;

/**
 * Utility for CME/futures month code conversions.
 *
 * <p>Standard month codes: F=Jan, G=Feb, H=Mar, J=Apr, K=May, M=Jun,
 * N=Jul, Q=Aug, U=Sep, V=Oct, X=Nov, Z=Dec.
 */
public final class FuturesMonthCode {

    private FuturesMonthCode() {}

    public static final Map<Integer, Character> MONTH_CODES = Map.ofEntries(
            Map.entry(1,  'F'), Map.entry(2,  'G'), Map.entry(3,  'H'),
            Map.entry(4,  'J'), Map.entry(5,  'K'), Map.entry(6,  'M'),
            Map.entry(7,  'N'), Map.entry(8,  'Q'), Map.entry(9,  'U'),
            Map.entry(10, 'V'), Map.entry(11, 'X'), Map.entry(12, 'Z')
    );

    private static final Map<Character, Integer> CODE_TO_MONTH = Map.ofEntries(
            Map.entry('F', 1),  Map.entry('G', 2),  Map.entry('H', 3),
            Map.entry('J', 4),  Map.entry('K', 5),  Map.entry('M', 6),
            Map.entry('N', 7),  Map.entry('Q', 8),  Map.entry('U', 9),
            Map.entry('V', 10), Map.entry('X', 11), Map.entry('Z', 12)
    );

    /**
     * Builds a futures contract symbol, e.g. {@code buildContractSymbol("ES", 3, 2025)} → {@code "ESH5"}.
     *
     * @param root  root symbol (e.g., "ES")
     * @param month calendar month (1-12)
     * @param year  four-digit year
     * @return full futures symbol
     * @throws IllegalArgumentException if month is outside 1-12
     */
    public static String buildContractSymbol(String root, int month, int year) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be 1-12, got: " + month);
        }
        char monthCode = MONTH_CODES.get(month);
        int yearDigit = year % 10;
        return root + monthCode + yearDigit;
    }

    /**
     * Returns the calendar month (1-12) for the given CME month code character.
     *
     * @throws IllegalArgumentException for unrecognised codes
     */
    public static int getMonthFromCode(char code) {
        Integer month = CODE_TO_MONTH.get(Character.toUpperCase(code));
        if (month == null) {
            throw new IllegalArgumentException("Invalid futures month code: " + code);
        }
        return month;
    }

    /**
     * Converts a YYYYMM contract month string to the two-character suffix used in the local symbol
     * (month code + last year digit). E.g., {@code "202503"} → {@code "H5"}.
     *
     * @param contractMonth in YYYYMM format
     * @throws IllegalArgumentException for malformed input
     */
    public static String toLocalSymbolSuffix(String contractMonth) {
        if (contractMonth == null || contractMonth.length() != 6) {
            throw new IllegalArgumentException("contractMonth must be in YYYYMM format, got: " + contractMonth);
        }
        int year  = Integer.parseInt(contractMonth.substring(0, 4));
        int month = Integer.parseInt(contractMonth.substring(4, 6));
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month in contractMonth: " + contractMonth);
        }
        char code = MONTH_CODES.get(month);
        return "" + code + (year % 10);
    }
}
