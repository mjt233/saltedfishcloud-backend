package com.xiaotao.saltedfishcloud.utils.captcha;

import com.xiaotao.saltedfishcloud.entity.CaptchaInfo;
import com.xiaotao.saltedfishcloud.service.CaptchaGenerator;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

class CaptchaGeneratorTest {

    @Test
    void generateOne() throws IOException {
        CaptchaGenerator generator = new CaptchaGeneratorDefaultImpl();
        String saveDir = "C:\\Users\\12079\\test233\\";
        CaptchaInfo info = generator.generate();
        OutputStream outputStream = Files.newOutputStream(Paths.get( saveDir + info.getCode() + ".png"));
        ImageIO.write(info.getImage(), "png", outputStream);
        outputStream.close();
    }

    @Test
    void generate() throws IOException, BrokenBarrierException, InterruptedException {
        CaptchaGenerator generator = new CaptchaGeneratorDefaultImpl();
        long start = System.currentTimeMillis();
        int threadCnt = 8;
        int task = 10000;
        String saveDir = "C:\\Users\\12079\\test233\\";
        CyclicBarrier barrier = new CyclicBarrier(threadCnt + 1);

        for (int i = 0; i < threadCnt; i++) {
            int target = task/threadCnt;
            new Thread(() -> {
                try {
                    for (int j = 0; j < target; j++) {
                        CaptchaInfo info = generator.generate();
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
