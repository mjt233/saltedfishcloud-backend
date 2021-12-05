package com.xiaotao.saltedfishcloud.service.file.exception;

import java.nio.file.FileSystemException;

public class DirectoryAlreadyExistsException extends FileSystemException {

    public DirectoryAlreadyExistsException(String file) {
        super(file);
    }

    public DirectoryAlreadyExistsException(String file, String other, String reason) {
        super(file, other, reason);
    }
}
