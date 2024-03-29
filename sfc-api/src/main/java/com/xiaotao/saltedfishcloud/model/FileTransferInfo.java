package com.xiaotao.saltedfishcloud.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collection;

@Data
@NoArgsConstructor
public class FileTransferInfo {
    private String source;
    private Collection<String> filenames;
    private String dest;
}
