package com.autoclicker.app.service;

interface IShellService {
    void destroy() = 16777114; // Shizuku 服务器保留方法

    // 一次性命令
    String exec(String command) = 1;
    void execAsync(String command) = 2;
    boolean isAlive() = 3;

    // 流式命令 (用于 getevent 等持续输出)
    void startStream(String command) = 4;
    String readStream() = 5;
    void stopStream() = 6;

    // 文件读取 (用于截屏等二进制文件)
    byte[] readFile(String path) = 7;
}
