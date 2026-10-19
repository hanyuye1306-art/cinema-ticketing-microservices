package com.gouyan.web.controller.bill;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.anji.captcha", "com.gouyan"})
@EnableFeignClients(basePackages = "com.gouyan.web.controller.bill.client")

public class GouyanBillApplication {

    public static void main(String[] args) {
        SpringApplication.run(GouyanBillApplication.class, args);
    }

}
