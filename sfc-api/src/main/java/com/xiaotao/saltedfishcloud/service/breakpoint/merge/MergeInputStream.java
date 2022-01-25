package com.xiaotao.saltedfishcloud.service.breakpoint.merge;

import java.io.IOException;
import java.io.InputStream;

/**
 * 合并的输入流<br>
 * 将依照InputStreamGenerator获取多个输入流并在内部按顺序读取和关闭
 */
public class MergeInputStream extends InputStream {

    private final InputStreamGenerator generator;

    private InputStream currentStream;

    private boolean atEnd = false;

    /**
     * @param generator 输入流生成器
     */
    public MergeInputStream(InputStreamGenerator generator) throws IOException {
        this.generator = generator;
        nextStream();
        if (currentStream == null) {
            atEnd = true;
        }
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (atEnd) return -1;

        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }
        int ret = 0;
        while (len > 0) {
            int s = currentStream.read(b, off + ret, len);
            if (s == -1) {
                if (nextStream()) {
                    continue;
                } else {
                    if (ret == 0) {
                        ret = -1;
                    }
                    atEnd = true;
                    break;
                }
            }
            ret += s;
            len -= s;
        }
        return ret;
    }

    /**
     * 关闭当前流，并切换到下一个文件块的流<br>
     * 切换成功返回true，如果所有文件块已经读取完毕，则返回false
     */
    private boolean nextStream() throws IOException {
        if (currentStream != null) {
            currentStream.close();
        }
        if (generator.hasNext()) {
            currentStream = generator.next();
        } else {
            return false;
        }
        return true;
    }

    @Override
    public void close() throws IOException {
        if (currentStream != null) {
            currentStream.close();
        }
    }

    @Override
    public int read() throws IOException {
        if (atEnd) return -1;
        boolean finish = false;
        int ret = 0;
        while (!finish) {
            ret = currentStream.read();
            if (ret == -1) {
                if (!nextStream()) {
                    return -1;
                }
            } else {
                finish = true;
            }
        }
        return ret;
    }
}
