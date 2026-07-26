package com.streamerplugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import com.streamerplugin.auth.AuthManager;
import com.streamerplugin.commands.StreamKickCommand;
import com.streamerplugin.kick.KickApiClient;
import com.streamerplugin.kick.KickSessionManager;
import com.streamerplugin.listeners.MenuListener;
import com.streamerplugin.listeners.PlayerListener;

public class StreamerChatPlugin extends JavaPlugin {

    public static final String DEFAULT_CLIENT_ID = "01KDVXPSFEFB93AW07VA0PVN7R";

    private KickApiClient apiClient;
    private KickSessionManager sessionManager;
    private AuthManager authManager;
    private Properties secretsProps = new Properties();

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        Logger logger = getLogger();

        logger.info("[📺] ");
        logger.info("[📺]  ░██████╗████████╗██████╗░███████╗░█████╗░███╗░░░███╗███████╗██████╗░");
        logger.info("[📺]  ██╔════╝╚══██╔══╝██╔══██╗██╔════╝██╔══██╗████╗░████║██╔════╝██╔══██╗");
        logger.info("[📺]  ╚█████╗░░░░██║░░░██████╔╝█████╗░░███████║██╔████╔██║█████╗░░██████╔╝");
        logger.info("[📺]  ░╚═══██╗░░░██║░░░██╔══██╗██╔══╝░░██╔══██║██║╚██╔╝██║██╔══╝░░██╔══██╗");
        logger.info("[📺]  ██████╔╝░░░██║░░░██║░░██║███████╗██║░░██║██║░╚═╝░██║███████╗██║░░██║");
        logger.info("[📺]  ╚═════╝░░░░╚═╝░░░╚═╝░░╚═╝╚══════╝╚═╝░░╚═╝╚═╝░░░░░╚═╝╚══════╝╚═╝░░╚═╝");
        logger.info("[📺]                 StreamerChat v0.1 - Kick Integration");
        logger.info("[📺]  ════════════════════════════════════════════════════════════════════");

        long stepStart = System.currentTimeMillis();
        saveDefaultConfig();
        loadSecrets();
        logger.info(String.format("[📺] (ConfigManager) -> Enabled manager & loaded secrets in %.3fms!",
                (double) (System.currentTimeMillis() - stepStart)));

        stepStart = System.currentTimeMillis();
        String clientId = getCredential("kick.client_id", "KICK_CLIENT_ID", "kick.client_id", DEFAULT_CLIENT_ID);
        String clientSecret = getCredential("kick.client_secret", "KICK_CLIENT_SECRET", "kick.client_secret", "");
        String redirectUri = getConfig().getString("kick.redirect_uri", "http://localhost:8080/auth/callback");

        this.apiClient = new KickApiClient(clientId, clientSecret, redirectUri, logger);
        this.sessionManager = new KickSessionManager(this);
        this.authManager = new AuthManager(this, apiClient, sessionManager);
        logger.info(String.format("[📺] (ApiClient) -> Initialized API client in %.3fms!",
                (double) (System.currentTimeMillis() - stepStart)));

        stepStart = System.currentTimeMillis();
        authManager.start();
        authManager.loadUserSessions();
        logger.info(String.format("[📺] (AuthManager) -> Started HTTP server & loaded sessions in %.3fms!",
                (double) (System.currentTimeMillis() - stepStart)));

        stepStart = System.currentTimeMillis();
        StreamKickCommand streamKickCommand = new StreamKickCommand(this, authManager, sessionManager);
        PluginCommand cmd = getCommand("streamkick");
        if (cmd != null) {
            cmd.setExecutor(streamKickCommand);
            cmd.setTabCompleter(streamKickCommand);
        }
        logger.info(String.format("[📺] (CommandManager) -> Registered /streamkick command in %.3fms!",
                (double) (System.currentTimeMillis() - stepStart)));

        stepStart = System.currentTimeMillis();
        getServer().getPluginManager().registerEvents(new PlayerListener(sessionManager), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        logger.info(String.format("[📺] (EventManager) -> Registered listeners & GUI menu in %.3fms!",
                (double) (System.currentTimeMillis() - stepStart)));

        long totalTime = System.currentTimeMillis() - startTime;
        logger.info(String.format("[📺] Plugin habilitado exitosamente en %dms!", totalTime));
    }

    private void loadSecrets() {
        secretsProps.clear();

        File secretsFile = new File(getDataFolder(), "secrets.properties");
        if (secretsFile.exists()) {
            try (InputStream is = new FileInputStream(secretsFile)) {
                secretsProps.load(is);
                return;
            } catch (Exception e) {
                getLogger().warning("[StreamerChat] No se pudo leer secrets.properties local: " + e.getMessage());
            }
        }

        try (InputStream is = getResource("secrets.properties")) {
            if (is != null) {
                secretsProps.load(is);
            }
        } catch (Exception e) {
        }
    }

    public String getCredential(String propKey, String envKey, String configPath, String defaultValue) {
        String env = System.getenv(envKey);
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }

        if (secretsProps != null && secretsProps.containsKey(propKey)) {
            String val = secretsProps.getProperty(propKey);
            if (val != null && !val.trim().isEmpty() && !val.startsWith("YOUR_")) {
                return val.trim();
            }
        }

        String cfg = getConfig().getString(configPath);
        if (cfg != null && !cfg.trim().isEmpty() && !cfg.startsWith("YOUR_")) {
            return cfg.trim();
        }

        return defaultValue;
    }

    public String getCredentialString(String path, String defaultValue) {
        return getCredential(path, path.toUpperCase().replace('.', '_'), path, defaultValue);
    }

    public void reloadPluginConfig() {
        reloadConfig();
        loadSecrets();
    }

    @Override
    public void onDisable() {
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
        if (authManager != null) {
            authManager.stop();
        }
        getLogger().info("[📺] [StreamerChat] Plugin deshabilitado.");
    }

    public KickApiClient getApiClient() {
        return apiClient;
    }

    public KickSessionManager getSessionManager() {
        return sessionManager;
    }

    public AuthManager getAuthManager() {
        return authManager;
    }
}
