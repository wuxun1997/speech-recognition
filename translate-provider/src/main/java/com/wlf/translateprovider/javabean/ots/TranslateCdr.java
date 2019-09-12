package com.wlf.translateprovider.javabean.ots;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TranslateCdr {

    private String cdrType;

    private String remoteIp;

    private String userId;

    private String userName;

    private String from;

    private String to;

    private String resultCode;
}
