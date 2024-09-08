package com.xiaotao.saltedfishcloud.validator;

import com.sfc.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.User;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class UIDValidator implements ConstraintValidator<UID, Object> {
    UID validUID;

    /**
     * 验证当前已登录用户是否具有操作某个UID资源的权限。 <br>
     * 一般用户只具备操作自己和公共资源的权限
     * @param uid 用户ID
     * @param publicOnlyAdmin 公共资源是否只允许管理员通过验证
     * @return 通过true 否则false
     */
    public static boolean validate(long uid, boolean publicOnlyAdmin) {
        User springSecurityUser = SecureUtils.getSpringSecurityUser();
        return validate(springSecurityUser, uid, publicOnlyAdmin);
    }

    /**
     * 验证当前已登录用户是否具有操作某个UID资源的权限。 <br>
     * 一般用户只具备操作自己和公共资源的权限。若不满足操作权限则抛出异常。
     * @param uid 用户ID
     * @param publicOnlyAdmin 公共资源是否只允许管理员通过验证
     * @see CommonError#SYSTEM_FORBIDDEN
     */
    public static void validateWithException(long uid, boolean publicOnlyAdmin) {
        if (!validate(uid, publicOnlyAdmin)) {
            throw new JsonException(CommonError.SYSTEM_FORBIDDEN);
        }
    }

    /**
     * 验证当前已登录用户是否具有操作某个UID资源的权限。 <br>
     * 一般用户只具备操作自己和公共资源的权限
     * @param uid 请求的资源所属用户ID
     * @param publicOnlyAdmin 公共资源是否只允许管理员通过验证
     * @return 通过true 否则false
     */
    public static boolean validate(User user, long uid, boolean publicOnlyAdmin) {
        if (!publicOnlyAdmin && uid == 0) return true;
        if (user == null) {
            return false;
        }

        boolean isAdmin = user.getType() == User.TYPE_ADMIN;
        if ( (user.getId() != uid && !isAdmin) ||
                (publicOnlyAdmin && uid == 0 && !isAdmin) ) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value instanceof Number) {
            return validate(TypeUtils.toNumber(Long.class, value), validUID.value());
        } else {
            throw new IllegalArgumentException("UID验证字段类型错误：" + value.getClass().getName());
        }
    }

    @Override
    public void initialize(UID constraintAnnotation) {
        validUID = constraintAnnotation;
    }
}
