package io.github.charlsoliver.psychelink.exception;

/**
 * 资源不存在异常 —— 由 GlobalExceptionHandler 统一转为 404
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
