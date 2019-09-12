package com.wlf.translateprovider.utils;

public enum ResultCode {
    SUCCESS("200", "success"),
    IP_ERROR("10001", "ip auth failed."),
    PARAM_ERROR("10002", "request param check failed."),
    LANGUAGE_FAILED("10003", "from and data not matched."),
    TRANSLATOR_FAILED("10004", "translator failed."),
    IAT_FAILED("10005", "voice recognize failed."),
    UPLOAD_FAILED("10006", "upload file failed."),
    TTS_FAILED("10007", "voice synthesis failed.");

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
