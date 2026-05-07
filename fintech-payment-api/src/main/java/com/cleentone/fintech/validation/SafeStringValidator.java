package com.cleentone.fintech.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SafeStringValidator implements ConstraintValidator<SafeString, String> {

    private static final String[] INVALID_STRING_PATTERN = {
        "<script", "</script", "javascript:", "onclick=", "onerror=",
        "' OR ", "\" OR ", "1=1", "--", "/*", "*/", "xp_",
        "DROP TABLE", "DELETE FROM", "INSERT INTO", "UNION SELECT"
};

    @Override
    public boolean isValid(String value,ConstraintValidatorContext context) {
        if (value == null) return true; // Consider null as valid, use @NotNull for null checks
        String upperCaseValue = value.toUpperCase();
        for (String pattern : INVALID_STRING_PATTERN) {
            if (upperCaseValue.contains(pattern.toUpperCase()))   return false;
        }
        return true;
    }

}