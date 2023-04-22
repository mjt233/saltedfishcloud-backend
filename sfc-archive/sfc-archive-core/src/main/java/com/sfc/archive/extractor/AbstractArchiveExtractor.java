package com.sfc.archive.extractor;

import com.sfc.archive.ArchiveHandleEventListener;
import com.sfc.archive.model.ArchiveFile;
import com.xiaotao.saltedfishcloud.utils.PathUtils;
import com.xiaotao.saltedfishcloud.utils.ResourceUtils;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 默认的压缩包文件系统，子类只需实现getArchiveInputStream()方法。
 * 默认采取顺序读取的方式访问压缩包
 */
@Slf4j
public abstract class AbstractArchiveExtractor implements ArchiveExtractor {
    protected List<ArchiveHandleEventListener> listeners = new ArrayList<>();
    protected List<Consumer<Path>> resourceBeginFetchFetchers = new ArrayList<>();
    protected List<Consumer<Path>> resourceFinishFetchFetchers = new ArrayList<>();

    /**
     * 待解压文件在本地文件系统上的临时文件路径
     */
    @Getter
    protected Path tempArchivePath;

    @Override
    public void addEventListener(ArchiveHandleEventListener listener) {
        listeners.add(listener);
    }

    /**
     * 将Resource转为在本地文件系统上的File对象。若Resource本身就是本地文件系统的文件，则直接返回对应的File。否则会使用{@link Resource#getInputStream()}来获取文件流保存到本地作为临时文件。<br>
     *
     * <br><br>
     * <strong>Note: </strong>若产生了临时文件，则需要手动清除。<br>
     *
     * 在外部可通过方法{@link #onResourceBeginFetch(Consumer)}和{@link #onResourceFinishFetch(Consumer)}感知临时文件的创建过程 <br>
     *
     * 可内部可通过方法{@link #getTempArchivePath()}方法获取临时文件在本地文件系统上的保存路径
     *
     * @param resource  待转换的对象
     * @return          转换或保存后的本地文件系统File文件对象
     */
    protected File resourceToFile(Resource resource) throws IOException {
        if (resource instanceof PathResource) {
            return resource.getFile();
        } else {
            return this.fetchResource(resource);
        }
    }

    /**
     * 将资源抓取到临时的本地文件系统
     * @param resource  资源
     * @return          本地文件系统上的临时文件
     */
    protected File fetchResource(Resource resource) throws IOException {
        File file;
        tempArchivePath = Paths.get(StringUtils.appendPath(PathUtils.getTempDirectory(), StringUtils.getRandomString(6) + ".zip"));
        resourceBeginFetchFetchers.forEach(e -> e.accept(tempArchivePath));
        try {
            ResourceUtils.saveToFile(resource, tempArchivePath);
            log.debug("[解压文件]非本地文件系统存储服务，需要复制文件到本地文件系统：{} ", resource.getFilename());
            log.debug("[解压文件]临时文件：{}", tempArchivePath);
        } catch (IOException e) {
            tempArchivePath = null;
            log.debug("[解压文件]临时文件保存出错");
            Files.deleteIfExists(tempArchivePath);
            throw e;
        }
        file = tempArchivePath.toFile();
        resourceFinishFetchFetchers.forEach(e -> e.accept(tempArchivePath));
        return file;
    }

    @Override
    public void onResourceBeginFetch(Consumer<Path> tempPathConsumer) {
        resourceBeginFetchFetchers.add(tempPathConsumer);
    }

    @Override
    public void onResourceFinishFetch(Consumer<Path> tempPathConsumer) {
        resourceFinishFetchFetchers.add(tempPathConsumer);
    }

    @Override
    public ArchiveInputStream walk(ArchiveExtractorVisitor visitor) throws Exception {
        ArchiveInputStream stream = getArchiveInputStream();
        ArchiveEntry entry;
        try {
            entry = stream.getNextEntry();
        } catch (IOException e) {
            stream.close();
            throw e;
        }
        ArchiveExtractorVisitor.Result result;
        do {
            ArchiveEntryInputStream entryInputStream = new ArchiveEntryInputStream(entry, stream);
            ArchiveFile compressFile = ArchiveFile.formArchiveEntry(entry);

            // 开始执行访问器
            try {
                result = visitor.walk(compressFile, entryInputStream);
            } catch (Exception exception) {
                exception.printStackTrace();
                stream.close();
                throw new ArchiveException("visitor error ", exception);
            }

            if (result == ArchiveExtractorVisitor.Result.STOP) {
                break;
            }
            if (result == ArchiveExtractorVisitor.Result.SKIP) {
                entryInputStream.skipThisEntry();
            }
            entry = stream.getNextEntry();
        } while (entry != null);
        return stream;
    }

    protected abstract ArchiveInputStream getArchiveInputStream() throws IOException, ArchiveException;

    @Override
    public List<? extends ArchiveFile> listFiles() throws IOException {
        List<ArchiveFile> res = new LinkedList<>();
        try(
            InputStream ignored = walk(((file, stream) -> {
                res.add(file);
                return ArchiveExtractorVisitor.Result.SKIP;
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
            return ArchiveExtractorVisitor.Result.CONTINUE;
        }))) {
            log.debug("完成解压：{}", dest);
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException(e.getCause());
        }
    }


}
