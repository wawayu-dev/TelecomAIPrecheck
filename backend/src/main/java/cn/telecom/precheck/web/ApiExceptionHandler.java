package cn.telecom.precheck.web;

import cn.telecom.precheck.context.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    public record ApiError(String code, String message, String traceId) {}

    @ExceptionHandler(TicketNotFoundException.class)
    ResponseEntity<ApiError> notFound(HttpServletRequest request) {
        return error(404, "TICKET_NOT_FOUND", "未找到工单", request);
    }
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> invalid(HttpServletRequest request) {
        return error(400, "INVALID_REQUEST", "请求参数格式不合法", request);
    }
    @ExceptionHandler(ContextUnavailableException.class)
    ResponseEntity<ApiError> unavailable(HttpServletRequest request) {
        return error(503, "CONTEXT_UNAVAILABLE", "宿主工单上下文暂不可用", request);
    }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> routeNotFound(HttpServletRequest request) {
        return error(404, "RESOURCE_NOT_FOUND", "接口不存在", request);
    }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> methodNotAllowed(HttpServletRequest request) {
        return error(405, "METHOD_NOT_ALLOWED", "请求方法不支持", request);
    }
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> invalidType(HttpServletRequest request) {
        return error(400, "INVALID_REQUEST", "请求参数类型不合法", request);
    }
    @ExceptionHandler(cn.telecom.precheck.precheck.PrecheckNotFoundException.class)
    ResponseEntity<ApiError> precheckNotFound(HttpServletRequest request) {
        return error(404, "PRECHECK_NOT_FOUND", "未找到预审记录", request);
    }
    @ExceptionHandler(cn.telecom.precheck.review.ReviewConflictException.class)
    ResponseEntity<ApiError> conflict(HttpServletRequest request) {
        return error(409, "REVIEW_CONFLICT", "状态或版本已变化，请刷新后重试", request);
    }
    @ExceptionHandler(cn.telecom.precheck.precheck.EventConflictException.class)
    ResponseEntity<ApiError> eventConflict(HttpServletRequest request) {
        return error(409, "EVENT_CONFLICT", "事件标识已用于其他工单版本", request);
    }
    @ExceptionHandler(cn.telecom.precheck.precheck.PrecheckRetryConflictException.class)
    ResponseEntity<ApiError> retryConflict(HttpServletRequest request) {
        return error(409, "PRECHECK_RETRY_CONFLICT", "任务状态或版本已变化，请刷新后重试", request);
    }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<ApiError> denied(HttpServletRequest request) {
        return error(403, "FORBIDDEN", "当前身份无操作权限", request);
    }
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        jakarta.validation.ConstraintViolationException.class})
    ResponseEntity<ApiError> invalidBody(HttpServletRequest request) {
        return error(400, "INVALID_REQUEST", "请求字段不合法", request);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        // 不记录异常原文/堆栈，以免宿主异常携带投诉、SQL 或密钥。
        LOG.error("Unhandled request failure type={} traceId={}",
                exception.getClass().getSimpleName(), request.getAttribute("traceId"));
        return error(500, "INTERNAL_ERROR", "服务暂不可用，请稍后重试", request);
    }
    private ResponseEntity<ApiError> error(int status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(code, message, (String) request.getAttribute("traceId")));
    }
}
