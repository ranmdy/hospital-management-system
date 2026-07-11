package com.example.ehospital;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class ChatDAO {

    public void sendMessage(int senderId, int receiverId, String senderRole, String content) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "INSERT INTO messages (sender_id, receiver_id, sender_role, content) VALUES (?, ?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, senderId);
            stmt.setInt(2, receiverId);
            stmt.setString(3, senderRole);
            stmt.setString(4, content);
            stmt.executeUpdate();
            conn.close();
        } catch (Exception e) {
            System.out.println("Send message failed: " + e.getMessage());
        }
    }

    public List<Message> getMessages(int userId1, int userId2) {
        List<Message> list = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getConnection();
            String sql = "SELECT * FROM messages WHERE " +
                    "(sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?) " +
                    "ORDER BY sent_at ASC";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId1);
            stmt.setInt(2, userId2);
            stmt.setInt(3, userId2);
            stmt.setInt(4, userId1);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Message m = new Message();
                m.setId(rs.getInt("id"));
                m.setSenderId(rs.getInt("sender_id"));
                m.setReceiverId(rs.getInt("receiver_id"));
                m.setSenderRole(rs.getString("sender_role"));
                m.setContent(rs.getString("content"));
                m.setSentAt(rs.getTimestamp("sent_at"));
                list.add(m);
            }
            conn.close();
        } catch (Exception e) {
            System.out.println("Get messages failed: " + e.getMessage());
        }
        return list;
    }
}
