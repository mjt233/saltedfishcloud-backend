package com.xiaotao.saltedfishcloud.validator;

import com.xiaotao.saltedfishcloud.constant.UserConstants;
import com.xiaotao.saltedfishcloud.constant.error.CommonError;
import com.xiaotao.saltedfishcloud.exception.JsonException;
import com.xiaotao.saltedfishcloud.model.po.UserPrincipal;
import com.xiaotao.saltedfishcloud.service.permission.UidAccessLevel;
import com.xiaotao.saltedfishcloud.service.permission.UidAccessResolver;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import com.xiaotao.saltedfishcloud.utils.TypeUtils;
import com.xiaotao.saltedfishcloud.validator.annotations.UID;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

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
        UserPrincipal springSecurityUser = SecureUtils.getSpringSecurityUser();
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
     * 一般用户只具备操作自己和公共资源的权限；经 {@link UidAccessResolver}（如团队空间插件）
     * 提供访问级别时，按级别放行：读校验（publicOnlyAdmin=false）任意级别通过，
     * 写校验（publicOnlyAdmin=true）要求至少 {@link UidAccessLevel#WRITE}。
     * @param uid 请求的资源所属用户ID
     * @param publicOnlyAdmin 公共资源是否只允许管理员通过验证
     * @return 通过true 否则false
     */
    public static boolean validate(UserPrincipal user, long uid, boolean publicOnlyAdmin) {
        if (!publicOnlyAdmin && uid == UserConstants.PUBLIC_USER_ID) return true;
        if (user == null) {
            return false;
        }

        boolean isAdmin = user.isAdmin();
        if (user.getId() != null && user.getId() == uid) {
            // 本人操作本人资源：公共资源写仅管理员规则除外
            return isAdmin || !(publicOnlyAdmin && uid == UserConstants.PUBLIC_USER_ID);
        }
        if (isAdmin) {
            return true;
        }

        // 非本人且非管理员：咨询 UID 访问权提供者（团队空间等），无提供者时保持原有拒绝行为
        UidAccessResolver resolver = UidAccessResolver.getInstance();
        if (resolver != null) {
            UidAccessLevel level = resolver.getAccessLevel(user, uid);
            if (level != null && level != UidAccessLevel.NONE) {
                if (publicOnlyAdmin) {
                    return level.getOrder() >= UidAccessLevel.WRITE.getOrder();
                } else {
                    return true;
                }
            }
        }
        // 未命中任何提供者：非本人、非管理员、非公共资源读场景一律拒绝
        return false;
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
