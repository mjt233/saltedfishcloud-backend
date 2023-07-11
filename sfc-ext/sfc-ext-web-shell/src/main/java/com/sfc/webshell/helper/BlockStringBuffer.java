package com.sfc.webshell.helper;

import java.util.LinkedList;

/**
 * 分块存储的字符缓冲容器，场景：用于收集持续不断产生的字符但只需要最后出现的部分内容，历史数据可不断被丢弃。<br>
 * 能容纳的最大字符数=maxQueueLen * blockSize。<br>
 * 数据块越大，消息被保存和丢弃的粒度就会越大，记录的数据越不连续，但性能更好。数据块越小则反之。<br>
 *
 */
public class BlockStringBuffer {
    private final int maxQueueLen;
    private final int blockSize;
    private final LinkedList<char[]> queue = new LinkedList<>();
    private char[] curBlock;
    private int curBlockIndex;

    /**
     * @param maxQueueLen        最大队列长度
     * @param blockSize          每个数据块的大小
     */
    public BlockStringBuffer(int maxQueueLen, int blockSize) {
        this.maxQueueLen = maxQueueLen;
        this.blockSize = blockSize;
        createBlock();
    }

    public BlockStringBuffer() {
        this.maxQueueLen = 512;
        this.blockSize = 1024;
        createBlock();
    }

    public synchronized long length() {
        return (long) queue.size() * blockSize - (blockSize - curBlockIndex);
    }

    public synchronized BlockStringBuffer append(char[] chars) {
        return append(chars, 0, chars.length);
    }

    public synchronized BlockStringBuffer append(String str) {
        return append(str.toCharArray());
    }

    /**
     * 添加字符数组
     */
    public synchronized BlockStringBuffer append(char[] chars, int offset, int length) {
        int readIndex = offset;
        int curBlockFreeSize;
        int needRead = length;
        while (needRead > 0) {
            curBlockFreeSize = blockSize - curBlockIndex;
            if (curBlockFreeSize == 0) {
                createBlock();
            }

            int copySize = Math.min(needRead, curBlockFreeSize);

            System.arraycopy(chars, readIndex, curBlock, curBlockIndex, copySize);

            readIndex += copySize;
            needRead -= copySize;
            curBlockIndex += copySize;
        }
        return this;
    }

    /**
     * 添加单个元素
     */
    public synchronized void append(char ch) {
        if (curBlockIndex == blockSize) {
            createBlock();
        }
        curBlock[curBlockIndex++] = ch;
    }

    /**
     * 新建一个块，并重置当前游标
     */
    private synchronized void createBlock() {
        curBlock = new char[blockSize];
        queue.add(curBlock);
        if (queue.size() > maxQueueLen) {
            queue.poll();
        }
        curBlockIndex = 0;
    }

    @Override
    public synchronized String toString() {
        StringBuilder sb = new StringBuilder();
        for (char[] chars : queue) {
            if (chars == curBlock) {
                sb.append(chars, 0, curBlockIndex);
            } else {
                sb.append(chars);
            }
        }
        return sb.toString();
    }
}
