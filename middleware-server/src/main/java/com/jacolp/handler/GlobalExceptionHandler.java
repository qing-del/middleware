package com.jacolp.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jacolp.constant.DatabaseConstant;
import com.jacolp.exception.AuthenticationException;
import com.jacolp.exception.BaseException;
import com.jacolp.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     * @param ex BaseException
     * @return Result
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex){
        log.error("Exception information:{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    @ExceptionHandler
    public Result authExceptionHandler(AuthenticationException ex, HttpServletResponse response){
        log.error("Exception information:{}", ex.getMessage());
        // 设置 401 状态码
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return Result.error(ex.getMessage());
    }

    /**
     * 捕获 Jackson 的异常
     * @param ex JsonProcessingException
     * @return Result
     */
    @ExceptionHandler({JsonProcessingException.class})
    public Result jsonExceptionHandler(JsonProcessingException ex) {
        log.error("JSON conversion exception: {}", ex.getMessage());
        return Result.error("服务器异常");
    }

    /**
     * 捕获 SQL 完整性约束异常
     * @param ex SQLIntegrityConstraintViolationException
     * @return Result
     */
    @ExceptionHandler({SQLIntegrityConstraintViolationException.class})
    public Result sqlExceptionHandler(SQLIntegrityConstraintViolationException ex) {
        log.error("SQL Integrity Constraint Violation Exception: {}", ex.getMessage());
        // 抓获唯一性错误
        if (ex.getMessage().contains("Duplicate entry")) {
            String[] split = ex.getMessage().split(" ");
            String msg = split[2] + DatabaseConstant.ALREADY_EXISTS;
            return Result.error(msg);
        }

        // SQL Integrity Constraint Violation Exception: Column 'user_id' cannot be null
        if (ex.getMessage().contains("cannot be null")) {
            String[] split = ex.getMessage().split(" ");
            String msg = "传递的字段：" + split[1] + DatabaseConstant.CANNOT_BE_NULL;
            return Result.error(msg);
        }

        return Result.error("未知错误");
    }
}
