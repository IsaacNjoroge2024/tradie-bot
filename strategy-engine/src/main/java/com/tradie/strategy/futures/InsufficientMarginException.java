package com.tradie.strategy.futures;

public class InsufficientMarginException extends RuntimeException {

    public InsufficientMarginException(String message) {
        super(message);
    }
}
