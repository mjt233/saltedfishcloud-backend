package com.xiaotao.saltedfishcloud.helper.http;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.StreamUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * 带边界检测的缓冲区输入流，读取数据时如果读取到了边界，read方法则会返回-1防止越过边界。直到手动调用{@link #nextBoundary()}方法越过边界，进入下一段数据的读取。
 * <br>内部通过循环缓冲区队列数组实现，会从原输入流读取数据到缓冲区检测是否存在边界。通常用于解析multipart/form-data的http请求体。
 */
public class BoundaryBufferInputStream extends InputStream {
    /**
     * 缓冲区中的边界数据状态
     */
    public interface BoundaryCheckResultStatus {
        /**
         * 未在缓冲区中找到边界标识
         */
        int NOT_FOUND = 0;

        /**
         * 缓冲区中存在一个完整的边界
         */
        int EXIST = 1;

        /**
         * 在缓冲区中只找到了部分不完整的疑似边界，需要继续从原始流中读取后续内容确认是否为边界
         */
        int INCOMPLETE = 2;
    }


    /**
     * 边界检查结果
     *
     * @param status        边界状态, {@link BoundaryCheckResultStatus}
     * @param boundaryIndex 边界标识数据在buffer中的起始索引。当不存在边界标识时为-1
     * @param boundary 边界标识数据样本
     */
    public record BoundaryCheckResult(int status, int boundaryIndex, byte[] boundary) {

    }

    /**
     * 缓冲区数组
     */
    private final byte[] buf;

    /**
     * 当前待消费数据起始索引
     */
    private int beginIndex = 0;

    /**
     * 当前待消费数据结束索引
     */
    private int endIndex = 0;

    /**
     * 当前待消费的下一个边界数据之前的数据结束索引
     */
    private int dataEndIndex = 0;

    /**
     * 最近一次检查边界结果
     */
    private BoundaryCheckResult lastBoundaryCheckResult;

    /**
     * 是否已到达终止边界
     */
    @Getter
    private boolean isEof;

    private final InputStream inputStream;
    private final byte[] boundaryBytes;
    private final byte[] eofBoundaryBytes;

    /**
     * @param inputStream      源输入流
     * @param bufferSize       缓冲区大小
     * @param boundaryBytes    分割边界标识
     * @param eofBoundaryBytes 边界数据结束标识
     */
    public BoundaryBufferInputStream(InputStream inputStream, int bufferSize, byte[] boundaryBytes, byte[] eofBoundaryBytes) {
        Objects.requireNonNull(boundaryBytes);
        Objects.requireNonNull(eofBoundaryBytes);
        int minBufferSize = Math.max(eofBoundaryBytes.length, boundaryBytes.length) + 2;
        if (bufferSize < minBufferSize) {
            throw new IllegalArgumentException("buffer size is too small. min buffer size is:" + minBufferSize);
        }

        this.inputStream = inputStream;
        this.boundaryBytes = boundaryBytes;
        this.eofBoundaryBytes = eofBoundaryBytes;
        this.buf = new byte[bufferSize];
    }

    /**
     * 是否已抵达数据边界
     */
    public boolean isAtBoundary() {
        return lastBoundaryCheckResult != null && lastBoundaryCheckResult.boundaryIndex == beginIndex;
    }

    /**
     * 获取循环队列缓冲区的剩余空闲空间
     */
    private int getAvailableSpace() {
        if (beginIndex <= endIndex) {
            return (buf.length - 1) - (endIndex - beginIndex);
        } else {
            return beginIndex - endIndex - 1;
        }
    }

    /**
     * 判断队列是否为空
     */
    private boolean isEmpty() {
        return endIndex == beginIndex;
    }

    /**
     * 判断队列是否已满
     */
    private boolean isFull() {
        return (endIndex + 1) % buf.length == beginIndex;
    }

    /**
     * 获取当前可用空间中，最大的连续空间大小
     */
    private int getAvailableSeqSpace() {
        if (endIndex < beginIndex) {
//                |0|1|2|3|4|5|6|7| buf.length = 8
//                |---------------|
//                | |$| | | |^| | | beginIndex = 5 endIndex = 1
//                |*|-|-|-|X|*|*|*| seqSpace = beginIndex - endIndex = 4 - 1 = 3
            return beginIndex - endIndex - 1;
        } else if (beginIndex == 0) {
//                |0|1|2|3|4|5|6|7| buf.length = 8
//                |---------------|
//                |^| | | | | |$| | beginIndex = 0 endIndex = 6
//                |*|*|*|*|*|*|-|X| seqSpace = buf.length - endIndex - 1 = 8 - 6 - 1 = 1
            return buf.length - endIndex - 1;
        } else {
//                |0|1|2|3|4|5|6|7| buf.length = 8
//                |---------------|
//                | | | | | | |^|$| beginIndex = 6 endIndex = 7
//                |-|-|-|-|-|X|*|-| seqSpace = 7 - 6 = 1


//                |0|1|2|3|4|5|6|7| buf.length = 8
//                |---------------|
//                | | |^| | |$| | | beginIndex = 2 endIndex = 5
//                |-|X|*|*|*|-|-|-| seqSpace = buf.length - endIndex = 8 - 5 = 3

//                |0|1|2|3|4|5|6|7| buf.length = 8
//                |---------------|
//                | | |^| | | | | | beginIndex = 2
//                | | |$| | | | | | endIndex = 2
//                |-|X|-|-|-|-|-|-| seqSpace = buf.length - endIndex = 8 - 2 = 6
            return buf.length - endIndex;
        }
    }

    /**
     * 从原始输入流中抓取数据到缓冲区，尝试填满整个缓冲区
     */
    private void fetchData() throws IOException {
        if (this.isEof || (!isAtBoundary() && !isEmpty())) {
            return;
        }
        int availableSpace = this.getAvailableSpace();
        if (availableSpace == 0) {
            return;
        }

        while (availableSpace > 0) {
            int seqSpace = this.getAvailableSeqSpace();
            if (seqSpace == 0) {
                break;
            }
            int actualReadSize = inputStream.read(buf, endIndex, seqSpace);
            if (actualReadSize == -1) {
                break;
            }
            endIndex = (endIndex + actualReadSize) % buf.length;
            availableSpace -= actualReadSize;
        }

        if (lastBoundaryCheckResult == null || lastBoundaryCheckResult.status != BoundaryCheckResultStatus.EXIST) {
            this.updateDataEndIndex();
        }
    }


    /**
     * 更新缓冲区中在第一个边界之前，数据的结束索引
     */
    private void updateDataEndIndex() {
        BoundaryCheckResult checkResult = this.checkBoundary(boundaryBytes);

        if (checkResult.status == BoundaryCheckResultStatus.NOT_FOUND) {
            checkResult = this.checkBoundary(eofBoundaryBytes);
        } else if (checkResult.status == BoundaryCheckResultStatus.INCOMPLETE) {
            // 普通边界 和 终止边界可能存在重合的地方，当找到了不完整的普通边界时，实际上可能是终止边界
            BoundaryCheckResult eofCheckResult = this.checkBoundary(eofBoundaryBytes);
            if (eofCheckResult.status == BoundaryCheckResultStatus.EXIST) {
                checkResult = eofCheckResult;
            }
        }

        if (checkResult.status == BoundaryCheckResultStatus.NOT_FOUND) {
            dataEndIndex = endIndex;
        } else {
            dataEndIndex = checkResult.boundaryIndex;
        }
        lastBoundaryCheckResult = checkResult;
    }

    /**
     * 获取缓冲区中，有效数据读取索引范围。遍历读取数据时，当结束索引位置比起始索引位置小则需要分两轮读取，从起始索引读到数组末尾，再从数组起始读取到结束索引。
     *
     * @param beginReadIndex 缓冲区数据起始索引
     * @param endReadIndex   缓冲区数据结束索引
     * @return 可读取数据的索引范围 可能为 [ [开始1, 结束1), [开始2, 结束2) ] 或 [[开始, 结束)]。结束索引为闭区间。
     */
    private int[][] getReadIndexRange(int beginReadIndex, int endReadIndex) {
        int[][] readRanges;
        if (endReadIndex < beginReadIndex) {
//                |0|1|2|3|4|5|6|7| buf.length = 8
//                |---------------|
//                | |$| | | |^| | | beginReadIndex = 5 endReadIndex = 1
//                |*|-|-|-|X|*|*|*| 需要分两轮读取，读取5、6、7，再读取0
            readRanges = new int[][]{{beginReadIndex, buf.length}, {0, endReadIndex}};
        } else {
            readRanges = new int[][]{{beginReadIndex, endReadIndex}};
        }
        return readRanges;
    }

    /**
     * 检查当前缓冲区中是否存在边界数据。当缓冲区中存在多个边界数据时，只返回第一个边界数据的检查信息。
     *
     * @param boundary 边界数据样例
     * @
     * @return 检查结果
     */
    private BoundaryCheckResult checkBoundary(byte[] boundary) {
        int boundaryIndex = -1;
        int boundaryMatchIndex = 0;
        boolean isMatch = false;

        int[][] readRanges = this.getReadIndexRange(beginIndex, endIndex);
        for (int[] readRange : readRanges) {
            if (isMatch) {
                break;
            }
            int curReadBeginIndex = readRange[0];
            int curReadEndIndex = readRange[1];

            for (; curReadBeginIndex < curReadEndIndex; curReadBeginIndex++) {
                byte b = buf[curReadBeginIndex];
                boolean isCurByteMatch = b == boundary[boundaryMatchIndex];
                if (!isCurByteMatch && boundaryMatchIndex != 0) {
                    // 匹配过程出现不匹配字节时，从头重新开始尝试匹配
                    boundaryIndex = -1;
                    boundaryMatchIndex = 0;
                    isCurByteMatch = b == boundary[boundaryMatchIndex];
                }
                if (isCurByteMatch) {
                    // 缓冲区中发现了匹配的边界数据，记录匹配的边界索引
                    boundaryMatchIndex++;
                    if (boundaryIndex == -1) {
                        boundaryIndex = curReadBeginIndex;
                    }
                    if (boundaryMatchIndex == boundary.length) {
                        isMatch = true;
                        break;
                    }
                }
            }
        }

        if (!isMatch) {
            if (boundaryMatchIndex != 0) {
                return new BoundaryCheckResult(BoundaryCheckResultStatus.INCOMPLETE, boundaryIndex, boundary);
            }
            return new BoundaryCheckResult(BoundaryCheckResultStatus.NOT_FOUND, boundaryIndex, boundary);
        } else {
            return new BoundaryCheckResult(BoundaryCheckResultStatus.EXIST, boundaryIndex, boundary);
        }
    }

    /**
     * 越过边界数据，进入下一段数据读取。<br>
     * 注意：调用该方法时会将下一个边界前未读取的数据丢弃。
     * @return 是否成功越过边界
     */
    public boolean nextBoundary() throws IOException {
        // 丢弃在下个边界前未读取的数据
        StreamUtils.drain(this);

        // 边界前的数据全部读取后，正常情况下缓冲区中肯定是会有完整的已存在的边界信息
        // 这里没有的话可能是原始的输入流中边界信息不完整，或所有数据均已完成读取
        if (lastBoundaryCheckResult == null || lastBoundaryCheckResult.status != BoundaryCheckResultStatus.EXIST) {
            return false;
        }
//                |0|1|2|3|4|5|6|7| buf.length = 8
//                |---------------|
//                |.|$| | | |^| | | beginReadIndex = 5 endReadIndex = 1 boundaryIndex = 5 boundary.length = 3
//                |*|-|-|-|X|*|*|*| beginIndex = boundaryIndex + boundary.length = (5 + 3) % 8 = 0
        // 越过边界，将抵达的边界数据消费掉，进入到该边界之后的数据区域
        markConsume(lastBoundaryCheckResult.boundary().length);

        // 查找并更新越过边界之后的下一个边界信息
        this.updateDataEndIndex();
        return true;
    }


    @Override
    public int read() throws IOException {
        byte[] data = new byte[1];
        int readResult = read(data, 0, 1);
        if (readResult == -1) {
            return -1;
        } else {
            return data[0];
        }
    }

    @Override
    public int read(@NotNull byte[] b, int off, int len) throws IOException {
        int toReadLen = len;
        int targetOffset = off;
        int totalReadSize = 0;
        while (toReadLen > 0) {
            // 从原始输入流抓取数据到缓冲区
            this.fetchData();
            if (this.isEmpty()) {
                break;
            }

            // 从原始输入流抓取数据后，缓冲区剩余的边界内数据依然为空，说明没有数据能够读取了
            if (this.isAtBoundary()) {
                if (lastBoundaryCheckResult.boundary == eofBoundaryBytes) {
                    isEof = true;
                }
                break;
            }

            for (int[] range : this.getReadIndexRange(beginIndex, dataEndIndex)) {
                if (toReadLen == 0) {
                    break;
                }
                int readSize = Math.min(toReadLen, range[1] - range[0]);
                System.arraycopy(buf, range[0], b, targetOffset, readSize);
                totalReadSize += readSize;
                toReadLen -= readSize;
                targetOffset += readSize;
                this.markConsume(readSize);
            }
        }
        return totalReadSize == 0 ? -1 : totalReadSize;
    }

    /**
     * 标记消费队列指定内容长度，更新指针位置
     * @param len   消费数据长度
     */
    private void markConsume(int len) {
        beginIndex += len;
        if (beginIndex == buf.length) {
            beginIndex = 0;
        }
    }

    private static class TerminableByteArrayOutputStream extends ByteArrayOutputStream {
        /**
         * 移除末尾的\r\n 或 \n
         */
        public void term() {
            byte[] buffer = this.buf;
            int count = this.count;

            // 从末尾移除 \r\n 或 \n
            if (count > 0) {
                if (buffer[count - 1] == '\n') {
                    count--;
                }
                if (count > 0 && buffer[count - 1] == '\r') {
                    count--;
                }
                this.count = count;
            }
        }
    }

    /**
     * 读取一行数据。行末尾的\r\n 或 \n会被忽略
     * @return  一行数据。null表示没能读取到任何数据。空字符串表示读取到了只有\r\n或\n的空行。
     */
    public String readLine() throws IOException {
        return this.readLine(null);
    }

    /**
     * 读取一行数据。行末尾的\r\n 或 \n会被忽略
     * @param charset   字符串的编码
     * @return  一行数据。null表示没能读取到任何数据。空字符串表示读取到了只有\r\n或\n的空行。
     */
    public String readLine(Charset charset) throws IOException {
        try(TerminableByteArrayOutputStream output = new TerminableByteArrayOutputStream()) {
            int linkBreakIdx = -1;
            while (linkBreakIdx == -1) {
                // 从原始输入流抓取数据到缓冲区
                this.fetchData();
                if (this.isEmpty()) {
                    break;
                }

                // 从原始输入流抓取数据后，缓冲区剩余的边界内数据依然为空，说明没有数据能够读取了
                if (this.isAtBoundary()) {
                    break;
                }

                for (int[] range : getReadIndexRange(beginIndex, dataEndIndex)) {
                    int endIdx = range[1];
                    // 从缓冲区查找换行符
                    for (int i = range[0]; i < endIdx; i++) {
                        byte b = buf[i];
                        if (b == '\n') {
                            linkBreakIdx = i;
                            break;
                        }
                    }
                    int len;
                    if (linkBreakIdx == -1) {

                        // 没找到换行符，则直接将本次查找范围的全部取走
                        len = range[1] - range[0];
                    } else {

                        // 找到换行符，则只取到换行符为止
                        len = linkBreakIdx - range[0] + 1;
                    }
                    output.write(buf, range[0], len);

                    // 更新循环队列指针
                    markConsume(len);
                    if (linkBreakIdx != -1) {
                        break;
                    }
                }
            }


            if (output.size() == 0 && linkBreakIdx == -1) {
                return null;
            }
            output.term();
            if (charset == null) {
                return output.toString();
            } else {
                return output.toString(charset);
            }
        }
    }

    @Override
    public void close() throws IOException {
        isEof = true;
        beginIndex = 0;
        endIndex = 0;
        dataEndIndex = 0;
        lastBoundaryCheckResult = null;
        inputStream.close();
    }
}
