package org.ctt.draw_guess.exception;

import org.ctt.draw_guess.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// @RestControllerAdvice 注解表示这是一个全局的异常处理类
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 捕获所有 RuntimeException 异常
     * @param e 异常对象
     * @return 统一的错误结果
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Object> handleRuntimeException(RuntimeException e) {
        // 在服务器控制台打印详细的错误日志
        log.error("运行时异常: ", e);
        // 把异常信息返回给前端，比如 "用户名 'xxx' 已被占用！"
        return Result.error(e.getMessage());

    }

    // 你还可以定义更多的 @ExceptionHandler 来处理不同类型的特定异常
}