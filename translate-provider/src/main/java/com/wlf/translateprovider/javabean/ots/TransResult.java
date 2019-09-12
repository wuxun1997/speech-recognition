package com.wlf.translateprovider.javabean.ots;

public class TransResult {

    private int code;
    private String message;
    private String sid;
    private TransResultData data;

    public int getCode() {
        return code;
    }
    public String getMessage() {
        return this.message;
    }
    public String getSid() {
        return sid;
    }
    public TransResultData getData() {
        return data;
    }
}
