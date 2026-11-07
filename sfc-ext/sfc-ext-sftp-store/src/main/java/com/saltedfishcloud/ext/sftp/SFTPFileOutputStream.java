package com.saltedfishcloud.ext.sftp;

import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.SFTPClient;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;

/**
 * SFTP文件输出流，持有远程文件及其所属的SFTP会话，关闭时一并释放并将会话归还连接池
 */
public class SFTPFileOutputStream extends OutputStream {
    private final RemoteFile file;
    private final OutputStream os;
    /**
     * 打开远程文件所使用的SFTP会话，close()时会归还连接池
     */
    private final SFTPClient client;
    /**
     * 是否已关闭，用于保证close()的幂等性
     */
    private boolean closed;

    /**
     * 构造SFTP文件输出流
     * @param file   已打开的远程文件
     * @param client 打开该远程文件所使用的SFTP会话
     */
    public SFTPFileOutputStream(RemoteFile file, SFTPClient client) {
        this.file = file;
        this.client = client;
        os = this.file.new RemoteFileOutputStream();
    }

    @Override
    public void write(int b) throws IOException {
        os.write(b);
    }

    @Override
    public void write(@NotNull byte[] b) throws IOException {
        os.write(b);
    }

    @Override
    public void write(@NotNull byte[] b, int off, int len) throws IOException {
        os.write(b, off, len);
    }

    /**
     * 关闭流，按 输出流 → 远程文件 → SFTP会话 的顺序释放资源。
     * 即使某个资源释放失败，后续资源仍会被释放，且后续异常会被抑制到首个异常上。
     * 重复调用无副作用。
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException first = null;
        try {
            os.close();
        } catch (IOException e) {
            first = e;
        } finally {
            try {
                file.close();
            } catch (IOException e) {
                first = mergeException(first, e);
            } finally {
                try {
                    client.close();
                } catch (IOException e) {
                    first = mergeException(first, e);
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }

    /**
     * 合并异常，若已存在首个异常则将后续异常作为被抑制异常附加，否则将后续异常作为首个异常
     * @param first 首个异常，可为null
     * @param next  后续异常
     * @return 用于继续向外抛出的异常
     */
    private static IOException mergeException(IOException first, IOException next) {
        if (first == null) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }
}
