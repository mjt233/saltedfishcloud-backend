package com.saltedfishcloud.ext.ftpserver;

import org.apache.ftpserver.ftplet.FtpException;

public interface FtpService {
    /**
     * 停止FTP服务。
     * 若没有正在运行的FTP服务，则会忽略本次操作。
     */
    void stop();

    /**
     * 加载参数并启动FTP服务。若服务器已在运行，则会忽略该动作
     */
    void start() throws FtpException;

    /**
     * 重新启动FTP服务。
     * 如果已有一个运行中的FTP服务则会先将其关闭。
     */
    void restart() throws FtpException;

    /**
     * 判断当前是否正在运行FTP服务
     */
    boolean isRunning();
}
