package com.sfc.webshell.utils;

import com.xiaotao.saltedfishcloud.utils.OSInfo;
import com.xiaotao.saltedfishcloud.utils.StringUtils;
import lombok.experimental.UtilityClass;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@UtilityClass
public class ProcessUtils {
    private final static List<String> WINDOWS_EXECUTABLE_SUFFIX_LIST = List.of(".exe", ".EXE", ".bat", ".BAT", ".cmd", ".CMD");

    /**
     * 解析一条命令行字符串，拆分为参数列表
     * @param cmd   待解析命令行
     * @return      解析后的参数列表
     */
    public static List<String> parseCommandArgs(String cmd) {
        List<String> res = new ArrayList<>();
        int len = cmd.length();

        // 是否处于字符串中
        boolean inString = false;
        // 是否处于转义中
        boolean inEscape = false;
        // 是否到达一个参数的末尾
        boolean isEnd = false;

        StringBuilder currentArg = new StringBuilder();

        for (int i = 0; i < len; i++) {
            char ch = cmd.charAt(i);
            if (inEscape) {
                if (ch == 'n') {
                    currentArg.append('\n');
                } else if (ch == 't') {
                    currentArg.append('\t');
                } else if (ch == 'r') {
                    currentArg.append('\r');
                } else if (ch == ' ') {
                    currentArg.append(' ');
                } else if (ch == '\\') {
                    currentArg.append('\\');
                } else {
                    currentArg.append('\\').append(ch);
                }
                inEscape = false;
                continue;
            }

            if (ch == '\\') {
                inEscape = true;
                continue;
            }

            if (ch == '"') {
                if (inString) {
                    isEnd = true;
                    inString = false;
                } else {
                    inString = true;
                    continue;
                }
            } else if (ch == ' ' || ch == '\n') {
                if (inString) {
                    currentArg.append(ch);
                    continue;
                } else if (currentArg.length() != 0) {
                    isEnd = true;
                }
            }


            // 到了一个参数判定末尾
            if (isEnd) {
                res.add(currentArg.toString());
                currentArg.setLength(0);
                isEnd = false;
            } else if (ch != ' ') {
                currentArg.append(ch);
            }
        }

        if (currentArg.length() != 0) {
            res.add(currentArg.toString());
        }
        return res;
    }

    /**
     * 通过环境变量PATH解析命令行主命令的可执行命令路径
     * @param workDir   工作目录
     * @param cmd       命令
     * @return          命令的可执行路径
     */
    public static String resolveCmdExecutablePath(String workDir, String cmd) {
        String[] cmds = cmd.split("\\s+", 2);
        if (Files.isExecutable(Paths.get(cmds[0]))) {
            return cmds[0];
        }

        Path curPathFile = Paths.get(workDir).resolve(cmds[0]);
        if (Files.exists(curPathFile)) {
            return curPathFile.toAbsolutePath().toString();
        }
        String splitter = OSInfo.isWindows() ? ";" : ":";
        return Arrays.stream(Optional.ofNullable(System.getenv("PATH")).orElse("")
                .split(splitter))
                .map(envPath -> {
                    Path path = Paths.get(StringUtils.appendPath(envPath, cmds[0]));
                    if (Files.isExecutable(path)) {
                        return path.toString();
                    }
                    if (OSInfo.isWindows()) {
                        return WINDOWS_EXECUTABLE_SUFFIX_LIST
                                .stream()
                                .map(suffix -> path + suffix)
                                .filter(windowsPath -> Files.isExecutable(Paths.get(windowsPath)))
                                .findAny()
                                .orElse(null);
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("找不到命令 " + cmds[0]));

    }
}
