package com.saltedfishcloud.ext.samba;

import com.hierynomus.smbj.share.File;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import org.springframework.core.io.AbstractResource;

import java.io.IOException;
import java.io.InputStream;

public class SambaResource extends AbstractResource {
    private File file;
    public SambaResource(File sambaFile) {
        this.file = sambaFile;
    }

    @Override
    public long lastModified() throws IOException {
        try {
            return file.getFileInformation().getBasicInformation().getLastWriteTime().toDate().getTime();
        } catch (NullPointerException e) {
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    public String getFilename() {
        return StringUtils.getURLLastName(file.getUncPath(), "\\");
    }

    @Override
    public String getDescription() {
        return file.getPath();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return file.getInputStream();
    }
}
