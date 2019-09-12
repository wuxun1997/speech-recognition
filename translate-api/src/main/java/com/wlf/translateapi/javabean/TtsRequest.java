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
public class TtsRequest implements Serializable {

    private String userId;

    private String appId;

    private String to;

    private String ttsText;
}
