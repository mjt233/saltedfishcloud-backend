package com.sfc.download.config;

import com.sfc.common.exception.SfcErrorInfo;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.json.SfcJsonResult;
import com.sfc.common.utils.SfcStringUtils;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 离线下载微服务的全局异常处理器。
 *
 * <p>语义与 {@code sfc-ai-service} 的 {@code SfcAiExceptionAdvice} 及宿主全局异常处理保持一致：</p>
 */
@Slf4j
@RestControllerAdvice
public class SfcDownloadExceptionAdvice {

    /** 日志前缀，与宿主 {@code ControllerAdvice} 保持一致，便于日志检索 */
    private static final String LOG_PREFIX = "[GlobalException]";

    /**
     * 提取异常链最内层消息时允许的最大深度。
     *
     * <p>用于防御异常 cause 自引用或环状引用导致的死循环。</p>
     */
    private static final int MAX_CAUSE_DEPTH = 16;

    /**
     * 处理业务异常：按错误信息里的状态码设置 HTTP 状态，并返回响应信封。
     *
     * @param e        业务异常
     * @param response HTTP 响应（用于设置状态码）
     * @return 业务异常自带的响应信封
     */
    @ExceptionHandler(SfcJsonException.class)
    @SuppressWarnings("unchecked")
    public SfcJsonResult<Object> handleSfcJsonException(SfcJsonException e, HttpServletResponse response) {
        log.error("{}错误：", LOG_PREFIX, e);
        SfcErrorInfo errorInfo = e.getErrorInfo();
        if (errorInfo != null) {
            response.setStatus(errorInfo.getStatus());
        }
        return e.getRes();
    }

    /**
     * 处理客户端入参非法：HTTP 400。
     *
     * <p>典型来源是下载引擎在创建期对下载来源、类型参数做的合法性校验（例如非法的下载类型编码、
     * 不受支持的 Git 协议、非法的磁力链）。这类异常由服务端主动抛出，语义上是「调用方输入有误」，
     * 因此只记 WARN、不打印堆栈。</p>
     *
     * @param e        非法参数异常
     * @param response HTTP 响应
     * @return 400 响应信封
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public SfcJsonResult<String> handleIllegalArgumentException(IllegalArgumentException e,
                                                                HttpServletResponse response) {
        String message = SfcStringUtils.hasText(e.getMessage())
                ? e.getMessage()
                : e.getClass().getSimpleName();
        log.warn("{}客户端参数错误：{}", LOG_PREFIX, message);
        response.setStatus(400);
        return SfcJsonResult.getInstance(400, null, message);
    }

    /**
     * 处理请求体反序列化失败：HTTP 400。
     *
     * <p>最常见的场景是请求体里的枚举字段（如 {@code type}）传了无法识别的取值，Jackson 会抛出
     * 反序列化异常并被 Spring 包装为本异常。消息取异常链<b>最内层</b>的描述，它对调用方最有意义
     * （例如 {@code Cannot deserialize value of type `DownloadType` from String "FOO"}）。</p>
     *
     * @param e        请求体不可读异常
     * @param response HTTP 响应
     * @return 400 响应信封
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public SfcJsonResult<String> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
                                                              HttpServletResponse response) {
        String cause = extractDeepestCauseMessage(e);
        String message = SfcStringUtils.hasText(cause) ? "请求体格式错误: " + cause : "请求体格式错误";
        log.warn("{}请求体解析失败：{}", LOG_PREFIX, message);
        response.setStatus(400);
        return SfcJsonResult.getInstance(400, null, message);
    }

    /**
     * 处理查询参数类型不匹配：HTTP 400。
     *
     * <p>例如列表接口的 {@code type} 查询参数传了未定义的枚举值（{@code ?type=FOO}）。</p>
     *
     * @param e        参数类型不匹配异常
     * @param response HTTP 响应
     * @return 400 响应信封
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public SfcJsonResult<String> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e,
                                                                  HttpServletResponse response) {
        String message = "参数 " + e.getName() + " 的取值非法: " + e.getValue();
        log.warn("{}参数类型不匹配：{}", LOG_PREFIX, message);
        response.setStatus(400);
        return SfcJsonResult.getInstance(400, null, message);
    }

    /**
     * 处理参数校验失败：HTTP 422 + 汇总后的错误提示。
     *
     * @param e        校验异常
     * @param response HTTP 响应
     * @return 422 响应信封
     */
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class
    })
    public SfcJsonResult<String> handleValidationException(Exception e, HttpServletResponse response) {
        log.debug("{}参数校验失败：", LOG_PREFIX, e);
        response.setStatus(422);
        return SfcJsonResult.getInstance(422, null, describeValidationFailure(e));
    }

    /**
     * 兜底处理未预期异常：HTTP 500 + 响应信封。
     *
     * <p>不把堆栈返回给客户端，但会记录完整异常；消息中包含异常类名便于排查。</p>
     *
     * @param e        任意异常
     * @param response HTTP 响应
     * @return 500 响应信封
     */
    @ExceptionHandler(Exception.class)
    public SfcJsonResult<String> handleUnexpectedException(Exception e, HttpServletResponse response) {
        log.error("{}未预期异常：", LOG_PREFIX, e);
        response.setStatus(500);
        return SfcJsonResult.getInstance(500, null, e.getClass().getCanonicalName() + " " + e.getMessage());
    }

    /**
     * 提取异常链中最内层的非空消息。
     *
     * <p>外层包装异常（如 {@code HttpMessageNotReadableException}）的消息通常只描述「读取失败」，
     * 真正对调用方有用的原因在 cause 链末端。遍历过程带深度上限与自引用保护。</p>
     *
     * @param e 顶层异常，允许为 {@code null}
     * @return 最内层非空消息；整条异常链都没有消息时返回 {@code null}
     */
    private String extractDeepestCauseMessage(Throwable e) {
        String message = null;
        Throwable current = e;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (SfcStringUtils.hasText(current.getMessage())) {
                message = current.getMessage();
            }
            Throwable cause = current.getCause();
            if (cause == null || cause == current) {
                break;
            }
            current = cause;
        }
        return message;
    }

    /**
     * 汇总校验失败的提示文本。
     *
     * @param e 校验异常
     * @return 可读的失败原因
     */
    private String describeValidationFailure(Exception e) {
        if (e instanceof MethodArgumentNotValidException manve) {
            return manve.getBindingResult().getFieldErrors().stream()
                    .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        }
        if (e instanceof BindException be) {
            return be.getBindingResult().getFieldErrors().stream()
                    .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        }
        if (e instanceof ConstraintViolationException cve) {
            return cve.getConstraintViolations().stream()
                    .map(this::describeViolation)
                    .collect(Collectors.joining("; "));
        }
        return e.getMessage();
    }

    /**
     * 描述单条约束违规。
     *
     * @param violation 约束违规
     * @return 形如 {@code 参数: 提示} 的文本
     */
    private String describeViolation(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() + ": " + violation.getMessage();
    }
}
