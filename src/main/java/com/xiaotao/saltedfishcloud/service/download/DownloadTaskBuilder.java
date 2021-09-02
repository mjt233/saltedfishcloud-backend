package com.xiaotao.saltedfishcloud.service.download;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public class DownloadTaskBuilder {
    private String url;

    @Setter(AccessLevel.NONE)
    private Map<String, String> headers = new HashMap<>();

    @Setter(AccessLevel.NONE)
    private String range;

    public void setRange(int begin) {
        this.range = begin + "-";
    }

    public void setRange(long begin, long end) {
        this.range = begin + "-" + end;
    }
}
