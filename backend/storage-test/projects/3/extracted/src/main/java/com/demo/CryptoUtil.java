package com.demo;
import java.security.MessageDigest;
import java.util.Random;

public class CryptoUtil {
    public String hashPassword(String password) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        return new String(md.digest(password.getBytes()));
    }
    public String generateToken() {
        return "tok-" + new Random().nextInt();
    }
}
