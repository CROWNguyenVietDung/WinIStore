package com.winistore.win;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WinIStoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(WinIStoreApplication.class, args);
        System.out.println("WinIStore Đăng nhập: http://localhost:8080/login.html");
        System.out.println("WinIStore User (chưa đăng nhập): http://localhost:8080/user.html");
    }

}
