package com.xiaotao.saltedfishcloud.service.wrap;

import com.xiaotao.saltedfishcloud.model.FileTransferInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WrapInfo {
    private Long uid;
    private FileTransferInfo files;
}
