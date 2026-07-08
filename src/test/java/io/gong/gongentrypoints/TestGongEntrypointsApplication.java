package io.gong.gongentrypoints;

import org.springframework.boot.SpringApplication;

public class TestGongEntrypointsApplication {

    public static void main(String[] args) {
        SpringApplication.from(GongEntrypointsApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
