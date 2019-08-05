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
public class CommonResponse implements Serializable {

    private String code;

    private String message;

    private String result;

    private String from;

    private String filePath;
}
