package com.wlf.translateprovider.controller;

import com.wlf.translateprovider.service.TranslateService;
import com.wlf.translateapi.api.TranslateApi;
import com.wlf.translateapi.javabean.CommonResponse;
import com.wlf.translateapi.javabean.TranslateTextRequest;
import com.wlf.translateapi.javabean.TtsRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class TranslateController implements TranslateApi {

        @Autowired
        private TranslateService translateService;


        @Override
        public CommonResponse translateText(TranslateTextRequest request) {
            return translateService.translateText(request);
        }

        @Override
        public CommonResponse iat(MultipartFile file, String from, String appId, String userId) {
            return translateService.iat(file, from, appId, userId);
        }

        @Override
        public CommonResponse tts(TtsRequest ttsRequest) {
            return translateService.tts(ttsRequest);
        }

        @Override
        public CommonResponse translateAudio(MultipartFile file, String userId, String userName, String from, String to, String appId) {
            return translateService.translateAudio(file, userId, userName, from, to, appId);
        }
}
