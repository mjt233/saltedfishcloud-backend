package com.xiaotao.saltedfishcloud.validator;

import com.xiaotao.saltedfishcloud.po.User;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class UIDValidator implements ConstraintValidator<UID, Object> {
    UID validUID;

    /**
     * 验证当前已登录用户是否具有操作某个UID资源的权限。 <br>
     * 一般用户只具备操作自己和公共资源的权限
     * @param uid 用户ID
     * @param publicOnlyAdmin 公共资源是否只允许管理员通过验证
     */
    static public boolean validate(int uid, boolean publicOnlyAdmin) {
        if (!publicOnlyAdmin && uid == 0) return true;
        User springSecurityUser = SecureUtils.getSpringSecurityUser();
        if (springSecurityUser == null) {
            return false;
        }

        boolean isAdmin = springSecurityUser.getType() == User.TYPE_ADMIN;
        if ( (springSecurityUser.getId() != uid && !isAdmin) ||
                (publicOnlyAdmin && uid == 0 && !isAdmin) ) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value instanceof Integer) {
            return validate((Integer)value, validUID.value());
        } else {
            throw new IllegalArgumentException("UID验证字段类型错误：" + value.getClass().getName());
        }
    }

    @Override
    public void initialize(UID constraintAnnotation) {
        validUID = constraintAnnotation;
    }
}
