package com.saltedfishcloud.ext.ve.model;

import lombok.Data;

import java.util.List;

@Data
public class ProcessWrap {
    private Process process;
    private List<String> args;
}
