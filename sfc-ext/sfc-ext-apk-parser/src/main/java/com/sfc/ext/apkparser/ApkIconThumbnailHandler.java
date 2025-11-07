package com.sfc.ext.apkparser;

import com.xiaotao.saltedfishcloud.service.file.thumbnail.ThumbnailHandler;
import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.IconFace;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

public class ApkIconThumbnailHandler implements ThumbnailHandler {
    private final static List<String> SUPPORT_TYPE = Collections.singletonList("apk");
    @Override
    public boolean generate(Resource resource, String type, OutputStream outputStream) throws IOException {
        try (ApkFile apkFile = new ApkFile(resource.getFile())) {
            apkFile.getAllIcons()
                    .stream()
                    .filter(IconFace::isFile)
                    .findAny()
                    .ifPresent(icon -> {
                        try {
                            outputStream.write(icon.getData());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        };
        return false;
    }

    @Override
    public List<String> getSupportType() {
        return SUPPORT_TYPE;
    }
}
