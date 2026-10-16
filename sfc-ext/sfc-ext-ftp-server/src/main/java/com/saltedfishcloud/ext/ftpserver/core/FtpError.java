package com.saltedfishcloud.ext.ftpserver.core;

import com.sfc.cloud.exception.SfcErrorInfo;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum  FtpError implements SfcErrorInfo {
    FTP_ALREADY_RUNNING("FTP服务已经在运行中", 400, 6001);

    private final String message;
    private final int status;
    private final int code;

}
