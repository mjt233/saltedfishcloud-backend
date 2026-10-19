package com.sfc.archive.extractor.impl.zip;

import com.sfc.archive.extractor.AbstractArchiveExtractor;
import com.sfc.archive.extractor.ArchiveExtractorVisitor;
import com.sfc.archive.model.ArchiveFile;
import com.sfc.archive.model.ArchiveParam;
import com.sfc.archive.model.CommonArchiveFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * todo 防御ZIP炸弹
 */
@Slf4j
public class ZipArchiveExtractor extends AbstractArchiveExtractor {
    private ZipFile zip;

    private final ArchiveParam archiveParam;
    private final Resource resource;

    public ZipArchiveExtractor(ArchiveParam archiveParam, Resource resource) {
        this.archiveParam = archiveParam;
        this.resource = resource;
    }

    private void initZip() throws IOException {
        if (this.zip == null) {
            this.zip = new ZipFile(resourceToFile(resource) , archiveParam.getEncoding());
        }

    }

    @Override
    public void close() {
        try {
            if (zip != null) {
                zip.close();
            }
            if (tempArchivePath != null) {
                log.info("待解压缩的临时文件移除：{}", tempArchivePath);
                Files.deleteIfExists(tempArchivePath);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected ArchiveInputStream getArchiveInputStream() {
        throw new UnsupportedOperationException("不支持获取ZIP的ArchiveInputStream");
    }

    @Override
    public ArchiveInputStream walk(ArchiveExtractorVisitor visitor) throws Exception {
        initZip();
        Enumeration<ZipArchiveEntry> entries = zip.getEntries();
        ArchiveExtractorVisitor.Result res = ArchiveExtractorVisitor.Result.CONTINUE;
        while (entries.hasMoreElements() && res == ArchiveExtractorVisitor.Result.CONTINUE) {
            ZipArchiveEntry entry = entries.nextElement();
            try (InputStream in = zip.getInputStream(entry)){
                 res = visitor.walk(new CommonArchiveFile(entry), in);
            }
        }
        return null;
    }

    @Override
    public InputStream getInputStream(String name) throws IOException, ArchiveException {
        initZip();
        return this.zip.getInputStream(this.zip.getEntry(name));
    }

    @Override
    public List<? extends ArchiveFile> listFiles() throws IOException {
        initZip();
        Enumeration<ZipArchiveEntry> entries = zip.getEntries();
        ArrayList<ArchiveFile> res = new ArrayList<>();
        while (entries.hasMoreElements()) {
            res.add(ArchiveFile.formArchiveEntry(entries.nextElement()));
        }
        return res;
    }
}
