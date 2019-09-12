package com.wlf.translateprovider.utils;

import com.wlf.translateprovider.javabean.AppInfo;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
@Slf4j
public class AppUtil {

    // 应用相关信息列表
    @Value("${appIds}")
    private String appIds;

    // 应用列表
    private static List<AppInfo> apps = new ArrayList<>();

    @PostConstruct
    private void initAppInfos() {

        // 读取配置文件，获取appId列表
        if (appIds != null && !appIds.trim().equals("")) {
            String[] appList = appIds.split("\\|");
            if (appList == null && appList.length == 0) {
                return;
            }

            for (int i = 0; i < appList.length; i++) {
                AppInfo appInfo = new AppInfo();
                String[] appInfos = appList[i].split(",");
                if (appInfos == null || appInfos.length == 0 || appInfos.length != 4) {
                    continue;
                } else {
                    appInfo.setAppId(appInfos[0]);
                    appInfo.setApiKey(appInfos[1]);
                    appInfo.setApiSecret(appInfos[2]);
                    appInfo.setTtsKey(appInfos[3]);
                }
                apps.add(appInfo);
            }
        }

        if (apps.size() <= 0) {
            log.error("apps is empty.");
            return;
        }
    }

    /**
     * 根据appId获取应用信息
     *
     * @param appId
     * @return
     */
    public static AppInfo getApp(String appId) {
        AppInfo result = null;
        if (apps != null && apps.size() > 0) {
            for (AppInfo app : apps) {
                if (app.getAppId().equals(appId)) {
                    result = app;
                }
            }
        }
        return result;
    }

    /**
     * 构造鉴权url
     *
     * @param hostUrl
     * @param apiKey
     * @param apiSecret
     * @return
     * @throws Exception
     */
    public static String getAuthUrl(String hostUrl, String apiKey, String apiSecret) throws Exception {
        URL url = new URL(hostUrl);
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = format.format(new Date());
        StringBuilder builder = new StringBuilder("host: ").append(url.getHost()).append("\n").
                append("date: ").append(date).append("\n").
                append("GET ").append(url.getPath()).append(" HTTP/1.1");
        log.info(builder.toString());
        Charset charset = Charset.forName("UTF-8");
        Mac mac = Mac.getInstance("hmacsha256");
        SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(charset), "hmacsha256");
        mac.init(spec);
        byte[] hexDigits = mac.doFinal(builder.toString().getBytes(charset));
        String sha = Base64.getEncoder().encodeToString(hexDigits);

        log.info(sha);
        String authorization = String.format("api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"", apiKey, "hmac-sha256", "host date request-line", sha);
        log.info(authorization);
        HttpUrl httpUrl = HttpUrl.parse("https://" + url.getHost() + url.getPath()).newBuilder().
                addQueryParameter("authorization", Base64.getEncoder().encodeToString(authorization.getBytes(charset))).
                addQueryParameter("date", date).
                addQueryParameter("host", url.getHost()).
                build();
        return httpUrl.toString();
    }

}