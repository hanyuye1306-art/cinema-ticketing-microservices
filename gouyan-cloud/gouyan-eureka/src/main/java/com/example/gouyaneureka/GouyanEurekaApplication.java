package com.example.gouyaneureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka 注册中心的启动入口。peer1 与 peer2 使用独立端口，并分别配置对端注册中心。
 * 客户端通过注册、续约和拉取注册表维护服务发现信息。
 */
@SpringBootApplication
@EnableEurekaServer
public class GouyanEurekaApplication {

    public static void main(String[] args) {
        SpringApplication.run(GouyanEurekaApplication.class, args);
    }

}
