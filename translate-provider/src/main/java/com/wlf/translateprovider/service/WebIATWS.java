package com.wlf.translateprovider.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.wlf.translateprovider.javabean.Decoder;
import com.wlf.translateprovider.javabean.ListenResponseData;
import com.wlf.translateprovider.javabean.Text;
import com.wlf.translateapi.javabean.CommonResponse;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class WebIATWS extends WebSocketListener {
    public static final int StatusFirstFrame = 0;
    public static final int StatusContinueFrame = 1;
    public static final int StatusLastFrame = 2;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyy-MM-dd HH:mm:ss.SSS");

    private String fullFileName;
    private String appId;
    private CommonResponse listenResult;
    private CountDownLatch countDownLatch;

    Decoder decoder = new Decoder();
    Date dateBegin = new Date();
    Date dateEnd = null;

    public WebIATWS(String fullFileName, String appId, CommonResponse listenResult, CountDownLatch countDownLatch) {
        this.fullFileName = fullFileName;
        this.appId = appId;
        this.listenResult = listenResult;
        this.countDownLatch = countDownLatch;
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        super.onOpen(webSocket, response);
        new Thread(() -> {
            //连接成功，开始发送数据
            int frameSize = 1280; //每一帧音频的大小,建议每 40ms 发送 122B
            int status = 0;  // 音频的状态
            try (FileInputStream fs = new FileInputStream(fullFileName)) {
                byte[] buffer = new byte[frameSize];
                // 发送音频
                end:
                while (true) {
                    int len = fs.read(buffer);
                    if (len == -1) {
                        status = StatusLastFrame;  //文件读完，改变status 为 2
                    }
                    switch (status) {
                        case StatusFirstFrame:   // 第一帧音频status = 0
                            JsonObject frame = new JsonObject();
                            JsonObject business = new JsonObject();  //第一帧必须发送
                            JsonObject common = new JsonObject();  //第一帧必须发送
                            JsonObject data = new JsonObject();  //每一帧都要发送
                            // 填充common
                            common.addProperty("app_id", appId);
                            //填充business
                            business.addProperty("language", "zh_cn");
                            business.addProperty("domain", "iat");
                            business.addProperty("accent", "mandarin");
                            //business.addProperty("nunum", 0);
                            //business.addProperty("ptt", 0);//标点符号
                            //business.addProperty("rlang", "zh-hk"); // zh-cn :简体中文（默认值）zh-hk :繁体香港(若未授权不生效)
                            //business.addProperty("vinfo", 1);
                            //business.addProperty("dwa", "wpgs");//动态修正(若未授权不生效)
                            //business.addProperty("nbest", 5);// 句子多候选(若未授权不生效)
                            //business.addProperty("wbest", 3);// 词级多候选(若未授权不生效)
                            //填充data
                            data.addProperty("status", StatusFirstFrame);
                            data.addProperty("format", "audio/L16;rate=16000");
                            data.addProperty("encoding", "raw");
                            data.addProperty("audio", Base64.getEncoder().encodeToString(Arrays.copyOf(buffer, len)));
                            //填充frame
                            frame.add("common", common);
                            frame.add("business", business);
                            frame.add("data", data);
                            webSocket.send(frame.toString());
                            status = StatusContinueFrame;  // 发送完第一帧改变status 为 1
                            break;
                        case StatusContinueFrame:  //中间帧status = 1
                            JsonObject frame1 = new JsonObject();
                            JsonObject data1 = new JsonObject();
                            data1.addProperty("status", StatusContinueFrame);
                            data1.addProperty("format", "audio/L16;rate=16000");
                            data1.addProperty("encoding", "raw");
                            data1.addProperty("audio", Base64.getEncoder().encodeToString(Arrays.copyOf(buffer, len)));
                            frame1.add("data", data1);
                            webSocket.send(frame1.toString());
                            System.out.println("send continue");
                            break;
                        case StatusLastFrame:    // 最后一帧音频status = 2 ，标志音频发送结束
                            JsonObject frame2 = new JsonObject();
                            JsonObject data2 = new JsonObject();
                            data2.addProperty("status", StatusLastFrame);
                            data2.addProperty("audio", "");
                            data2.addProperty("format", "audio/L16;rate=16000");
                            data2.addProperty("encoding", "raw");
                            frame2.add("data", data2);
                            webSocket.send(frame2.toString());
                            System.out.println("sendlast");
                            break end;
                    }
                }
                System.out.println("all data is send");
            } catch (FileNotFoundException e) {
                log.error("call onOpen failed, error :{}", e.getMessage());
            } catch (IOException e) {
                log.error("call onOpen failed, error :{}", e.getMessage());
            } catch (Exception e) {
                log.error("call onOpen failed, error :{}", e.getMessage());
            }
        }).start();
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        Gson json = new Gson();
        super.onMessage(webSocket, text);
        System.out.println(text);
        ListenResponseData resp = json.fromJson(text, ListenResponseData.class);
        if (resp != null) {
            if (resp.getCode() != 0) {
                System.out.println("code=>" + resp.getCode() + " error=>" + resp.getMessage() + " sid=" + resp.getSid());
                return;
            }
            if (resp.getData() != null) {
                if (resp.getData().getResult() != null) {
                    Text te = resp.getData().getResult().getText();
                    System.out.println(te.toString());
                    try {
                        decoder.decode(te);
                        System.out.println("中间识别结果 ==》" + decoder.toString());
                    } catch (Exception e) {
                        log.error("call onMessage failed, error :{}", e.getMessage());
                    }
                }
                if (resp.getData().getStatus() == 2) {
                    // todo  resp.data.status ==2 说明数据全部返回完毕，可以关闭连接，释放资源
                    System.out.println("session end ");
                    dateEnd = new Date();
                    System.out.println(sdf.format(dateBegin) + "开始");
                    System.out.println(sdf.format(dateEnd) + "结束");
                    System.out.println("耗时:" + (dateEnd.getTime() - dateBegin.getTime()) + "ms");
                    System.out.println("最终识别结果 ==》" + decoder.toString());
                    listenResult.setResult(decoder.toString());
                    countDownLatch.countDown();
                    System.out.println("本次识别sid ==》" + resp.getSid());
                    decoder.discard();
                    webSocket.close(1000, "");
                } else {
                    // todo 根据返回的数据处理

                }
            }
        }
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        super.onFailure(webSocket, t, response);
        try {
            if (null != response) {
                int code = response.code();
                log.error("call listen faild, code:{}", code);
                System.out.println("onFailure code:" + code);
                System.out.println("onFailure body:" + response.body().string());
                if (101 != code) {
                    System.out.println("connection failed");
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            log.error("call onFailure failed, error: {}", e.getMessage());
        }
    }
}
