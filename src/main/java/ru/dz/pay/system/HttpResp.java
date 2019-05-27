package ru.dz.pay.system;

import lombok.Getter;
import lombok.ToString;
import org.apache.http.HttpResponse;

@Getter
@ToString
public class HttpResp {
    private HttpResponse response;
    private String name;
    private String body;
    private int statusCode;
    private boolean ok;
    private long connectTime;

    public HttpResp(HttpResponse response, String body, int statusCode) {
        this.response = response;
        this.body = body;
        this.statusCode = statusCode;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setConnectTime(long connectTime) {
        this.connectTime = connectTime;
    }
}
