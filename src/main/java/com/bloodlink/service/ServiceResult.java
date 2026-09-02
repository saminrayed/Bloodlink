package com.bloodlink.service;

public record ServiceResult<T>(boolean success, String message, T data) {
    public static <T> ServiceResult<T> success(String message, T data) { return new ServiceResult<>(true, message, data); }
    public static <T> ServiceResult<T> failure(String message) { return new ServiceResult<>(false, message, null); }
}
