package com.saltedfishcloud.ext.ve.model.request;

import com.xiaotao.saltedfishcloud.model.dto.ResourceRequest;
import lombok.Data;

@Data
public class VideoResourceParam {
    private ResourceRequest source;
    private ResourceRequest target;
}
