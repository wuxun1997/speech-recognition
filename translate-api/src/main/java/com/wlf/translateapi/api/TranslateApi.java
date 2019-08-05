package com.wlf.translateapi.api;

import com.wlf.translateapi.javabean.TranslateTextRequest;
import com.wlf.translateapi.javabean.TtsRequest;
import com.wlf.translateapi.javabean.CommonResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

public interface TranslateApi {

    @RequestMapping("/translateText")
    CommonResponse translateText(@RequestBody TranslateTextRequest request);

    @RequestMapping("/listen")
    CommonResponse listen(@RequestParam(value = "file") MultipartFile file);

    @RequestMapping("/tts")
    CommonResponse tts(@RequestBody TtsRequest ttsRequest);

    @RequestMapping("/translateAudio")
    CommonResponse translateAudio(@RequestParam(value = "file") MultipartFile file,
                                  @RequestParam(value = "userId", required = false) String userId,
                                  @RequestParam(value = "userName", required = false) String userName,
                                  @RequestParam(value = "from") String from,
                                  @RequestParam(value = "to") String to);
}
