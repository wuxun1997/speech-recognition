package com.wlf.translateprovider.javabean;

public class ListenResponseData {

    private int code;
    private String message;
    private String sid;
    private ListenData data;

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return this.message;
    }

    public String getSid() {
        return sid;
    }

    public ListenData getData() {
        return data;
    }
}
