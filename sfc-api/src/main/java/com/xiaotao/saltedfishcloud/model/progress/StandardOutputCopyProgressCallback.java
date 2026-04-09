package com.xiaotao.saltedfishcloud.model.progress;

public class StandardOutputCopyProgressCallback implements CopyProgressCallback {
    private final CopyProgressRecord record = new CopyProgressRecord();
    @Override
    public CopyProgressRecord getProgressRecord() {
        return record;
    }

    @Override
    public void onFileStart(FileTransferItem record) {
        System.out.println("[Copy Start]" + record.getFrom() + " ==> " + record.getTo());
    }

    @Override
    public void onFileComplete(FileTransferItem record) {
        System.out.println("[Copy Complete]" + record.getFrom() + " ==> " + record.getTo());
    }

    @Override
    public void onAdditionalEvent(CopyProgressEvent event) {
        System.out.println(event.toString());
    }
}
