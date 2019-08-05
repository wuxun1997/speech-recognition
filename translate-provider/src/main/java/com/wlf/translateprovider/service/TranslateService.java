package com.wlf.translateprovider.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.wlf.translateprovider.javabean.TransResult;
import com.wlf.translateprovider.javabean.TranslateCdr;
import com.wlf.translateprovider.cdr.CdrThreadLocal;
import com.wlf.translateprovider.utils.FileUtil;
import com.wlf.translateprovider.utils.HttpUtil;
import com.wlf.translateprovider.utils.ResultCode;
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

    // 应用ID
    @Value("${appId}")
    private String appId;

    // OTS webapi 接口地址
    @Value("${webOTSUrl}")
    private String webOTSUrl;

    // 翻译接口APIKey
    @Value("${tranlator_apiKey}")
    private String translator_apiKey;

    // 翻译接口APISercet
    @Value("${tranlator_apiSecret}")
    private String translator_apiSecret;

    // IAT webapi 接口地址
    @Value("${webIATUrl}")
    private String webIATUrl;

    // 翻译接口APIKey
    @Value("${listen_apiKey}")
    private String listen_apiKey;

    // 翻译接口APISercet
    @Value("${listen_apiSecret}")
    private String listen_apiSecret;

    // IAT webapi 接口地址
    @Value("${webTTSUrl}")
    private String webTTSUrl;

    // 翻译接口APIKey
    @Value("${tts_apiKey}")
    private String tts_apiKey;

    // 语音识别音频存放路径
    @Value("${iat_filePath}")
    private String iat_filePath;

    // 语音合成音频存放路径
    @Value("${tts_filePath}")
    private String tts_filePath;

    // 对外合成音频路径
    @Value("${tts_prefix}")
    private String tts_prefix;

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
    private static final String ENGINE_TYPE = "intp65_en";
    // 文本类型（webapi是单次只支持1000个字节，具体看您的编码格式，计算一下具体支持多少文字）
    private static final String TEXT_TYPE = "text";

    public CommonResponse translateAudio(MultipartFile file, String userId, String userName, String from, String to) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        CommonResponse result = new CommonResponse();
        result.setCode(ResultCode.TRANSLATOR_FAILED.getCode());
        result.setMessage(ResultCode.TRANSLATOR_FAILED.getMessage());

        // 记话单
        TranslateCdr translateCdr = new TranslateCdr();
        translateCdr.setCdrType("4");
        translateCdr.setUserId(userId != null ? userId : "");
        translateCdr.setUserName(userName != null ? userName : "");

        try {
            // 先语音识别
            doVoiceRecognize(countDownLatch, file, result);

            // 再翻译
            TranslateTextRequest request = new TranslateTextRequest();
            request.setFrom(from);
            request.setTo(to);
            request.setUserId(userId);
            request.setUserName(userName);
            result.setFrom(result.getResult());
            request.setData(result.getResult());
            doTranslate(request, result);

            // 最后合成
            TtsRequest ttsRequest = new TtsRequest(result.getResult());
            doTts(ttsRequest, result);
        } catch (Exception e) {
            log.error("call translate failed, error: {}", e.getMessage());
            e.printStackTrace();
        } finally {
            translateCdr.setResultCode(result.getCode());
            CdrThreadLocal.setTranslateCdr(translateCdr);
        }

        return result;
    }

    public CommonResponse tts(TtsRequest ttsRequest) {
        CommonResponse result = new CommonResponse();
        result.setCode(ResultCode.TTS_FAILED.getCode());
        result.setMessage(ResultCode.TTS_FAILED.getMessage());

        // 记话单
        TranslateCdr translateCdr = new TranslateCdr();
        translateCdr.setCdrType("3");

        try {
            doTts(ttsRequest, result);
        } catch (Exception e) {
            log.error("call tts failed, error :{}", e.getMessage());
        } finally {
            translateCdr.setResultCode(result.getCode());
            CdrThreadLocal.setTranslateCdr(translateCdr);
        }

        return result;
    }

    public CommonResponse listen(MultipartFile file) {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        CommonResponse result = new CommonResponse();
        result.setCode(ResultCode.UPLOAD_FAILED.getCode());
        result.setMessage(ResultCode.UPLOAD_FAILED.getMessage());

        // 记话单
        TranslateCdr translateCdr = new TranslateCdr();
        translateCdr.setCdrType("2");
        try {
            doVoiceRecognize(countDownLatch, file, result);
        } catch (Exception e) {
            log.error("call listen failed, error : {}", e.getMessage());
        } finally {
            translateCdr.setResultCode(result.getCode());
            CdrThreadLocal.setTranslateCdr(translateCdr);
        }

        return result;
    }

    public CommonResponse translateText(TranslateTextRequest request) {
        CommonResponse result = new CommonResponse();
        result.setCode(ResultCode.TRANSLATOR_FAILED.getCode());
        result.setMessage(ResultCode.TRANSLATOR_FAILED.getMessage());

        // 记话单
        TranslateCdr translateCdr = new TranslateCdr();
        translateCdr.setCdrType("1");
        translateCdr.setUserId(request.getUserId() != null ? request.getUserId() : "");
        translateCdr.setUserName(request.getUserName() != null ? request.getUserName() : "");

        try {
            doTranslate(request, result);
        } catch (Exception e) {
            log.error("call translator failed, error: {}", e.getMessage());
        } finally {
            translateCdr.setResultCode(result.getCode());
            CdrThreadLocal.setTranslateCdr(translateCdr);
        }
        return result;
    }

    private void doTts(TtsRequest ttsRequest, CommonResponse result) {
        // 校验入参
        if (ttsRequest == null || ttsRequest.getTtsText() == null || "".equals(ttsRequest.getTtsText().trim())) {
            log.error("tts param text is null.");
            result.setCode(ResultCode.PARAM_ERROR.getCode());
            result.setMessage(ResultCode.PARAM_ERROR.getMessage());
            return;
        }

        // 构造消息头
        Map<String, String> header = null;
        try {
            header = buildHttpHeader(appId, tts_apiKey);
        } catch (Exception e) {
            log.error("buildHttpHeader failed, error :{}", e.getMessage());
            return;
        }

        // 调用讯飞合成语音接口
        Map<String, Object> resultMap = null;
        try {
            resultMap = HttpUtil.doPost2(webTTSUrl, header, "text=" + URLEncoder.encode(ttsRequest.getTtsText(), "utf-8"));
            System.out.println("占用内存大小： " + URLEncoder.encode(ttsRequest.getTtsText(), "utf-8").getBytes().length);
        } catch (Exception e) {
            log.error("call tts failed, error : {}", e.getMessage());
            return;
        }

        String fileName = null;
        String fullFilePath = null;
        if ("audio/mpeg".equals(resultMap.get("Content-Type"))) { // 合成成功
            if ("raw".equals(AUE)) {
                fileName = resultMap.get("sid") + ".wav";
                fullFilePath = FileUtil.save(tts_filePath, fileName, (byte[]) resultMap.get("body"));
                log.info("call tts success, file : {}", fullFilePath);
                System.out.println("合成 WebAPI 调用成功，音频保存位置：" + fullFilePath);
            } else {
                fileName = resultMap.get("sid") + ".mp3";
                fullFilePath = FileUtil.save(tts_filePath, fileName, (byte[]) resultMap.get("body"));
                log.info("call tts success, file : {}", fullFilePath);
                System.out.println("合成 WebAPI 调用成功，音频保存位置：" + fullFilePath);
            }
        } else { // 合成失败
            log.error("call tts failed: {}", resultMap.get("body").toString());
            log.error("call tts faild: {}", resultMap.get("body").toString());
            System.out.println("合成 WebAPI 调用失败，错误信息：" + resultMap.get("body").toString());
        }

        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMessage(ResultCode.SUCCESS.getMessage());
        result.setFilePath(tts_prefix + fileName);
    }

    private void doTranslate(TranslateTextRequest request, CommonResponse result) {
        // 校验入参
        if (checkRequest(request)) {
            result.setCode(ResultCode.PARAM_ERROR.getCode());
            result.setMessage(ResultCode.PARAM_ERROR.getMessage());
            return;
        }

        // 构造请求消息体
        String requestBody = null;
        try {
            requestBody = buildHttpBody(request, appId);
        } catch (Exception e) {
            log.error("build requestBody failed, error: {}", e.getMessage());
            return;
        }

        // 构造请求消息头
        Map<String, String> requestHeader = null;
        try {
            requestHeader = buildHttpHeader(webOTSUrl, translator_apiKey, translator_apiSecret, requestBody);
        } catch (Exception e) {
            log.error("build requestHeader failed, error: {}", e.getMessage());
            return;
        }

        // 调讯飞翻译接口
        Map<String, Object> resultMap = HttpUtil.doPost2(webOTSUrl, requestHeader, requestBody);
        if (resultMap != null) {
            String resultStr = resultMap.get("body").toString();
            System.out.println("【OTS WebAPI 接口调用结果】\n" + resultStr);
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
                result.setCode(ResultCode.SUCCESS.getCode());
                result.setMessage(ResultCode.SUCCESS.getMessage());
                result.setResult(resultData.getData().getResult().getTrans_result().getDst());
            }
        } else {
            log.error("call OTS webapi failed, result is null.");
        }
    }

    private void doVoiceRecognize(CountDownLatch countDownLatch, MultipartFile file, CommonResponse result) throws InterruptedException {
        // 文件校验
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            log.error("file is empty.");
            return;
        }

        // 文件上传
        String fullFileName = null;
        try {
            fullFileName = uploadFile(file, iat_filePath);
        } catch (Exception e) {
            log.error("upload file failed, error: {}", e.getMessage());
            return;
        }

        result.setCode(ResultCode.LISTEN_FAILED.getCode());
        result.setMessage(ResultCode.LISTEN_FAILED.getMessage());

        // 构建鉴权url
        String authUrl = null;
        try {
            authUrl = getAuthUrl(webIATUrl, listen_apiKey, listen_apiSecret);
        } catch (Exception e) {
            log.error("build authUrl failed, error:{}", e.getMessage());
            return;
        }

        OkHttpClient client = new OkHttpClient.Builder().build();

        //将url中的 schema http://和https://分别替换为ws:// 和 wss://
        String url = authUrl.replace("http://", "ws://").replace("https://", "wss://");
        System.out.println("url===>" + url);

        Request request = new Request.Builder().url(url).build();
        WebSocket webSocket = client.newWebSocket(request, new WebIATWS(fullFileName, appId, result, countDownLatch));
        countDownLatch.await();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMessage(ResultCode.SUCCESS.getMessage());
    }


    private boolean checkRequest(TranslateTextRequest request) {

        if (request == null) {
            return true;
        }

        String from = request.getFrom();
        String to = request.getTo();
        String data = request.getData();

        if (from == null || "".equals(from.trim())) {
            log.error("param from is null.");
            return true;
        } else if (!from.matches("cn")) {
            log.error("param from is not cn.");
            return true;
        } else if (to == null || "".equals(to.trim())) {
            log.error("param to is null");
            return true;
        } else if (!to.matches("ja|en")) {
            log.error("param to is not ja or en");
            return true;
        } else if (data == null || "".equals(data.trim())) {
            log.error("param data is null");
            return true;
        }
        return false;
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
        System.out.println("【OTS WebAPI TEXT字个数：】\n" + request.getData().length());
        byte[] textByte = request.getData().getBytes("UTF-8");
        String textBase64 = new String(java.util.Base64.getEncoder().encodeToString(textByte));
        System.out.println("【OTS WebAPI textBase64编码后长度：】\n" + textBase64.length());
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
    private static Map<String, String> buildHttpHeader(String appId, String tts_apiKey) throws UnsupportedEncodingException {
        String curTime = System.currentTimeMillis() / 1000L + "";
        String param = "{\"auf\":\"" + AUF + "\",\"aue\":\"" + AUE + "\",\"voice_name\":\"" + VOICE_NAME + "\",\"speed\":\"" + SPEED + "\",\"volume\":\"" + VOLUME + "\",\"pitch\":\"" + PITCH + "\",\"engine_type\":\"" + ENGINE_TYPE + "\",\"text_type\":\"" + TEXT_TYPE + "\"}";
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
        System.out.println("【OTS WebAPI date】\n" + date);

        //对body进行sha256签名,生成digest头部，POST请求必须对body验证
        SecretKeySpec spec = new SecretKeySpec(body.getBytes(charset), "hmacsha256");
        mac.init(spec);
        String digestBase64 = "SHA-256=" + java.util.Base64.getEncoder().encodeToString(mac.doFinal());
        System.out.println("【OTS WebAPI digestBase64】\n" + digestBase64);

        //hmacsha256加密原始字符串
        StringBuilder builder = new StringBuilder("host: ").append(url.getHost()).append("\n").//
                append("date: ").append(date).append("\n").//
                append("POST ").append(url.getPath()).append(" HTTP/1.1").append("\n").//
                append("digest: ").append(digestBase64);
        //System.out.println("【OTS WebAPI builder】\n" + builder);
        spec = new SecretKeySpec(apiSecret.getBytes(charset), "hmacsha256");
        mac.init(spec);
        byte[] hexDigits = mac.doFinal(builder.toString().getBytes(charset));
        String sha = java.util.Base64.getEncoder().encodeToString(hexDigits);
        System.out.println("【OTS WebAPI sha】\n" + sha);

        //组装authorization
        String authorization = String.format("api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"", apiKey, "hmac-sha256", "host date request-line digest", sha);
        System.out.println("【OTS WebAPI authorization】\n" + authorization);

        header.put("Authorization", authorization);
        header.put("Content-Type", "application/json");
        header.put("Accept", "application/json,version=1.0");
        header.put("Host", url.getHost());
        header.put("Date", date);
        header.put("Digest", digestBase64);
        System.out.println("【OTS WebAPI header】\n" + header);
        return header;
    }

    private String getAuthUrl(String hostUrl, String apiKey, String apiSecret) throws Exception {
        URL url = new URL(hostUrl);
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = format.format(new Date());
        StringBuilder builder = new StringBuilder("host: ").append(url.getHost()).append("\n").//
                append("date: ").append(date).append("\n").//
                append("GET ").append(url.getPath()).append(" HTTP/1.1");
        System.out.println(builder);
        Charset charset = Charset.forName("UTF-8");
        Mac mac = Mac.getInstance("hmacsha256");
        SecretKeySpec spec = new SecretKeySpec(apiSecret.getBytes(charset), "hmacsha256");
        mac.init(spec);
        byte[] hexDigits = mac.doFinal(builder.toString().getBytes(charset));
        String sha = java.util.Base64.getEncoder().encodeToString(hexDigits);

        System.out.println(sha);
        String authorization = String.format("api_key=\"%s\", algorithm=\"%s\", headers=\"%s\", signature=\"%s\"", apiKey, "hmac-sha256", "host date request-line", sha);
        System.out.println(authorization);
        HttpUrl httpUrl = HttpUrl.parse("https://" + url.getHost() + url.getPath()).newBuilder().//
                addQueryParameter("authorization", java.util.Base64.getEncoder().encodeToString(authorization.getBytes(charset))).//
                addQueryParameter("date", date).//
                addQueryParameter("host", url.getHost()).//
                build();
        return httpUrl.toString();
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
}
