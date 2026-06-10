package com.dejebu.controller;

import com.dejebu.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(this::translateFieldError)
                .orElse("請求參數不正確");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    private String translateFieldError(FieldError error) {
        String field = error.getField();
        String code = error.getCode() == null ? "" : error.getCode();

        if ("username".equals(field)) {
            if (code.contains("Pattern")) {
                return "帳號只能包含英文字母、數字與底線";
            }
            if (code.contains("Size")) {
                return "帳號長度需為 3 到 32 字";
            }
            return "帳號格式不正確";
        }

        if ("password".equals(field)) {
            if (code.contains("Size")) {
                return "密碼至少需要 6 個字元";
            }
            return "密碼格式不正確";
        }

        if ("displayName".equals(field)) {
            if (code.contains("Size")) {
                return "角色名稱長度需為 1 到 32 字";
            }
            return "角色名稱格式不正確";
        }

        if ("element".equals(field)) {
            return "請選擇元素屬性";
        }

        if ("appearance".equals(field)) {
            return "請選擇角色外型";
        }

        return "請求參數不正確";
    }
}
