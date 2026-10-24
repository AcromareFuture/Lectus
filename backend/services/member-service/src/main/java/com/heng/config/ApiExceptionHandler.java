package com.heng.config;

import com.heng.controller.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiResponse<Void> uploadTooLarge(Exception exception) {
        return ApiResponse.fail("文件过大，请将单张图片控制在 10 MB、视频控制在 50 MB 以内");
    }

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> biz(BizException exception) {
        return ApiResponse.fail(exception.getMessage());
    }

    @ExceptionHandler(com.heng.mall.WorkspaceException.class)
    public ApiResponse<Void> workspace(com.heng.mall.WorkspaceException exception, jakarta.servlet.http.HttpServletResponse response) {
        response.setStatus(exception.status);
        return ApiResponse.fail(exception.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class, org.springframework.web.bind.MissingRequestHeaderException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> validation(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException e) {
            String message = e.getBindingResult().getFieldErrors().stream()
                    .findFirst()
                    .map(error -> error.getField() + " 参数不合法")
                    .orElse("请求参数不合法");
            return ApiResponse.fail(message);
        }
        return ApiResponse.fail("请求参数不合法");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> unexpected(Exception exception) {
        log.error("unhandled api error", exception);
        return ApiResponse.fail("服务暂时不可用");
    }
}
