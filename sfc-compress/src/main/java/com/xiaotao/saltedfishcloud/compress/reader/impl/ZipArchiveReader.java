package com.xiaotao.saltedfishcloud.compress.reader.impl;

import com.xiaotao.saltedfishcloud.compress.reader.AbstractArchiveReader;
import com.xiaotao.saltedfishcloud.compress.reader.ArchiveReaderVisitor;
import com.xiaotao.saltedfishcloud.compress.reader.CompressFile;
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
 * @TODO 兼容多种编码（或用户指定）
 */
public class ZipArchiveReader extends AbstractArchiveReader {
    private final ZipFile zip;

    public ZipArchiveReader(File file) throws IOException {
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
    public ArchiveInputStream walk(ArchiveReaderVisitor visitor) throws Exception {
        Enumeration<ZipArchiveEntry> entries = zip.getEntries();
        ArchiveReaderVisitor.Result res = ArchiveReaderVisitor.Result.CONTINUE;
        while (entries.hasMoreElements() && res == ArchiveReaderVisitor.Result.CONTINUE) {
            ZipArchiveEntry entry = entries.nextElement();
            try (InputStream in = zip.getInputStream(entry)){
                 res = visitor.walk(new CompressFileImpl(entry), in);
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
    public List<? extends CompressFile> listFiles() {
        Enumeration<ZipArchiveEntry> entries = zip.getEntries();
        ArrayList<CompressFile> res = new ArrayList<>();
        while (entries.hasMoreElements()) {
            res.add(CompressFile.formArchiveEntry(entries.nextElement()));
        }
        return res;
    }
}
