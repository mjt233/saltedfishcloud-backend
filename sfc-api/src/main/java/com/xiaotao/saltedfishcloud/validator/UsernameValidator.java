package com.xiaotao.saltedfishcloud.validator;

import com.xiaotao.saltedfishcloud.validator.annotations.Username;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class UsernameValidator implements ConstraintValidator<Username, CharSequence> {
    private final static Pattern PATTERN = Pattern.compile("[:|*?!^&$`#@%;\\[\\]{}<>\\\\/]");

    public static boolean validate(CharSequence username) {
        return !PATTERN.matcher(username).find();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        return validate(value);
    }
}
