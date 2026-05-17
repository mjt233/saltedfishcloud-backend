package com.xiaotao.saltedfishcloud.validator;


import com.xiaotao.saltedfishcloud.validator.annotations.ValidPath;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class ValidPathValidator implements ConstraintValidator<ValidPath, Object> {
    private final static Pattern pattern = Pattern.compile(RejectRegex.PATH);
    private final static Pattern pattern2 = Pattern.compile(RejectRegex.PATH_NODE);
    public static boolean valid(CharSequence input) {
        return !(pattern.matcher(input).find() || pattern2.matcher(input).find());
    }
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value instanceof CharSequence charSeq) {
            return valid(charSeq);
        } else {
            throw new IllegalArgumentException("该验证注解只能使用在CharSequence的实现类字段上，错误的字段类型：" + value.getClass().getName());
        }
    }

    @Override
    public void initialize(ValidPath constraintAnnotation) {

    }
}
