package com.wlf.translateprovider.cdr;

import com.wlf.translateprovider.javabean.ots.TranslateCdr;
import com.wlf.translateprovider.utils.ExpiredCache;
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

    // 话单格式：接口名称|话单记录时间|接口时延|调用方IP|本地IP|用户ID|用户名|源语言|目标语言|结果码|QPS
    private final static String CDR_FORMAT = "{}|{}|{}|{}|{}|{}|{}|{}|{}|{}|{}";

    // 过期缓存
    private ExpiredCache expiredCache = new ExpiredCache();

    @Around("execution(* com.wlf.translateprovider.controller.TranslateController.*(..))")
    public Object recordCdr(ProceedingJoinPoint joinPoint) throws Throwable {

        long startTime = System.currentTimeMillis();
        String startDate = SF.format(new Date(startTime));

        // 白名单校验
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest httpServletRequest = attributes.getRequest();
        String localIp = IPUtil.getLocalIp();
        String remoteIp = IPUtil.getRemoteIp(httpServletRequest);
        TranslateCdr cdr = new TranslateCdr();
        cdr.setRemoteIp(remoteIp);
        CdrThreadLocal.setTranslateCdr(cdr);

        // 获取接口名
        String requestPath = httpServletRequest.getRequestURI();
        String cacheKey = requestPath.substring(requestPath.lastIndexOf("/") + 1, requestPath.length());

        // 设置过期时间为1秒
        long qps = expiredCache.set(cacheKey, 1).get();

        Object result = joinPoint.proceed();

        long endTime = System.currentTimeMillis();
        cdr = CdrThreadLocal.getTranslateCdr();
        if (cdr != null) {
            log.error(CDR_FORMAT, cacheKey, startDate, endTime - startTime, remoteIp, localIp, cdr.getUserId(),
                    cdr.getUserName(), cdr.getFrom(), cdr.getTo(), cdr.getResultCode(), qps);
        }
        CdrThreadLocal.delThreadLocal();
        return result;
    }
}
