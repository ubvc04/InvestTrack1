package com.examly.springapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.examly.springapp.model.InvestmentInquiry;
import com.examly.springapp.model.User;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String username;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtpEmail(
            String email,
            String otp) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(email);
        message.setSubject(
                "InvestTrack OTP Verification");

        message.setText(
                "Your OTP for InvestTrack registration is: "
                        + otp
                        + "\n\nDo not share this OTP with anyone.");

        mailSender.send(message);
    }

    public void sendPasswordRecoveryOtpEmail(String email, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("InvestTrack Password Recovery OTP");
        message.setText("Your InvestTrack password recovery OTP is: " + otp
                + "\n\nThis OTP expires soon and must not be shared.");
        mailSender.send(message);
    }

    public void sendInquiryStatusUpdateEmail(User user, InvestmentInquiry inquiry,
                                              String previousStatus, String currentStatus) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalStateException("Cannot send inquiry notification: inquiry owner has no email address");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail());
        message.setSubject("InvestTrack - Your Inquiry Status Has Been Updated");
        message.setText("Hello " + user.getUsername() + ",\n\n"
                + "Your inquiry status has been updated.\n\n"
                + "Inquiry: " + (inquiry.getSubject() == null ? inquiry.getInvestment().getName() : inquiry.getSubject()) + "\n"
                + "Previous Status: " + previousStatus + "\n"
                + "Current Status: " + currentStatus + "\n\n"
                + "Your inquiry has been reviewed and its status has been updated.\n\n"
                + "Thank you,\nInvestTrack Support Team");
        try {
            mailSender.send(message);
            logger.info("Inquiry status notification sent for inquiry {} to {}",
                    inquiry.getInquiryId(), user.getEmail());
        } catch (RuntimeException exception) {
            logger.error("Failed to send inquiry status notification for inquiry {} to {}",
                    inquiry.getInquiryId(), user.getEmail(), exception);
            throw exception;
        }
    }
}