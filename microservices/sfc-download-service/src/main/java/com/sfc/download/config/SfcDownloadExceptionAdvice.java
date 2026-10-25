package com.sfc.download.config;

import com.sfc.common.exception.SfcErrorInfo;
import com.sfc.common.exception.SfcJsonException;
import com.sfc.common.model.json.SfcJsonResult;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 离线下载微服务的全局异常处理器。
 *
 * <p>语义与 {@code sfc-ai-service} 的 {@code SfcAiExceptionAdvice} 及宿主全局异常处理保持一致：</p>
 * <ul>
 *   <li>业务异常 {@link SfcJsonException}：按异常自带错误信息里的状态码设置 HTTP 状态，并返回响应信封；</li>
 *   <li>参数校验失败（请求体 / 请求参数 / 路径变量）：HTTP 422 + 汇总后的错误提示；</li>
 *   <li>其余未预期异常：HTTP 500 + 响应信封（不返回堆栈，但完整记录日志）。</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class SfcDownloadExceptionAdvice {

    /** 日志前缀，与宿主 {@code ControllerAdvice} 保持一致，便于日志检索 */
    private static final String LOG_PREFIX = "[GlobalException]";

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
