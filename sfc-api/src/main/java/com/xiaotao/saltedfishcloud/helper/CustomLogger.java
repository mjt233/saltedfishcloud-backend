package com.xiaotao.saltedfishcloud.helper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;

/**
 * 自定义日志输出<br>
 * 来自项目 - <a href="https://github.com/mjt233/summerframework">summerframework</a>的com.xiaotao.summerframework.Logger
 *
 */
public class CustomLogger {
    private PrintStream out;

    // 缩略类名
//    private String name;

    public CustomLogger() {
        this(System.out);
    }

    public CustomLogger(OutputStream outputStream) {
        this.out = new PrintStream(outputStream, false, StandardCharsets.UTF_8);
//        try {
//            // 通过创建一个异常来获取函数调用栈，得知实例化者的类名
//            StackTraceElement[] trace = new Throwable().getStackTrace();
//            Class<?> clazz = Class.forName(trace[1].getClassName());
//            this.name = parseClassName(clazz);
//        } catch (ClassNotFoundException e) {
//            e.printStackTrace();
//            this.name = parseClassName(CustomLogger.class);
//        }
    }

    private String parseClassName(Class<?> clazz) {
        String[] names = clazz.getName().split("\\.");
        // 构造缩略显示的类名，只完整显示最后的包名和类名
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < names.length - 2; i++) {
            name.append(names[i], 0, 1).append('.');
        }
        for (int i = names.length - 2; i < names.length; i++) {
            name.append(names[i]).append('.');
        }
        name.setLength(name.length() - 1);
        return name.toString();
    }

    private void printMsg(String type, String msg) {
//        StackTraceElement ele = new Throwable().getStackTrace()[2];
//        out.println("[" + type + "][" + new Date() + "]" + "[" + Thread.currentThread().getName() + "]" +
//                "[" + name + "" +
//                "#" + ele.getMethodName() + "()]: " + msg);
        out.println("[" + type + "]" + "[" + now() + "]" + msg);
    }

    private String now() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.YEAR) + "-" +
                paddingZeroTo10(now.get(Calendar.MONTH)) + "-" +
                paddingZeroTo10(now.get(Calendar.DATE)) + " " +
                paddingZeroTo10(now.get(Calendar.HOUR_OF_DAY)) + ":" +
                paddingZeroTo10 (now.get(Calendar.MINUTE)) + ":" +
                paddingZeroTo10(now.get(Calendar.SECOND));
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
