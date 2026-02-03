package com.sfc.ext.webdav.model.resource;

import lombok.*;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class WebDavFile extends WebDavItem {
    private Long contentLength;

    private String contentType;
}
