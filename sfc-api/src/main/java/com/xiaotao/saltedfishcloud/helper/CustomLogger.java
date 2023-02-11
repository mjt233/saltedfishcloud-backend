package com.xiaotao.saltedfishcloud.helper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

/**
 * 自定义输出流日志<br>
 * 来自项目 - <a href="https://github.com/mjt233/summerframework">summerframework</a>的com.xiaotao.summerframework.Logger
 *
 */
public class CustomLogger {
    private final PrintStream out;

    /**
     * 创建一个输出到控制台的日志器
     */
    public CustomLogger() {
        this(System.out);
    }

    /**
     * 创建一个输出到指定流的日志器
     */
    public CustomLogger(OutputStream outputStream) {
        this.out = new PrintStream(outputStream, false, StandardCharsets.UTF_8);
    }

    private void printMsg(String type, String msg) {
        out.println("[" + type + "]" + "[" + now() + "]" + msg);
    }

    private String now() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.YEAR) + "-" +
                paddingZeroTo10(now.get(Calendar.MONTH)) + "-" +
                paddingZeroTo10(now.get(Calendar.DATE)) + " " +
                paddingZeroTo10(now.get(Calendar.HOUR_OF_DAY)) + ":" +
                paddingZeroTo10 (now.get(Calendar.MINUTE)) + ":" +
                paddingZeroTo10(now.get(Calendar.SECOND)) + "." +
                paddingZeroTo10(now.get(Calendar.MILLISECOND));
    }

    private String paddingZeroTo10(int num) {
        return num < 10 ? "0" + num : String.valueOf(num);
    }

    public void info(String msg) {
        printMsg(" INFO", msg);
    }

    public void debug(String msg) {
        printMsg("DEBUG", msg);
    }


    public void warn(String msg) {
        printMsg(" WARN", msg);
    }

    public void error(String msg) {
        printMsg("ERROR", msg);
    }

    public void error(Throwable e) {
        try (StringWriter stringWriter = new StringWriter(); PrintWriter printWriter = new PrintWriter(stringWriter)) {
            e.printStackTrace(printWriter);
            out.println("异常:" + stringWriter);
        } catch (IOException ioException) {
            error(ioException.getMessage());
        };

    }

    public void error(String msg, Throwable e) {
        error(msg);
        error(e);
    }
}
