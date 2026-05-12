package com.ai.askquestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot Application Entry Point
 */
@SpringBootApplication
public class AiAskQuestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAskQuestionApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("Ai-AskQuestion 启动成功!");
        System.out.println("访问地址: http://localhost:8080");
        System.out.println("========================================\n");
    }

}
