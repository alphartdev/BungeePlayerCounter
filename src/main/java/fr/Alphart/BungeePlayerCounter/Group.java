package fr.Alphart.BungeePlayerCounter;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

/**
 * This class is used to handle groups when manual display is enabled
 */
public class Group {
	private String displayName;
	// ServersPlayerCount: contains player count of each server's group
	private Map<String, Integer> serversPC;
	private Integer playerCount;
	private Ping ping = null;
	private InetSocketAddress address = null;

	/**
	 * Constructor
	 * 
	 * @param displayName
	 * @param servers
	 */
	public Group(String name, List<String> servers) {
		displayName = ChatColor.translateAlternateColorCodes('&', name);
		if (displayName.length() > 16)
			displayName = displayName.substring(0, 16);
		serversPC = new HashMap<String, Integer>();
		for (String serverName : servers) {
			serversPC.put(serverName, 0);
		}
	}

	public Group(String name, List<String> servers, InetSocketAddress address, BPC plugin){
		displayName = ChatColor.translateAlternateColorCodes('&', name);
		if (displayName.length() > 16)
			displayName = displayName.substring(0, 16);
		serversPC = new HashMap<String, Integer>();
		for (String serverName : servers) {
			serversPC.put(serverName, 0);
		}
		this.address = address;
		ping = new Ping();
		Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, ping, 20L, 20L * BPC.getInstance().getUpdateInterval());
	}

	private void calculatePlayerCount() {
		Integer totalPC = 0;
		for (Integer serverPC : serversPC.values()) {
			totalPC += serverPC;
		}
		playerCount = totalPC;
	}

	/**
	 * Set the player count of a server and then update the total player count
	 * 
	 * @param server
	 * @param playerCount
	 */
	public void updatePlayerCount(String server, Integer playerCount) {
		if (serversPC.containsKey(server)) {
			serversPC.put(server, playerCount);
		}
		calculatePlayerCount();
	}

	public String getName() {
		return displayName;
	}

	public Integer getPlayerCount() {
		return playerCount;
	}

	public boolean isOnline(){
		return (ping != null) ? ping.isOnline() : true; //If state is not checked just set it online
	}
	
	public class Ping implements Runnable {
		private boolean online = false;
		
		public boolean isOnline(){
			return online;
		}
		
		@Override
		public void run() {
			try {
				ping();
			} catch (final IOException e) {
				if(!(e instanceof ConnectException) && !(e instanceof SocketTimeoutException)){
					System.out.println("[BPC] Something goes wrong during the ping of " + displayName
							+ " server. Please report this error :");
					e.printStackTrace();
				}
			}
		}

        private void ping() throws IOException {
            try (Socket socket = new Socket()) {
                socket.setSoTimeout(1000);
                socket.connect(address);
                OutputStream outputStream;
                DataOutputStream dataOutputStream;
                DataInputStream inputStream;
                InputStreamReader inputStreamReader;

                outputStream = socket.getOutputStream();
                dataOutputStream = new DataOutputStream(outputStream);

                inputStream = new DataInputStream(socket.getInputStream());
                inputStreamReader = new InputStreamReader(inputStream);

                // Realize the handshake
                ByteArrayOutputStream frame = new ByteArrayOutputStream();
                DataOutputStream frameOut = new DataOutputStream(frame);

                // Handshake
                writeVarInt(0x00, frameOut);
                writeVarInt(4, frameOut);
                writeString(address.getHostName(), frameOut);
                frameOut.writeShort(address.getPort());
                writeVarInt(1, frameOut);
                // Write handshake
                writeVarInt(frame.size(), dataOutputStream);
                frame.writeTo(dataOutputStream);
                frame.reset();

                // Ping
                writeVarInt(0x00, frameOut);
                // Write ping
                writeVarInt(frame.size(), dataOutputStream);
                frame.writeTo(dataOutputStream);
                frame.reset();

                int len = readVarInt(inputStream);
                byte[] packet = new byte[len];
                inputStream.readFully(packet);

                try (ByteArrayInputStream inPacket = new ByteArrayInputStream(packet)) {
                    try (DataInputStream inFrame = new DataInputStream(inPacket)) {
                        int id = readVarInt(inFrame);
                        if (id != 0x00) {
                            online = false;
                        } else {
                            online = true;
                        }
                    }
                }

                dataOutputStream.close();
                outputStream.close();
                inputStreamReader.close();
                inputStream.close();
                socket.close();
            } catch (final IOException e) {
                online = false;
                throw e;
            }
        }
    }

    public static void writeString(String s, DataOutput out) throws IOException {
        // TODO: Check len - use Guava?
        byte[] b = s.getBytes("UTF-8");
        writeVarInt(b.length, out);
        out.write(b);
    }

    public static int readVarInt(DataInput input) throws IOException {
        int out = 0;
        int bytes = 0;
        byte in;
        while (true) {
            in = input.readByte();

            out |= (in & 0x7F) << (bytes++ * 7);

            if (bytes > 32) {
                throw new RuntimeException("VarInt too big");
            }

            if ((in & 0x80) != 0x80) {
                break;
            }
        }

        return out;
    }

    public static void writeVarInt(int value, DataOutput output) throws IOException {
        int part;
        while (true) {
            part = value & 0x7F;

            value >>>= 7;
            if (value != 0) {
                part |= 0x80;
            }

            output.writeByte(part);

            if (value == 0) {
                break;
            }
        }
    }
}
