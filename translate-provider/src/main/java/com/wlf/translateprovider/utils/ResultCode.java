package com.wlf.translateprovider.utils;

public enum ResultCode {
    SUCCESS("200", "success"),
    TRANSLATOR_FAILED("10001", "translator failed."),
    PARAM_ERROR("10002", "request param check failed."),
    LISTEN_FAILED("10003","listen failed."),
    UPLOAD_FAILED("10004","upload file failed."),
    TTS_FAILED("10005","tts failed.");

    private String code;

    private String message;

    private ResultCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

}
