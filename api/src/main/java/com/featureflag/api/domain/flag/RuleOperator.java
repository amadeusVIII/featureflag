package com.featureflag.api.domain.flag;

public enum RuleOperator {
    IN,        // userId is IN ["user-1", "user-2"]
    NOT_IN,    // userId is NOT in the list
    EQUALS,    // attribute equals exactly one value
    CONTAINS
}
