package com.xiaotao.saltedfishcloud.dao.jpa;

import com.xiaotao.saltedfishcloud.entity.po.CollectionField;
import com.xiaotao.saltedfishcloud.entity.po.CollectionInfo;
import com.xiaotao.saltedfishcloud.utils.ByteSize;
import com.xiaotao.saltedfishcloud.utils.SecureUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CollectionInfoRepositoryTest {
    @Autowired
    private CollectionInfoRepository repository;

    @Test
    public void testUpdateState() {

        System.out.println(repository.updateState());
    }

    @Test
    public void testHighConcurrent() throws BrokenBarrierException, InterruptedException {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, 2);
        CollectionInfo info = new CollectionInfo(1, "admin", "测试高并发安全", "1", "1", new Date(), null);
        info.setExpiredAt(calendar.getTime());
        info.setAllowMax(10000);
        info.setAvailable(10000);
        repository.save(info);
        AtomicInteger integer = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(1001);
        for (int i = 0; i < 1000; i++) {
            new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    try {
                        CollectionInfo info1 = repository.findById(info.getId()).get();
                        if (info1.getAvailable() == 0) break;
                        if (repository.consumeCount(info1.getId(), info1.getAvailable()) == 1) {
                            System.out.println("消费成功： " + info1.getAvailable());
                            integer.incrementAndGet();
                        }
                    } catch (Exception e) {e.printStackTrace();}
                }
                try {
                    barrier.await();
                } catch (Exception ignore) { }
            }).start();
        }
        barrier.await();
        assertTrue( integer.get() <= 10000);
        System.out.println("实际消费：" + integer.get());
    }

    @Test
    public void testAdd() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR, 24);
        CollectionInfo info = new CollectionInfo(2, "admin", "测试收集", "啦啦啦", "root", calendar.getTime(), null);
        info.setMaxSize(ByteSize._1MiB * 512L);
        repository.save(info);
        assertTrue(repository.findAll().size() > 0);

        Collection<CollectionField> fields = new ArrayList<>();
        fields.add(new CollectionField()
                .setName("姓名").setDescribe("你的姓名").setType(CollectionField.Type.TEXT));

        fields.add(new CollectionField()
                .setName("班级").setType(CollectionField.Type.OPTION).addOption("1班").addOption("2班").addOption("3班"));

        info.setField(fields);
        repository.save(info);
        System.out.println(info.getId());
    }
}
