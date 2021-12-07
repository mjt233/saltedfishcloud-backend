package com.xiaotao.saltedfishcloud.compress.filesystem;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 默认的压缩包文件系统，子类只需实现getArchiveInputStream()方法。
 * 默认采取顺序读取的方式访问压缩包
 */
@Slf4j
public abstract class AbstractCompressFileSystem implements CompressFileSystem {

    @Override
    public ArchiveInputStream walk(CompressFileSystemVisitor visitor) throws IOException, ArchiveException {
        ArchiveInputStream stream = getArchiveInputStream();
        ArchiveEntry entry = stream.getNextEntry();
        CompressFileSystemVisitor.Result result;
        do {
            ArchiveEntryInputStream entryInputStream = new ArchiveEntryInputStream(entry, stream);
            CompressFile compressFile = CompressFile.forArchiveEntry(entry);
            try {
                result = visitor.walk(compressFile, entryInputStream);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                result = CompressFileSystemVisitor.Result.STOP;
            }

            if (result == CompressFileSystemVisitor.Result.STOP) {
                break;
            }
            if (result == CompressFileSystemVisitor.Result.SKIP) {
                entryInputStream.skipThisEntry();
            }
            entry = stream.getNextEntry();
        } while (entry != null);
        return stream;
    }


    @Override
    public List<? extends CompressFile> listFiles(String path) throws IOException, ArchiveException {
        List<CompressFile> res = new LinkedList<>();
        walk(((file, stream) -> {
            res.add(file);
            return CompressFileSystemVisitor.Result.SKIP;
        })).close();
        return res;
    }



    @Override
    public InputStream getInputStream(String name) throws IOException, ArchiveException {
        AtomicReference<InputStream> res = new AtomicReference<>();
        ArchiveInputStream walkStream = walk(((file, stream) -> {
            if (file.getName().equals(name)) {
                res.set(stream);
                return CompressFileSystemVisitor.Result.STOP;
            }
            if (!file.isDirectory()) {
                long skip = stream.skipThisEntry();
                log.debug("skip: " + skip);
            }
            return CompressFileSystemVisitor.Result.CONTINUE;
        }));
        if (res.get() == null) {
            walkStream.close();
            throw new NoSuchFileException(name);
        }
        return res.get();
    }

    @Override
    public void extractAll(Path dest) throws IOException, ArchiveException {
        walk(((file, stream) -> {
            Path target = Paths.get(dest + "/" + file.getPath());
            if (file.isDirectory()) {
                Files.createDirectories(target);
            } else {
                if (!Files.exists(target.getParent())) Files.createDirectories(target.getParent());
                StreamUtils.copy(stream, Files.newOutputStream(target));
            }
            return CompressFileSystemVisitor.Result.CONTINUE;
        })).close();
    }
}
