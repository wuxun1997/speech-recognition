package com.wlf.translateprovider.cdr;

import com.wlf.translateprovider.javabean.TranslateCdr;

public class CdrThreadLocal {

    private static final ThreadLocal<TranslateCdr> threadLocal = new ThreadLocal<>();

    public static TranslateCdr getTranslateCdr() {
        return threadLocal.get();
    }

    public static void setTranslateCdr(TranslateCdr translateCdr) {
        threadLocal.set(translateCdr);
    }

    public static void delThreadLocal() {
        threadLocal.remove();
    }
}
