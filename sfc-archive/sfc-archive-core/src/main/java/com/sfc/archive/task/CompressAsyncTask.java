package com.sfc.archive.task;

import com.sfc.archive.model.ArchiveParam;
import com.sfc.task.AsyncTask;
import com.sfc.task.prog.ProgressRecord;
import lombok.Getter;

import java.io.OutputStream;

/**
 * 压缩任务
 */
public class CompressAsyncTask implements AsyncTask {
    /**
     * 压缩参数
     */
    @Getter
    private final ArchiveParam archiveParam;

    /**
     * 原始参数
     */
    @Getter
    private final String originParams;

    public CompressAsyncTask(ArchiveParam archiveParam, String originParams) {
        this.archiveParam = archiveParam;
        this.originParams = originParams;
    }

    @Override
    public void execute(OutputStream logOutputStream) {

    }

    @Override
    public void interrupt() {

    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public String getParams() {
        return originParams;
    }

    @Override
    public ProgressRecord getProgress() {
        return null;
    }
}
