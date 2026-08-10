package com.autoclicker.app.service;

interface IShellService {
    void destroy() = 16777114; // Shizuku 服务器保留方法
    String exec(String command) = 1;
    void execAsync(String command) = 2;
    boolean isAlive() = 3;
}
