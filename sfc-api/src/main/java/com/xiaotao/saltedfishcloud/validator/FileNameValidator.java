package com.xiaotao.saltedfishcloud.validator;

import com.xiaotao.saltedfishcloud.validator.annotations.FileName;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.List;
import java.util.regex.Pattern;

public class FileNameValidator implements ConstraintValidator<FileName, Object> {
    private final static Pattern pattern = Pattern.compile(RejectRegex.FILE_NAME);
    @Override
    public void initialize(FileName constraintAnnotation) {
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value instanceof List && ((List<Object>)value).get(0) instanceof CharSequence ) {
            for(CharSequence name : (List<CharSequence>)value) {
                if (!valid(name)) {
                    return false;
                }
            }
        } else if (value instanceof CharSequence) {
            return valid((CharSequence)value);
        }
        return true;
    }

    /**
     * 判断文件名是否合法，合法返回true，否则返回false
     * @see RejectRegex#FILE_NAME
     * @param input 文件名
     */
    public static boolean valid(CharSequence input) {
        return !pattern.matcher(input).find();
    }
}
