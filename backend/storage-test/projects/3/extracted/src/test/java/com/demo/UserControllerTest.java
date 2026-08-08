package com.demo;
import org.junit.jupiter.api.Test;

class UserControllerTest {
    @Test
    void listUsersReturnsOk() {
        given().when().get("/api/users").then().statusCode(200);
    }
}
