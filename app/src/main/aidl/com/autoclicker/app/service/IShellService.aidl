package com.autoclicker.app.service;

interface IShellService {
    String exec(String command);
    void execAsync(String command);
    boolean isAlive();
}
