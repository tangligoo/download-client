package com.ztxa.client.database;

import java.sql.*;

public class ConfigDAO {
    private final DatabaseManager dbManager;
    
    public ConfigDAO() {
        this.dbManager = DatabaseManager.getInstance();
    }
    
    public void saveConfig(String key, String value) {
        String sql = "INSERT OR REPLACE INTO config (key, value, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)";
        
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }
    
    public String getConfig(String key, String defaultValue) {
        String sql = "SELECT value FROM config WHERE key = ?";
        
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, key);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getString("value");
            }
        } catch (SQLException e) {
            System.err.println("Failed to get config: " + e.getMessage());
        }
        
        return defaultValue;
    }
    
    public int getIntConfig(String key, int defaultValue) {
        String value = getConfig(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    public void deleteConfig(String key) {
        String sql = "DELETE FROM config WHERE key = ?";
        
        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to delete config: " + e.getMessage());
        }
    }
}
