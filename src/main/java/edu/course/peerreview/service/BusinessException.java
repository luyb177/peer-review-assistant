package edu.course.peerreview.service;

/** 可直接展示给用户的校验错误，不包含 SQL、凭据或外部服务响应体。 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) { super(message); }
    public BusinessException(String message, Throwable cause) { super(message, cause); }
}
