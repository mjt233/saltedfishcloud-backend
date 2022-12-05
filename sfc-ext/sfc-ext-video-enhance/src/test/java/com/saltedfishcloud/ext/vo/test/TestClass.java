package com.saltedfishcloud.ext.vo.test;

import com.saltedfishcloud.ext.ve.model.VEProperty;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class TestClass {
    private final String VIDEO_PATH = "C:\\Users\\xiaotao\\Downloads\\JoJo's Bizarre Adventure - S05E25 - DUAL 1080p WEB H.264 -NanDesuKa (NF).mkv";
    private final String FFMPEG_PATH = "C:\\DATA\\soft\\ffmpeg-master-latest-win64-gpl\\ffmpeg-master-latest-win64-gpl\\bin";
    private final VEProperty PROPERTY = VEProperty.builder().ffmpegPath(FFMPEG_PATH).build();

    @Test
    public void testExecuteInCMD() throws IOException {
        List<String> args = new ArrayList<>();
        args.add(PROPERTY.getFFProbeExecPath());
        args.add("-i");
        args.add(VIDEO_PATH);

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(args);
        processBuilder.redirectErrorStream(true);

        Process start = processBuilder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(start.getInputStream()));
        String res;
        while ( (res = reader.readLine()) != null ) {
            System.out.println(res);
        }
    }
}
