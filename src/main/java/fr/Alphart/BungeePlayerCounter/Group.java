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

		private int readVarInt(DataInputStream in) throws IOException {
			int i = 0;
			int j = 0;
			while (true) {
				int k = in.readByte();
				i |= (k & 0x7F) << j++ * 7;
				if (j > 5)
					throw new RuntimeException("VarInt too big");
				if ((k & 0x80) != 128)
					break;
			}
			return i;
		}

		private void writeVarInt(DataOutputStream out, int paramInt) throws IOException {
			while (true) {
				if ((paramInt & 0xFFFFFF80) == 0) {
					out.writeByte(paramInt);
					return;
				}

				out.writeByte(paramInt & 0x7F | 0x80);
				paramInt >>>= 7;
			}
		}

		private void ping() throws IOException {
			Socket socket = null;
			try {
				socket = new Socket();
				socket.setSoTimeout(1000);
				socket.connect(address);
				OutputStream outputStream;
				DataOutputStream dataOutputStream;
				InputStream inputStream;
				InputStreamReader inputStreamReader;

				outputStream = socket.getOutputStream();
				dataOutputStream = new DataOutputStream(outputStream);

				inputStream = socket.getInputStream();
				inputStreamReader = new InputStreamReader(inputStream);

				// Realize the handshake
				ByteArrayOutputStream b = new ByteArrayOutputStream();
				DataOutputStream handshake = new DataOutputStream(b);
				handshake.writeByte(0x00);
				writeVarInt(handshake, 4);
				writeVarInt(handshake, address.getHostString().length());
				handshake.writeBytes(address.getHostString());
				handshake.writeShort(address.getPort());
				writeVarInt(handshake, 1);

				writeVarInt(dataOutputStream, b.size());
				dataOutputStream.write(b.toByteArray());

				dataOutputStream.writeByte(0x01); // 0x01 is ping packet id
				dataOutputStream.writeByte(0x00);
				DataInputStream dataInputStream = new DataInputStream(inputStream);
				int id = readVarInt(dataInputStream);
				
				if (id == -1) {
					throw new IOException("End of stream.");
				}
				if (id != 0x00) {
					throw new IOException("Invalid packetID");
				}
				
				int length = readVarInt(dataInputStream);
				if (length == -1) {
					throw new IOException("End of stream.");
				}
				if (length == 0) {
					throw new IOException("Invalid string length.");
				}

				dataOutputStream.close();
				outputStream.close();
				inputStreamReader.close();
				inputStream.close();
				socket.close();
				online = true;
			} catch (final IOException e) {
				online = false;
				throw e;
			} finally {
				socket.close();
			}
		}
	}
}