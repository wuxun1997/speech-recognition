package com.wlf.translateapi.javabean;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TranslateTextRequest implements Serializable {

    private String appId;

    private String userId;

    private String userName;

    private String from;

    private String to;

    private String data;

    private String isComposeVoice;
}
