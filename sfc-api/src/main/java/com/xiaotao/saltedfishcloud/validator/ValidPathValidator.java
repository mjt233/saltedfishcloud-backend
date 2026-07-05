package com.xiaotao.saltedfishcloud.validator;


import com.xiaotao.saltedfishcloud.constant.error.FileSystemError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.validator.annotations.ValidPath;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class ValidPathValidator implements ConstraintValidator<ValidPath, Object> {
    private final static Pattern pattern = Pattern.compile(RejectRegex.PATH);
    private final static Pattern pattern2 = Pattern.compile(RejectRegex.PATH_NODE);

    /**
     * 校验路径是否合法
     * @param input 待校验路径
     * @return  合法返回true，不合法返回false
     */
    public static boolean isValid(CharSequence input) {
        return !(pattern.matcher(input).find() || pattern2.matcher(input).find());
    }

    /**
     * 校验路径是否合法，不合法的路径直接抛出异常
     * @param input 待校验路径
     */
    public static void valid(CharSequence input) {
        if (!isValid(input)) {
            throw new JsonException(FileSystemError.INVALID_PATH);
        }
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value instanceof CharSequence charSeq) {
            return isValid(charSeq);
        } else {
            throw new IllegalArgumentException("该验证注解只能使用在CharSequence的实现类字段上，错误的字段类型：" + value.getClass().getName());
        }
    }

    @Override
    public void initialize(ValidPath constraintAnnotation) {

    }
}
