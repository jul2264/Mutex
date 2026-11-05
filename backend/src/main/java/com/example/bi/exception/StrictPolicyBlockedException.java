package com.example.bi.exception;

public class StrictPolicyBlockedException extends RuntimeException {
    public StrictPolicyBlockedException(String message) {
        super(message);
    }
}

