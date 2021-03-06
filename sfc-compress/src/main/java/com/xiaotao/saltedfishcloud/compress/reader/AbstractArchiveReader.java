package com.xiaotao.saltedfishcloud.compress.reader;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

/**
 * 默认的压缩包文件系统，子类只需实现getArchiveInputStream()方法。
 * 默认采取顺序读取的方式访问压缩包
 */
@Slf4j
public abstract class AbstractArchiveReader implements ArchiveReader {

    @Override
    public ArchiveInputStream walk(ArchiveReaderVisitor visitor) throws Exception {
        ArchiveInputStream stream = getArchiveInputStream();
        ArchiveEntry entry;
        try {
            entry = stream.getNextEntry();
        } catch (IOException e) {
            stream.close();
            throw e;
        }
        ArchiveReaderVisitor.Result result;
        do {
            ArchiveEntryInputStream entryInputStream = new ArchiveEntryInputStream(entry, stream);
            CompressFile compressFile = CompressFile.formArchiveEntry(entry);

            // 开始执行访问器
            try {
                result = visitor.walk(compressFile, entryInputStream);
            } catch (Exception exception) {
                exception.printStackTrace();
                stream.close();
                throw new ArchiveException("visitor error ", exception);
            }

            if (result == ArchiveReaderVisitor.Result.STOP) {
                break;
            }
            if (result == ArchiveReaderVisitor.Result.SKIP) {
                entryInputStream.skipThisEntry();
            }
            entry = stream.getNextEntry();
        } while (entry != null);
        return stream;
    }

    protected abstract ArchiveInputStream getArchiveInputStream() throws IOException, ArchiveException;

    @Override
    public List<? extends CompressFile> listFiles() {
        List<CompressFile> res = new LinkedList<>();
        try(
            InputStream ignored = walk(((file, stream) -> {
                res.add(file);
                return ArchiveReaderVisitor.Result.SKIP;
            }))
        ) {
            return res;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res;
    }

    @Override
    public void extractAll(Path dest) throws IOException {
        try(InputStream ignore = walk(((file, stream) -> {
            Path target = Paths.get(dest + "/" + file.getPath());
            if (file.isDirectory()) {
                Files.createDirectories(target);
            } else {
                if (!Files.exists(target.getParent())) Files.createDirectories(target.getParent());
                StreamUtils.copy(stream, Files.newOutputStream(target));
            }
            return ArchiveReaderVisitor.Result.CONTINUE;
        }))) {
            log.debug("完成解压：{}", dest);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException(e.getCause());
        }
    }


}
