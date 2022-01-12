package com.xiaotao.saltedfishcloud.utils.captcha;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
class CaptchaGeneratorTest {

    @Test
    void generateOne() throws IOException {
        String saveDir = "C:\\Users\\12079\\test233\\";
        CaptchaInfo info = CaptchaGenerator.generate();
        OutputStream outputStream = Files.newOutputStream(Paths.get( saveDir + info.getCode() + ".png"));
        ImageIO.write(info.getImage(), "png", outputStream);
        outputStream.close();
    }

    @Test
    void generate() throws IOException, BrokenBarrierException, InterruptedException {
        long start = System.currentTimeMillis();
        int threadCnt = 8;
        int task = 10000;
        String saveDir = "C:\\Users\\12079\\test233\\";
        AtomicInteger cnt = new AtomicInteger(0);
        CyclicBarrier barrier = new CyclicBarrier(threadCnt + 1);

        for (int i = 0; i < threadCnt; i++) {
            int target = task/threadCnt;
            new Thread(() -> {
                try {
                    for (int j = 0; j < target; j++) {
                        CaptchaInfo info = CaptchaGenerator.generate();
                        OutputStream outputStream = Files.newOutputStream(Paths.get( saveDir + info.getCode() + ".jpg"));
                        ImageIO.write(info.getImage(), "jpg", outputStream);
                        outputStream.close();
//                        log.info("生成了：{}, 当前数量：{}", info.getCode(), cnt.addAndGet(1));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        barrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
        barrier.await();

        System.out.println(task + "张耗时：" + (System.currentTimeMillis() - start) + "ms");
    }
}
