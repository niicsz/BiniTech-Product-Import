package com.binitech.imports.domain;

public record ColumnMapping(String sourceColumn, ImportField targetField, double confidence) {}
