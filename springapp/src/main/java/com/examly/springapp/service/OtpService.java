package com.examly.springapp.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@Service
public class OtpService {

    private final Map<String, String> otpStorage =
            new HashMap<>();

    private final Set<String> verifiedEmails =
            new HashSet<>();

    public String generateOtp(String email) {
        String normalizedEmail = normalize(email);

        String otp = String.valueOf(
                100000 + new Random().nextInt(900000));

        otpStorage.put(normalizedEmail, otp);

        return otp;
    }

    public boolean verifyOtp(
            String email,
            String otp) {

        boolean valid = otp != null
                && otp.equals(otpStorage.get(normalize(email)));

        if (valid) {
            verifiedEmails.add(normalize(email));
        }

        return valid;
    }

    public boolean isVerified(String email) {
        return verifiedEmails.contains(normalize(email));
    }

    public void removeVerified(String email) {
        verifiedEmails.remove(normalize(email));
    }

    public void removeOtp(String email) {
        otpStorage.remove(normalize(email));
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
