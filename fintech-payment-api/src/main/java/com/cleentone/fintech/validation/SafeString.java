package com.cleentone.fintech.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Custom annotation to flag a field for security validation.
 * It uses the SafeStringValidator to check for XSS and SQL injection patterns.
 */
@Documented
@Constraint(validatedBy = SafeStringValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeString {

    String message() default "Input contains invalid or suspicious characters";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
