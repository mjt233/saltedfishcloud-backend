# 定时任务

## 1. 简述

咸鱼云网盘的定时任务功能并未做什么修改和调整，仅在SpringBoot原有的基础上进行了AOP增强以支持集群模式下的定时任务简单调度。

## 2. 单机任务

作为定时任务的类需要注入到Spring容器中，建议在类上使用`@Component`注解或在配置类的Bean工厂方法中注入

单机定时任务在集群的各节点上是相互独立互不影响的。

### 2.1 固定间隔执行

```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CustomSchedule {

    /**
     * 固定每5秒间隔执行
     */
    @Scheduled(fixedRate = 5000)
    public void scheduleMethod() { }
}
```

### 2.1 Cron表达式

表达式按空格分割，从左到右分别表示：

- 秒(0~59)
- 分(0~59)
- 时(0~23)
- 日(1~31)
- 月(1~12)
- 星期(1~7)

```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CustomSchedule {

    /**
     * 固定每天12点钟时，每10秒间隔执行
     */
    @Scheduled(cron = "*/10 * 12 * * *")
    public void scheduleMethod() { }
}
```


详细可参考`org.springframework.scheduling.support.CronSequenceGenerator`的Java Document描述

## 3. 集群任务

### 3.1 简述

在原单机任务的基础上，定时任务的方法上多加一个`@ClusterScheduleJob`注解即可。

被该注解标注的方法在集群中，同一时刻触发的定时任务只会有一个节点得到执行。

> **TIPS**  
> 集群定时任务会被封装为Spring异步方法调用，不会阻塞定时任务的线程，因此同一个任务可能会出现并行执行的情况。（即上次任务还未执行完成，又开始了新的一次执行）  
> 
> 后续会考虑添加并行控制，在这之前若顾虑该情况，可自行使用redisson加锁执行

### 3.2 代码示例

```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CustomSchedule {

    /**
     * 固定每天12点钟时，每10秒间隔执行
     */
    @Scheduled(cron = "*/10 * 12 * * *")
    @ClusterScheduleJob("my_schedule_job")
    public void scheduleMethod() { }
}
```

> **TIPS**  
> 注解的`value`属性是集群定时任务的唯一标识，应当确保在同一个定时任务方法上使用相同的值，否则会导致调度混乱。

### 3.3 任务调度

#### Cron表达式任务

对于Cron表达式的定时任务，应确保集群所有节点的系统时间一致。设定的执行时间到来时，随机一个节点触发执行。

#### 固定间隔任务

对于使用`fixedRate`的固定速度间隔定时任务，只要集群节点未下线，该任务就会一直在该节点上执行，直到节点下线。

若该节点下线，会导致该任务延期，最坏的情况下会延期一次`fixedRate`指定的整个间隔持续时长。
