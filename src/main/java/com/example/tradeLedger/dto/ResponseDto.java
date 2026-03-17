package com.example.tradeLedger.dto;

import lombok.Data;

@Data
public class ResponseDto {
    Object data;
    int StatusCode;
    String message;
    String status;

    public ResponseDto() {
    }

    public ResponseDto(Object data, int statusCode, String message, String status) {
        this.data = data;
        StatusCode = statusCode;
        this.message = message;
        this.status = status;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public int getStatusCode() {
        return StatusCode;
    }

    public void setStatusCode(int statusCode) {
        StatusCode = statusCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
