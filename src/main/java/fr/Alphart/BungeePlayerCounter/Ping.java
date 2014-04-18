package fr.Alphart.BungeePlayerCounter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.bukkit.craftbukkit.libs.com.google.gson.JsonParser;

/**
 * Thanks to Harry5573OP for helping me with the pinger issue
 * @author double0negative, Harry5573OP, fuzzy_bot(mccore)
 */
public class Ping implements Runnable {
	private InetSocketAddress address;
	private String displayName;
	private boolean online = false;
	private int maxPlayers = -1;

	public Ping(final String name, final InetSocketAddress address) {
		displayName = name;
		this.address = address;
	}
	
	public boolean isOnline() {
		return online;
	}
	



	public int getMaxPlayers() {
		return maxPlayers;
	}
	



	@Override
	public void run() {
		try {
			ping();
		} catch (IOException e) {
			if (!(e instanceof ConnectException) && !(e instanceof SocketTimeoutException)) {
				System.out.println("[BPC] Something goes wrong during the ping of " + displayName
						+ " server. Please report this error :");
				e.printStackTrace();
			}
		}
	}

	public void ping() throws IOException {
		try (Socket socket = new Socket(address.getAddress(), address.getPort())) {
			try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
				try (DataInputStream in = new DataInputStream(socket.getInputStream())) {
					try (ByteArrayOutputStream frame = new ByteArrayOutputStream()) {
						try (DataOutputStream frameOut = new DataOutputStream(frame)) {

							// Handshake
							writeVarInt(0x00, frameOut);
							writeVarInt(4, frameOut);
							writeString(address.getHostName(), frameOut);
							frameOut.writeShort(address.getPort());
							writeVarInt(1, frameOut);
							// Write handshake
							writeVarInt(frame.size(), out);
							frame.writeTo(out);
							frame.reset();

							// Ping
							writeVarInt(0x00, frameOut);
							// Write ping
							writeVarInt(frame.size(), out);
							frame.writeTo(out);
							frame.reset();

							int len = readVarInt(in);
							byte[] packet = new byte[len];
							in.readFully(packet);

							try (ByteArrayInputStream inPacket = new ByteArrayInputStream(packet)) {
								try (DataInputStream inFrame = new DataInputStream(inPacket)) {
									int id = readVarInt(inFrame);
									if (id != 0x00) {
										throw new IllegalStateException("Wrong ping response");
									}

									online = true;
									
									maxPlayers = new JsonParser().parse(readString(inFrame)).getAsJsonObject().get("players").getAsJsonObject().get("max").getAsInt();
								}
							}
						}
					}
				}
			}
		} catch (Exception e) {
			online = false;
			maxPlayers = -1;
		}
	}

	public static void writeString(String s, DataOutput out) throws IOException {
		byte[] b = s.getBytes("UTF-8");
		writeVarInt(b.length, out);
		out.write(b);
	}

	public static String readString(DataInput in) throws IOException {
		int len = readVarInt(in);
		byte[] b = new byte[len];
		in.readFully(b);

		return new String(b, "UTF-8");
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