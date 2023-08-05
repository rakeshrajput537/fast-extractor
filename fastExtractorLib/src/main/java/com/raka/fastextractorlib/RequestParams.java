package com.raka.fastextractorlib;

import java.util.concurrent.ConcurrentHashMap;

public class RequestParams {
    ConcurrentHashMap<String, String> urlParams = new ConcurrentHashMap<>();

    public void put(String key, String value) {
        if (key != null && value != null) {
            urlParams.put(key, value);
        }
    }

    public ConcurrentHashMap<String, String> getUrlParams() {
        return urlParams;
    }
}