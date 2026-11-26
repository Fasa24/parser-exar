package com.app.model;

public record ParseResult (
        int id,
        int lineNumber,
        String expression,
        String code,
        String message
) {}
