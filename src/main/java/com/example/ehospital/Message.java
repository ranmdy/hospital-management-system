package com.example.ehospital;

import java.sql.Timestamp;

public class Message {
    private int id;
    private int senderId;
    private int receiverId;
    private String senderRole;
    private String content;
    private Timestamp sentAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getSenderId() { return senderId; }
    public void setSenderId(int senderId) { this.senderId = senderId; }

    public int getReceiverId() { return receiverId; }
    public void setReceiverId(int receiverId) { this.receiverId = receiverId; }

    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Timestamp getSentAt() { return sentAt; }
    public void setSentAt(Timestamp sentAt) { this.sentAt = sentAt; }
}
