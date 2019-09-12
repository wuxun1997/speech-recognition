package com.wlf.translateprovider.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.wlf.translateapi.javabean.ResultData;
import com.wlf.translateprovider.javabean.AppInfo;
import com.wlf.translateprovider.javabean.ots.TransResult;
import com.wlf.translateprovider.javabean.ots.TranslateCdr;
import com.wlf.translateprovider.cdr.CdrThreadLocal;
import com.wlf.translateprovider.utils.*;
import com.wlf.translateapi.javabean.CommonResponse;
import com.wlf.translateapi.javabean.TranslateTextRequest;
import com.wlf.translateapi.javabean.TtsRequest;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import org.apache.commons.codec.binary.Base64;

@Slf4j
@Service
public class TranslateService {

    // 语音识别接口地址
    @Value("${iat_url}")
    private String iat_url;

    // 语音合成接口地址
    @Value("${tts_url}")
    private String tts_url;

    // 翻译接口地址
    @Value("${ots_url}")
    private String ots_url;

    // 语音识别音频存放路径
    @Value("${iat_filePath}")
    private String iat_filePath;

    // 语音合成音频存放路径
    @Value("${tts_filePath}")
    private String tts_filePath;

    // 对外合成音频路径
    @Value("${tts_prefix}")
    private String tts_prefix;

    // 白名单列表
    @Value("${white_ips}")
    private String white_ips;

    final OkHttpClient client = new OkHttpClient.Builder().build();

    // 音频编码(raw合成的音频格式pcm、wav,lame合成的音频格式MP3)
    private static final String AUE = "raw";
    // 采样率
    private static final String AUF = "audio/L16;rate=16000";
    // 语速（取值范围0-100）
    private static final String SPEED = "50";
    // 音量（取值范围0-100）
    private static final String VOLUME = "50";
    // 音调（取值范围0-100）
    private static final String PITCH = "50";
    // 发音人（登陆开放平台https://www.xfyun.cn/后--我的应用（必须为webapi类型应用）--添加在线语音合成（已添加的不用添加）--发音人管理---添加发音人--修改发音人参数）
    private static final String VOICE_NAME = "xiaoyan";
    // 引擎类型
    private static final String ENGINE_TYPE_EN = "intp65_en";
    private static final String ENGINE_TYPE_CN = "intp65";
    // 文本类型（webapi是单次只支持1000个字节，具体看您的编码格式，计算一下具体支持多少文字）
    private static final String TEXT_TYPE = "text";

    public CommonResponse translateAudio(MultipartFile file, String userId, String userName, String from, String to,
                                         String appId) {
        CountDownLatch iatCountDown = new CountDownLatch(1);
        CommonResponse result = new CommonResponse();
        result.setCode(ResultCode.TRANSLATOR_FAILED.getCode());
        result.setMessage(ResultCode.TRANSLATOR_FAILED.getMessage());

        // 记话单
        TranslateCdr translateCdr = CdrThreadLocal.getTranslateCdr();
        translateCdr.setCdrType("1");
        translateCdr.setUserId(userId);
        translateCdr.setUserName(userName);
        translateCdr.setFrom(from);
        translateCdr.setTo(to);

        try {
            // 白名单校验
            if (checkIp(translateCdr.getRemoteIp())) {
                result.setCode(ResultCode.IP_ERROR.getCode());
                result.setMessage(ResultCode.IP_ERROR.getMessage());
                return result;
            }

            // 构造翻译请求消息体
            TranslateTextRequest request = new TranslateTextRequest();
            request.setAppId(appId);
            request.setFrom(from);
            request.setTo(to);
            request.setUserId(userId);
            request.setUserName(userName);

            // 校验入参
            if (checkOTSRequestParam(request)) {
                result.setCode(ResultCode.PARAM_ERROR.getCode());
                result.setMessage(ResultCode.PARAM_ERROR.getMessage());
                return result;
            }

            // 先语音识别
            doIat(file, result, iatCountDown, from, appId, userId);
            if (!ResultCode.SUCCESS.getCode().equals(result.getCode())) {
                return result;
            }

            // 再翻译
            request.setData(result.getData().getResult());
            result.getData().setSource(result.getData().getResult());
            doTranslate(request, result);
            if (!ResultCode.SUCCESS.getCode().equals(result.getCode())) {
                return result;
            }

            // 最后合成
            TtsRequest ttsRequest = new TtsRequest();
            ttsRequest.setAppId(appId);
            ttsRequest.setTo(to);
            ttsRequest.setTtsText(result.getData().getResult());
            ttsRequest.setUserId(userId);
            doTts(ttsRequest, result);
        } catch (Exception e) {
            log.error("call translate failed, error: {}", e.getMessage());
            e.printStackTrace();
        } finally {
            translateCdr.setResultCode(result.getCode());
        }

        return result;
    }

    public CommonResponse tts(TtsRequest ttsRequest) {
        CommonResponse result = new CommonResponse();
        result.setCode(ResultCode.TTS_FAILED.getCode());
        result.setMessage(ResultCode.TTS_FAILED.getMessage());

        // 记话单
        TranslateCdr translateCdr = CdrThreadLocal.getTranslateCdr();
        translateCdr.setCdrType("4");
        translateCdr.setUserId(ttsRequest.getUserId());
        translateCdr.setTo(ttsRequest.getTo());

        try {
            // 白名单校验
            if (checkIp(translateCdr.getRemoteIp())) {
                result.setCode(ResultCode.IP_ERROR.getCode());
                result.setMessage(ResultCode.IP_ERROR.getMessage());
                return result;
            }
            doTts(ttsRequest, result);
        } catch (Exception e) {
            log.error("call tts failed, error :{}", e.getMessage());
        } finally {
            translateCdr.setResultCode(result.getCode());
        }

        return result;
    }

    public CommonResponse iat(MultipartFile file, String from, String appId, String userId) {
        CountDownLatch iatCountDown = new CountDownLatch(1);
        CommonResponse result = new CommonResponse();
        result.setCode(ResultCode.IAT_FAILED.getCode());
        result.setMessage(ResultCode.IAT_FAILED.getMessage());

        // 记话单
        TranslateCdr translateCdr = CdrThreadLocal.getTranslateCdr();
        translateCdr.setCdrType("3");
        translateCdr.setFrom(from);
        translateCdr.setUserId(userId);

        try {
            // 白名单校验
            if (checkIp(translateCdr.getRemoteIp())) {
                result.setCode(ResultCode.IP_ERROR.getCode());
                result.setMessage(ResultCode.IP_ERROR.getMessage());
                return result;
            }
            doIat(file, result, iatCountDown, from, appId, userId);
        } catch (Exception e) {
            log.error("call listen failed, error : {}", e.getMessage());
        } finally {
            translateCdr.setResultCode(result.getCode());
        }

        return result;
    }

    public CommonResponse translateText(TranslateTextRequest request) {
        CommonResponse result = new CommonResponse();
        result.setCode(ResultCode.TRANSLATOR_FAILED.getCode());
        result.setMessage(ResultCode.TRANSLATOR_FAILED.getMessage());

        // 记话单
        TranslateCdr translateCdr = CdrThreadLocal.getTranslateCdr();
        translateCdr.setCdrType("2");
        translateCdr.setUserId(request.getUserId());
        translateCdr.setUserName(request.getUserName());
        translateCdr.setFrom(request.getFrom());
        translateCdr.setTo(request.getTo());

        try {
            // 白名单校验
            if (checkIp(translateCdr.getRemoteIp())) {
                result.setCode(ResultCode.IP_ERROR.getCode());
                result.setMessage(ResultCode.IP_ERROR.getMessage());
                return result;
            }
            doTranslate(request, result);
        } catch (Exception e) {
            log.error("call translator failed, error: {}", e.getMessage());
        } finally {
            translateCdr.setResultCode(result.getCode());
        }
        return result;
    }

    private void doTts(TtsRequest ttsRequest, CommonResponse result) {
        // 校验入参
        if (checkTtsRequest(ttsRequest)) {
            result.setCode(ResultCode.PARAM_ERROR.getCode());
            result.setMessage(ResultCode.PARAM_ERROR.getMessage());
            return;
        }

        // 获取应用信息
        AppInfo appInfo = AppUtil.getApp(ttsRequest.getAppId());
        if (appInfo == null) {
            log.error("appInfo is null.");
            return;
        }

        // 构造消息头
        Map<String, String> header = null;
        try {
            header = buildHttpHeader(ttsRequest.getAppId(), appInfo.getTtsKey(), ttsRequest.getTo());
        } catch (Exception e) {
            log.error("buildHttpHeader failed, error :{}", e.getMessage());
            return;
        }

        // 调用讯飞合成语音接口
        Map<String, Object> resultMap = null;
        try {
            resultMap = HttpUtil.doPost2(tts_url, header, "text=" + URLEncoder.encode(ttsRequest.getTtsText(), "utf-8"));
            log.info("占用内存大小： {}", URLEncoder.encode(ttsRequest.getTtsText(), "utf-8").getBytes().length);
        } catch (Exception e) {
            log.error("call tts failed, error : {}", e.getMessage());
            return;
        }

        String fileName = null;
        String fullFilePath = null;
        if ("audio/mpeg".equals(resultMap.get("Content-Type"))) { // 合成成功
            if ("raw".equals(AUE)) {
                fileName = resultMap.get("sid") + ".wav";
                fullFilePath = FileUtil.save(tts_filePath + File.separator + ttsRequest.getUserId(), fileName, (byte[]) resultMap.get("body"));
                log.info("合成 WebAPI 调用成功，音频保存位置：{}", fullFilePath);
            } else {
                fileName = resultMap.get("sid") + ".mp3";
                fullFilePath = FileUtil.save(tts_filePath + File.separator + ttsRequest.getUserId(), fileName, (byte[]) resultMap.get("body"));
                log.info("合成 WebAPI 调用成功，音频保存位置：{}", fullFilePath);
            }
        } else { // 合成失败
            log.error("call tts faild: {}", resultMap.get("body").toString());
            log.error("合成 WebAPI 调用失败，错误信息：{}", resultMap.get("body").toString());
        }

        // 设置返回对象
        ResultData data = result.getData();
        if (data == null) {
            data = new ResultData();
        }
        data.setFilePath(tts_prefix + ttsRequest.getUserId() + "/" + fileName);
        result.setData(data);
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMessage(ResultCode.SUCCESS.getMessage());
    }

    private void doTranslate(TranslateTextRequest request, CommonResponse result) {
        // 校验入参
        if (checkOTSRequestParam(request)) {
            result.setCode(ResultCode.PARAM_ERROR.getCode());
            result.setMessage(ResultCode.PARAM_ERROR.getMessage());
            return;
        }

        // 语言与文本校验
        if (checkLanguage(request.getFrom(), request.getData())) {
            result.setCode(ResultCode.LANGUAGE_FAILED.getCode());
            result.setMessage(ResultCode.LANGUAGE_FAILED.getMessage());
            return;
        }

        // 获取应用信息
        AppInfo appInfo = AppUtil.getApp(request.getAppId());
        if (appInfo == null) {
            log.error("app info is null.");
            return;
        }

        // 构造请求消息体
        String requestBody = null;
        try {
            requestBody = buildHttpBody(request, request.getAppId());
        } catch (Exception e) {
            log.error("build requestBody failed, error: {}", e.getMessage());
            return;
        }

        // 构造请求消息头
        Map<String, String> requestHeader = null;
        try {
            requestHeader = buildHttpHeader(ots_url, appInfo.getApiKey(), appInfo.getApiSecret(), requestBody);
        } catch (Exception e) {
            log.error("build requestHeader failed, error: {}", e.getMessage());
            return;
        }

        // 调讯飞翻译接口
        Map<String, Object> resultMap = HttpUtil.doPost2(ots_url, requestHeader, requestBody);
        if (resultMap != null) {
            String resultStr = resultMap.get("body").toString();
            log.info("【OTS WebAPI 接口调用结果】: {}", resultStr);
            Gson json = new Gson();
            TransResult resultData = json.fromJson(resultStr, TransResult.class);
            int code = resultData.getCode();
            String message = resultData.getMessage();
            if (code != 0) {
                log.error("call OTS webapi failed, code: {}, message: {}", code, message);
            } else {
                if (resultData.getData() == null || resultData.getData().getResult() == null || resultData.getData().getResult().getTrans_result() == null) {
                    log.error("call OTS webapi success, but result data is null");
                    return;
                }

                ResultData data = result.getData();
                if (data == null) {
                    data = new ResultData();
                }
                data.setSource(request.getData());
                data.setResult(resultData.getData().getResult().getTrans_result().getDst());
                result.setCode(ResultCode.SUCCESS.getCode());
                result.setMessage(ResultCode.SUCCESS.getMessage());
                result.setData(data);

                if (request.getIsComposeVoice() != null && request.getIsComposeVoice().equals("1")) {
                    TtsRequest ttsRequest = new TtsRequest();
                    ttsRequest.setAppId(request.getAppId());
                    ttsRequest.setTo(request.getTo());
                    ttsRequest.setTtsText(data.getResult());
                    ttsRequest.setUserId(request.getUserId());
                    doTts(ttsRequest, result);
                }
            }
        } else {
            log.error("call OTS webapi failed, result is null.");
        }
    }

    private void doIat(MultipartFile file, CommonResponse result, CountDownLatch iatCountDown, String from, String appId, String userId) throws InterruptedException {

        // 校验入参
        if (checkIATRequest(file, from, appId, userId)) {
            result.setCode(ResultCode.PARAM_ERROR.getCode());
            result.setMessage(ResultCode.PARAM_ERROR.getMessage());
            return;
        }

        // 文件校验
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            result.setCode(ResultCode.UPLOAD_FAILED.getCode());
            result.setMessage(ResultCode.UPLOAD_FAILED.getMessage());
            return;
        }

        // 文件上传
        String fullFileName = null;
        try {
            fullFileName = uploadFile(file, iat_filePath + File.separator + userId);
        } catch (Exception e) {
            log.error("upload file failed, error: {}", e.getMessage());
            return;
        }

        // 获取应用信息
        AppInfo appInfo = AppUtil.getApp(appId);

        if (appInfo == null) {
            log.error("appInfo is null.");
            return;
        }

        // 构建鉴权url
        String authUrl = null;
        try {
            authUrl = AppUtil.getAuthUrl(iat_url, appInfo.getApiKey(), appInfo.getApiSecret());
        } catch (Exception e) {
            log.error("build authUrl failed, error:{}", e.getMessage());
            return;
        }

        OkHttpClient client = new OkHttpClient.Builder().build();

        //将url中的 schema http://和https://分别替换为ws:// 和 wss://
        String url = authUrl.replace("http://", "ws://").replace("https://", "wss://");
        log.info("url===>{}", url);

        Request request = new Request.Builder().url(url).build();
        WebSocket webSocket = client.newWebSocket(request, new WebIATWS(fullFileName, appId, result, iatCountDown, from));
        iatCountDown.await();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMessage(ResultCode.SUCCESS.getMessage());
    }


    /**
     * 组装http请求体
     */
    private String buildHttpBody(TranslateTextRequest request, String appId) throws Exception {
        JsonObject body = new JsonObject();
        JsonObject business = new JsonObject();
        JsonObject common = new JsonObject();
        JsonObject data = new JsonObject();
        //填充common
        common.addProperty("app_id", appId);
        //填充business
        business.addProperty("from", request.getFrom());
        business.addProperty("to", request.getTo());
        //填充data
        log.info("【OTS WebAPI TEXT字个数：】{}", request.getData().length());
        byte[] textByte = request.getData().getBytes("UTF-8");
        String textBase64 = new String(java.util.Base64.getEncoder().encodeToString(textByte));
        log.info("【OTS WebAPI textBase64编码后长度：】{}", textBase64.length());
        data.addProperty("text", textBase64);
        //填充body
        body.add("common", common);
        body.add("business", business);
        body.add("data", data);
        return body.toString();
    }

    /**
     * 组装http请求头
     */
    private static Map<String, String> buildHttpHeader(String appId, String tts_apiKey, String to) throws UnsupportedEncodingException {
        String curTime = System.currentTimeMillis() / 1000L + "";
        String param = "{\"auf\":\"" + AUF + "\",\"aue\":\"" + AUE + "\",\"voice_name\":\"" + VOICE_NAME + "\",\"speed\":\"" + SPEED + "\",\"volume\":\"" + VOLUME + "\",\"pitch\":\""
                + PITCH + "\",\"engine_type\":\"" + ("cn".equals(to) ? ENGINE_TYPE_CN : ENGINE_TYPE_EN) + "\",\"text_type\":\"" + TEXT_TYPE + "\"}";
        String paramBase64 = new String(Base64.encodeBase64(param.getBytes("UTF-8")));
        String checkSum = DigestUtils.md5Hex(tts_apiKey + curTime + paramBase64);
        Map<String, String> header = new HashMap<String, String>();
        header.put("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
        header.put("X-Param", paramBase64);
        header.put("X-CurTime", curTime);
        header.put("X-CheckSum", checkSum);
        header.put("X-Appid", appId);
        return header;
    }

    /**
     * 组装http请求头
     */
    public static Map<String, String> buildHttpHeader(String webOTSUrl, String apiKey, String apiSecret, String body) throws Exception {
        Map<String, String> header = new HashMap<String, String>();
        Charset charset = Charset.forName("UTF-8");
        Mac mac = Mac.getInstance("hmacsha256");
        URL url = new URL(webOTSUrl);

        //时间戳
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date dateD = new Date();
        String date = format.format(dateD);
        log.info("【OTS WebAPI date】: {}", date);

        //对body进行sha256签名,生成digest头部，POST请求必须对body验证
        SecretKeySpec spec = new SecretKeySpec(body.getBytes(charset), "hmacsha256");
        mac.init(spec);
        String digestBase64 = "SHA-256=" + java.util.Base64.getEncoder().encodeToString(mac.doFinal());
        log.info("【OTS WebAPI digestBase64】：{}", digestBase64);

        //hmacsha256加密原始字符串
        StringBuilder builder = new StringBuilder("host: ").append(url.getHost()).append("\n").//
                append("date: ").append(date).append("\n").//
                append("POST ").append(url.getPath()).append(" HTTP/1.1").append("\n").//
                append("digest: ").append(digestBase64);
        spec = new SecretKeySpec(apiSecret.getBytes(charset), "hmacsha256");
        mac.init(spec);
        byte[] hexDigits = mac.doFinal(builder.toString().getBytes(charset));
        String sha = java.util.Base64.getEncoder().encodeToString(hexDigits);
        log.info("【OTS WebAPI sha】:{}", sha);

        //组装authorization
        String authorization = String.format("api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"", apiKey, "hmac-sha256", "host date request-line digest", sha);
        log.info("【OTS WebAPI authorization】: {}", authorization);

        header.put("Authorization", authorization);
        header.put("Content-Type", "application/json");
        header.put("Accept", "application/json,version=1.0");
        header.put("Host", url.getHost());
        header.put("Date", date);
        header.put("Digest", digestBase64);
        log.info("【OTS WebAPI header】: {}", header);
        return header;
    }


    private String uploadFile(MultipartFile file, String filePath) throws IOException {
        // 改个新名字
        String fileName = FileUtil.createFileName(file.getOriginalFilename());

        // 判断文件目录是否存在，不存在则新建
        File targetFile = new File(filePath);
        if (!targetFile.exists()) {
            targetFile.mkdir();
        }

        return FileUtil.save(filePath, fileName, file.getBytes());
    }

    /**
     * 白名单校验
     *
     * @param remoteIP
     * @return
     */
    private boolean checkIp(String remoteIP) {
        if (remoteIP == null || "".equals(remoteIP.trim())) {
            log.error("remoteIp is null");
            return true;
        } else if (!remoteIP.matches(white_ips)) {
            log.error("remoteIp: {} is not in whiteIps: {}", remoteIP, white_ips);
            return true;
        }

        return false;
    }

    /**
     * 翻译参数校验
     *
     * @param request
     * @return
     */
    private boolean checkOTSRequestParam(TranslateTextRequest request) {
        if (request == null) {
            return true;
        }
        String appId = request.getAppId();
        String userId = request.getUserId();
        String from = request.getFrom();
        String to = request.getTo();

        if (appId == null || "".equals(appId.trim())) {
            log.error("param from is null.");
            return true;
        } else if (userId == null || "".equals(userId.trim())) {
            log.error("param userId is null.");
            return true;
        } else if (from == null || "".equals(from.trim())) {
            log.error("param from is null.");
            return true;
        } else if (!from.matches("cn|en")) {
            log.error("param from is not en or cn.");
            return true;
        } else if (to == null || "".equals(to.trim())) {
            log.error("param to is null");
            return true;
        } else if (!to.matches("en|cn")) {
            log.error("param to is not en or cn.");
            return true;
        } else if (from.equals(to)) {
            log.error("param from == to.");
            return true;
        }

        return false;
    }

    /**
     * 校验语音合成参数
     *
     * @param ttsRequest
     * @return
     */
    private boolean checkTtsRequest(TtsRequest ttsRequest) {
        if (ttsRequest == null) {
            return true;
        }

        String appId = ttsRequest.getAppId();
        String to = ttsRequest.getTo();
        String ttsText = ttsRequest.getTtsText();
        String userId = ttsRequest.getUserId();

        if (appId == null || "".equals(appId.trim())) {
            log.error("param from is null.");
            return true;
        } else if (to == null || "".equals(to.trim())) {
            log.error("param to is null");
            return true;
        } else if (userId == null || "".equals(userId.trim())) {
            log.error("param userId is null");
            return true;
        } else if (!to.matches("en|cn")) {
            log.error("param to is not en or cn");
            return true;
        } else if (ttsText == null || "".equals(ttsText.trim())) {
            log.error("param ttsText is null");
            return true;
        }
        return false;

    }

    /**
     * 语言与文本匹配校验
     *
     * @param from
     * @param data
     * @return
     */
    private boolean checkLanguage(String from, String data) {
        if (data == null || "".equals(data.trim())) {
            log.error("request param data is null.");
            return true;
        } else if ("cn".equals(from) && !StringUtil.isChinese(StringUtil.removePunctuation(data))) {
            log.error("data is not match from");
            return true;
        } else if ("en".equals(from) && !StringUtil.isEnglish(StringUtil.removePunctuation(data))) {
            log.error("data is not match from");
            return true;
        }
        return false;
    }

    /**
     * 语音识别参数校验
     *
     * @param file
     * @param from
     * @return
     */
    private boolean checkIATRequest(MultipartFile file, String from, String appId, String userId) {

        if (appId == null || "".equals(appId.trim())) {
            log.error("param from is null.");
            return true;
        } else if (from == null || "".equals(from.trim())) {
            log.error("param from is null.");
            return true;
        } else if (!from.matches("cn|en")) {
            log.error("param from is not en or cn.");
            return true;
        } else if (userId == null || "".equals(userId.trim())) {
            log.error("userId is null.");
            return true;
        }

        return false;
    }

}
