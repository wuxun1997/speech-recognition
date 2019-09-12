package com.wlf.translateprovider.javabean.iat;

import com.google.gson.JsonObject;

public class ListenResult {
    int bg;
    int ed;
    String pgs;
    int[] rg;
    int sn;
    Ws[] ws;
    boolean ls;
    JsonObject vad;

    public Text getText() {
        Text text = new Text();
        StringBuilder sb = new StringBuilder();
        for (Ws ws : this.ws) {
            sb.append(ws.cw[0].w);
        }
        text.sn = this.sn;
        text.text = sb.toString();
        text.sn = this.sn;
        text.rg = this.rg;
        text.pgs = this.pgs;
        text.bg = this.bg;
        text.ed = this.ed;
        text.ls = this.ls;
        text.vad = this.vad == null ? null : this.vad;
        return text;
    }
}
