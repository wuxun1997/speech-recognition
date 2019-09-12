package com.wlf.translateprovider.cdr;

import com.wlf.translateprovider.javabean.ots.TranslateCdr;
import com.wlf.translateprovider.utils.IPUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
@Aspect
@Component
public class CdrAsept {
    private final static SimpleDateFormat SF = new SimpleDateFormat("yyyyMMddHHmmss");

    // 话单格式：话单类型|话单记录时间|接口时延|调用方IP|本地IP|用户ID|用户名|结果码
    private final static String CDR_FORMAT = "{}|{}|{}|{}|{}|{}|{}|{}";

    @Around("execution(* com.wlf.translateprovider.controller.TranslateController.*(..))")
    public Object recordCdr(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String startDate = SF.format(new Date(startTime));

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest httpServletRequest = attributes.getRequest();
        String localIp = IPUtil.getLocalIp();
        String remoteIp = IPUtil.getRemoteIp(httpServletRequest);
        TranslateCdr cdr = new TranslateCdr();
        cdr.setRemoteIp(remoteIp);
        CdrThreadLocal.setTranslateCdr(cdr);

        Object result = joinPoint.proceed();

        long endTime = System.currentTimeMillis();
        cdr = CdrThreadLocal.getTranslateCdr();
        if (cdr != null) {
            log.error(CDR_FORMAT, CdrThreadLocal.getTranslateCdr().getCdrType(), startDate, endTime - startTime, remoteIp, localIp, CdrThreadLocal.getTranslateCdr().getUserId(), CdrThreadLocal.getTranslateCdr().getUserName(), CdrThreadLocal.getTranslateCdr().getResultCode());
        }
        return result;
    }
}
