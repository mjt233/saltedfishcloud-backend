package com.sfc.cloud.data.validation;

import com.sfc.cloud.constant.SfcCommonError;
import com.sfc.cloud.constant.SfcUserConstants;
import com.sfc.cloud.data.identity.CurrentUserHolder;
import com.sfc.cloud.exception.SfcJsonException;
import com.sfc.cloud.utils.SfcTypeUtils;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * uid 资源操作权限校验器。
 *
 * <p>迁移自宿主的 {@code com.xiaotao.saltedfishcloud.validator.UIDValidator}，
 * 但把"当前用户是谁"这一宿主相关能力抽成了 SPI
 * （{@link com.sfc.cloud.data.identity.CurrentUserProvider}），
 * 因此宿主与微服务两侧可复用同一套判定口径。</p>
 *
 * <p><b>权限语义</b>（与宿主保持一致）：</p>
 * <ul>
 *   <li>一般用户只能操作<b>自己</b>的资源与<b>公共</b>资源（uid = 0）；</li>
 *   <li>管理员可操作所有资源；</li>
 *   <li>当 {@code publicOnlyAdmin} 为 {@code true} 时，公共资源也仅管理员可写。</li>
 * </ul>
 *
 * <p><b>fail-closed</b>：未登录（uid 为 null）或未注册身份 provider 时一律判定不通过。</p>
 */
public class SfcUIDValidator implements ConstraintValidator<SfcUID, Object> {

    /** 当前约束注解实例，由 Bean Validation 通过 {@link #initialize(SfcUID)} 注入 */
    private SfcUID validUID;

    /**
     * 校验当前登录用户是否具有操作某个 uid 资源的权限。
     *
     * <p>一般用户只具备操作自己和公共资源的权限。当前用户身份取自身份 SPI。</p>
     *
     * @param uid             请求的资源所属用户 ID
     * @param publicOnlyAdmin 公共资源（uid = 0）是否只允许管理员通过校验
     * @return 通过返回 {@code true}，否则返回 {@code false}
     */
    public static boolean validate(long uid, boolean publicOnlyAdmin) {
        return validate(CurrentUserHolder.getCurrentUid(), CurrentUserHolder.currentIsAdmin(), uid, publicOnlyAdmin);
    }

    /**
     * 校验当前登录用户是否具有操作某个 uid 资源的权限，不满足则抛出异常。
     *
     * @param uid             请求的资源所属用户 ID
     * @param publicOnlyAdmin 公共资源（uid = 0）是否只允许管理员通过校验
     * @throws SfcJsonException 当校验不通过时抛出（错误码 {@link SfcCommonError#SYSTEM_FORBIDDEN}）
     * @see SfcCommonError#SYSTEM_FORBIDDEN
     */
    public static void validateWithException(long uid, boolean publicOnlyAdmin) {
        if (!validate(uid, publicOnlyAdmin)) {
            throw new SfcJsonException(SfcCommonError.SYSTEM_FORBIDDEN);
        }
    }

    /**
     * 以显式给定的"当前用户身份"校验是否具有操作某个 uid 资源的权限。
     *
     * <p>供调用方已经自行取得用户对象（而非依赖请求上下文）的场景使用，
     * 例如持有自定义认证主体、或在后台线程中处理的场景。</p>
     *
     * @param currentUid      当前用户 uid；未登录传 {@code null}
     * @param currentIsAdmin  当前用户是否管理员
     * @param uid             请求的资源所属用户 ID
     * @param publicOnlyAdmin 公共资源（uid = 0）是否只允许管理员通过校验
     * @return 通过返回 {@code true}，否则返回 {@code false}
     */
    public static boolean validate(Long currentUid, boolean currentIsAdmin, long uid, boolean publicOnlyAdmin) {
        // 非公共资源或不限制管理员时，公共资源对所有（含匿名）放行
        if (!publicOnlyAdmin && uid == SfcUserConstants.PUBLIC_USER_ID) {
            return true;
        }
        if (currentUid == null) {
            return false;
        }

        // 非本人资源且非管理员 → 拒绝；公共资源要求管理员但当前不是管理员 → 拒绝
        return (currentUid == uid || currentIsAdmin)
                && !(publicOnlyAdmin && uid == SfcUserConstants.PUBLIC_USER_ID && !currentIsAdmin);
    }

    /**
     * Bean Validation 回调：校验被 {@link SfcUID} 标注的字段或参数。
     *
     * @param value   被校验的值，必须是数字类型
     * @param context 校验上下文
     * @return 通过返回 {@code true}，否则返回 {@code false}
     * @throws IllegalArgumentException 当被校验值不是数字类型时抛出
     */
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value instanceof Number) {
            return validate(SfcTypeUtils.toNumber(Long.class, value), validUID.value());
        }
        throw new IllegalArgumentException("UID验证字段类型错误：" + value.getClass().getName());
    }

    /**
     * Bean Validation 回调：注入注解实例，用于读取 {@link SfcUID#value()}。
     *
     * @param constraintAnnotation 当前约束注解实例
     */
    @Override
    public void initialize(SfcUID constraintAnnotation) {
        validUID = constraintAnnotation;
    }
}
