package net.ebii.geyservc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public class GeyserVC extends JavaPlugin {

    private DatagramSocket socket;
    private InetAddress address;
    
    // Loaded from config.yml
    private int udpPort; 
    private String baseUrl;
    
    // Variable to hold the Node.js process
    private Process nodeProcess;

    @Override
    public void onEnable() {
        // Generate config.yml from resources if it doesn't exist
        saveDefaultConfig();

        // Load settings from config.yml
        udpPort = getConfig().getInt("ports.udp", 50000);
        baseUrl = getConfig().getString("url.base", "https://mc.ebii.net");

        // Start the Node.js server
        startNodeServer();

        // Setup UDP communication
        try {
            socket = new DatagramSocket();
            address = InetAddress.getByName("127.0.0.1");
            Bukkit.getScheduler().runTaskTimer(this, this::sendPlayerData, 0L, 1L);
            getLogger().info("[UDP communication started]");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startNodeServer() {
        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }

            File jsFile = new File(getDataFolder(), "bundle.js");

            // Extract bundle.js from the jar to the plugin folder
            if (!jsFile.exists()) {
                try (InputStream in = getResource("bundle.js");
                     FileOutputStream out = new FileOutputStream(jsFile)) {
                    if (in == null) {
                        getLogger().warning("bundle.js was not found in the resources folder! Please build and place it.");
                        return;
                    }
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    getLogger().info("bundle.js has been extracted to the plugin folder.");
                }
            }

            // Load all required environment variables from config.yml
            int wsPort = getConfig().getInt("ports.websocket", 8080);
            int httpPort = getConfig().getInt("ports.http", 3000);
            
            String livekitApiKey = getConfig().getString("livekit.api_key", "");
            String livekitApiSecret = getConfig().getString("livekit.api_secret", "");
            String livekitHost = getConfig().getString("livekit.host", "");
            String roomName = getConfig().getString("livekit.room_name", "ebii_vc");
            
            String xblApiKey = getConfig().getString("api.xbl_key", "");
            String apiBaseUrl = getConfig().getString("api.base_url", "http://localhost:3000");
            int maxDistance = getConfig().getInt("voice.max_distance", 40);

            // Prepare the Node.js process
            ProcessBuilder pb = new ProcessBuilder("node", jsFile.getAbsolutePath());
            pb.directory(getDataFolder());
            pb.redirectErrorStream(true);

            // Inject variables into process.env
            Map<String, String> env = pb.environment();
            env.put("UDP_PORT", String.valueOf(udpPort));
            env.put("WS_PORT", String.valueOf(wsPort));
            env.put("HTTP_PORT", String.valueOf(httpPort));
            
            env.put("LIVEKIT_API_KEY", livekitApiKey);
            env.put("LIVEKIT_API_SECRET", livekitApiSecret);
            env.put("LIVEKIT_HOST", livekitHost);
            env.put("ROOM_NAME", roomName);
            
            env.put("XBL_API_KEY", xblApiKey);
            env.put("API_BASE_URL", apiBaseUrl);
            env.put("MAX_DISTANCE", String.valueOf(maxDistance));

            // Start the process
            nodeProcess = pb.start();
            getLogger().info("[Node.js server started]");

            // Pipe Node.js console output to Minecraft server console
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(nodeProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        getLogger().info("[Node] " + line);
                    }
                } catch (Exception e) {
                    // Ignore errors when the stream closes
                }
            }).start();

        } catch (Exception e) {
            getLogger().severe("Failed to start the Node.js server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("vcurl")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.sendMessage("§a[VC-URL] §f" + generateVcUrl(player));
                return true;
            } else {
                if (args.length != 1) {
                    sender.sendMessage("Usage: /vcurl <player_name>");
                    return true;
                }

                Player target = Bukkit.getPlayer(args[0]);
                if (target == null) {
                    sender.sendMessage("Player '" + args[0] + "' not found or is offline.");
                    return true;
                }

                sender.sendMessage("VC-URL for player [" + target.getName() + "]:");
                sender.sendMessage(generateVcUrl(target));
                return true;
            }
        }
        return false;
    }

    private String generateVcUrl(Player player) {
        String uuid = player.getUniqueId().toString();
        String encodedUuid = Base64.getEncoder().encodeToString(uuid.getBytes(StandardCharsets.UTF_8));
        String cleanBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return cleanBaseUrl + "/vc?id=" + encodedUuid;
    }

    private void sendPlayerData() {
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();

        int bufferSize = 4 + (49 * players.size());
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        
        buffer.putInt(players.size());

        for (Player p : players) {
            UUID uuid = p.getUniqueId();
            buffer.putLong(uuid.getMostSignificantBits());
            buffer.putLong(uuid.getLeastSignificantBits());

            Location loc = p.getLocation();
            buffer.putDouble(loc.getX());
            buffer.putDouble(loc.getY());
            buffer.putDouble(loc.getZ());
            buffer.putFloat(loc.getYaw());
            buffer.putFloat(loc.getPitch());
            
            char gmChar;
            switch (p.getGameMode()) {
                case CREATIVE: gmChar = 'C'; break;
                case ADVENTURE: gmChar = 'A'; break;
                case SPECTATOR: gmChar = 'P'; break;
                case SURVIVAL:
                default: gmChar = 'S'; break;
            }
            buffer.put((byte) gmChar);
        }

        byte[] data = buffer.array();
        try {
            DatagramPacket packet = new DatagramPacket(data, data.length, address, udpPort);
            socket.send(packet);
        } catch (Exception e) {
            // Error handling ignored intentionally for tick performance
        }
    }

    @Override
    public void onDisable() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        if (nodeProcess != null && nodeProcess.isAlive()) {
            nodeProcess.destroy();
            getLogger().info("[Node.js server stopped]");
        }
    }
}