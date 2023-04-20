package com.sfc.archive.extractor.impl;

import com.sfc.archive.model.CommonArchiveFile;
import com.sfc.archive.extractor.AbstractArchiveExtractor;
import com.sfc.archive.extractor.ArchiveExtractorVisitor;
import com.sfc.archive.model.ArchiveFile;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipException;

/**
 * todo 兼容多种编码（或用户指定）
 * todo 防御ZIP炸弹
 */
public class ZipArchiveExtractor extends AbstractArchiveExtractor {
    private final ZipFile zip;

    public ZipArchiveExtractor(File file) throws IOException {
        try {
            this.zip = new ZipFile(file, "GBK");
        } catch (IOException e) {
            if (e.getCause() instanceof ZipException) {
                throw (ZipException)e.getCause();
            } else {
                throw e;
            }
        }
    }

    @Override
    public void close() {
        try {
            zip.close();
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
        Enumeration<ZipArchiveEntry> entries = zip.getEntries();
        ArchiveExtractorVisitor.Result res = ArchiveExtractorVisitor.Result.CONTINUE;
        while (entries.hasMoreElements() && res == ArchiveExtractorVisitor.Result.CONTINUE) {
            ZipArchiveEntry entry = entries.nextElement();
            try (InputStream in = zip.getInputStream(entry)){
                 res = visitor.walk(new CommonArchiveFile(entry), in);
            } catch (Exception e) {
                throw e;
            }
        }
        return null;
    }

    @Override
    public InputStream getInputStream(String name) throws IOException, ArchiveException {
        return this.zip.getInputStream(this.zip.getEntry(name));
    }

    @Override
    public List<? extends ArchiveFile> listFiles() {
        Enumeration<ZipArchiveEntry> entries = zip.getEntries();
        ArrayList<ArchiveFile> res = new ArrayList<>();
        while (entries.hasMoreElements()) {
            res.add(ArchiveFile.formArchiveEntry(entries.nextElement()));
        }
        return res;
    }
}
