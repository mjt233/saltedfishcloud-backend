package com.xiaotao.saltedfishcloud.common;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

/**
 * 该 Resource 在被包装为接口响应时，可以定义部分响应头
 * @see com.xiaotao.saltedfishcloud.utils.ResourceUtils#wrapResource
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Accessors(chain = true)
public abstract class ResponseResource extends AbstractResource {
    /**
     * 暂时无用的属性
     */
    protected String redirectUrl;

    protected String contentType;
    protected String responseFilename;

    @Override
    public String getFilename() {
        return responseFilename;
    }

    public static ResponseResource create(InputStreamSource inputStreamSource, String description) {
        return new ResponseResource() {
            {
                if(inputStreamSource instanceof Resource r) {
                    responseFilename = r.getFilename();
                }
                if (inputStreamSource instanceof RedirectableUrl r) {
                    redirectUrl = r.getRedirectUrl();
                }
            }
            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return inputStreamSource.getInputStream();
            }
        };
    }

    public static ResponseResource create(InputStreamSource inputStreamSource) {
        return create(inputStreamSource, inputStreamSource.toString());
    }
}
