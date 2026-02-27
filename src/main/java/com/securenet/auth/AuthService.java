package com.securenet.auth;

import com.securenet.db.DatabaseManager;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.sql.*;

/**
 * Service d'authentification.
 * Hash du mot de passe : SHA-256.
 */
public class AuthService {

    private static final AuthService INSTANCE = new AuthService();
    private User currentUser = null;

    private AuthService() {}
    public static AuthService getInstance() { return INSTANCE; }

    /**
     * Tente de se connecter. Retourne l'utilisateur ou null si échec.
     */
    public User login(String username, String password) {
        String hash = sha256(password);
        String sql  = "SELECT id, username, role FROM users WHERE username=? AND password=?";
        try (PreparedStatement ps = DatabaseManager.getInstance()
                                        .getConnection().prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                currentUser = new User(rs.getInt("id"),
                                       rs.getString("username"),
                                       rs.getString("role"));
                return currentUser;
            }
        } catch (SQLException e) {
            System.err.println("[Auth] Erreur login : " + e.getMessage());
        }
        return null;
    }

    public void logout() { currentUser = null; }

    public User getCurrentUser() { return currentUser; }

    // ─── SHA-256 ───────────────────────────────────────────────────────────
    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes());
            BigInteger bi = new BigInteger(1, digest);
            return String.format("%064x", bi);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 indisponible", e);
        }
    }
}
