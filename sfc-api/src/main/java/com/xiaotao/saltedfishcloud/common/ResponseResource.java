package com.xiaotao.saltedfishcloud.common;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.core.io.AbstractResource;

@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
public abstract class ResponseResource extends AbstractResource {
    protected String contentType;
    protected String redirectUrl;
    protected String responseFilename;

    @Override
    public String getFilename() {
        return responseFilename;
    }
}
