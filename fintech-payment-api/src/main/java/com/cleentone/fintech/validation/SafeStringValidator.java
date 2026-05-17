package com.cleentone.fintech.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * A basic security validator to protect against common injection attacks.
 * It checks input strings for suspicious patterns like script tags or 
 * common SQL injection fragments.
 */
public class SafeStringValidator implements ConstraintValidator<SafeString, String> {

    // A list of patterns that we consider dangerous.
    private static final String[] INVALID_STRING_PATTERN = {
        "<script", "</script", "javascript:", "onclick=", "onerror=",
        "' OR ", "\" OR ", "1=1", "--", "/*", "*/", "xp_",
        "DROP TABLE", "DELETE FROM", "INSERT INTO", "UNION SELECT"
    };

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // If it's null, we're okay with it (use @NotNull if you want to block nulls).
        if (value == null) return true;
        
        String upperCaseValue = value.toUpperCase();
        
        // We scan the input for any of our "forbidden" patterns.
        for (String pattern : INVALID_STRING_PATTERN) {
            if (upperCaseValue.contains(pattern.toUpperCase())) {
                return false;
            }
        }
        
        return true;
    }
}