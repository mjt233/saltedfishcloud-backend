package com.xiaotao.saltedfishcloud.service.breakpoint.entity;

import com.xiaotao.saltedfishcloud.service.breakpoint.utils.PartParser;
import com.xiaotao.saltedfishcloud.service.breakpoint.utils.TaskStorePath;
import lombok.var;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;

/**
 * 任务状态数据，除了元数据本身，还附加了任务的当前完成状态
 */
public class TaskStatMetadata extends TaskMetadata {
    private final ArrayList<Integer> finish = new ArrayList<>();

    public ArrayList<Integer> getFinish() {
        return finish;
    }

    public TaskStatMetadata(TaskMetadata data) throws IOException {
        setTaskId(data.getTaskId());
        setFileName(data.getFileName());
        setLength(data.getLength());
        fresh();
    }


    public void fresh() throws IOException {
        Files.list(TaskStorePath.getRoot(getTaskId()))
            .filter(e -> e.endsWith(".part"))
            .forEach(path -> {
                var p = path.toString().replaceAll(".part", "");
                for (int i : PartParser.parse(p)) {
                    finish.add(i);
                }
            });
    }

}
