package fr.Alphart.BungeePlayerCounter.Servers;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

import com.google.gson.Gson;

import fr.Alphart.BungeePlayerCounter.BPC;
import fr.Alphart.BungeePlayerCounter.Servers.Pinger.VarIntStreams.VarIntDataInputStream;
import fr.Alphart.BungeePlayerCounter.Servers.Pinger.VarIntStreams.VarIntDataOutputStream;

public class Pinger implements Runnable {
    private static Gson gson;
	private InetSocketAddress address;
	private String parentGroupName;
	private boolean online = false;
	private int maxPlayers = -1;

	public Pinger(final String parentGroupName, final InetSocketAddress address) {
	    if(gson == null){
	        loadGson();
	        try{
	            gson = new Gson();
	            BPC.debug("Gson was loaded with success !");
	        }catch(final Throwable t){
	            BPC.severe("Gson cannot be downloaded or loaded ... Please update to spigot 1.8.3 or earlier", t);
	        }
	    }
		this.parentGroupName = parentGroupName;
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
		    final PingResponse response = ping(address, 1000);
		    online = true;
		    maxPlayers = response.getPlayers().getMax();
		} catch (IOException e) {
			if (!(e instanceof ConnectException) && !(e instanceof SocketTimeoutException)) {
			    BPC.severe("An unexcepted error occured while pinging " + parentGroupName + " server", e);
			}
			online = false;
		}
	}
    public static PingResponse ping(final InetSocketAddress host, final int timeout) throws IOException{
        Socket socket = null;
        try{
            socket = new Socket();
            OutputStream outputStream;
            VarIntDataOutputStream dataOutputStream;
            InputStream inputStream;
            InputStreamReader inputStreamReader;
    
            socket.setSoTimeout(timeout);
    
            socket.connect(host, timeout);
    
            outputStream = socket.getOutputStream();
            dataOutputStream = new VarIntDataOutputStream(outputStream);
    
            inputStream = socket.getInputStream();
            inputStreamReader = new InputStreamReader(inputStream);
    
            // Write handshake, protocol=4 and state=1
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            VarIntDataOutputStream handshake = new VarIntDataOutputStream(b);
            handshake.writeByte(0x00);
            handshake.writeVarInt(4);
            handshake.writeVarInt(host.getHostString().length());
            handshake.writeBytes(host.getHostString());
            handshake.writeShort(host.getPort());
            handshake.writeVarInt(1);
            dataOutputStream.writeVarInt(b.size());
            dataOutputStream.write(b.toByteArray());
    
            // Send ping request
            dataOutputStream.writeVarInt(1);
            dataOutputStream.writeByte(0x00);
            VarIntDataInputStream dataInputStream = new VarIntDataInputStream(inputStream);
            dataInputStream.readVarInt();
            int id = dataInputStream.readVarInt();
            if (id == -1) {
                throw new IOException("Premature end of stream.");
            }
            if (id != 0x00) {
                throw new IOException("Invalid packetID");
            }
            int length = dataInputStream.readVarInt();
            if (length == -1) {
                throw new IOException("Premature end of stream.");
            }
    
            if (length == 0) {
                throw new IOException("Invalid string length.");
            }
            
            // Read ping response
            byte[] in = new byte[length];
            dataInputStream.readFully(in);
            String json = new String(in);
            
            // Send ping packet (to get ping value in ms)
            long now = System.currentTimeMillis();
            dataOutputStream.writeByte(0x09);
            dataOutputStream.writeByte(0x01);
            dataOutputStream.writeLong(now);
    
            // Read ping value in ms
            dataInputStream.readVarInt();
            id = dataInputStream.readVarInt();
            if (id == -1) {
                throw new IOException("Premature end of stream.");
            }
            if (id != 0x01) {
                throw new IOException("Invalid packetID");
            }
            long pingtime = dataInputStream.readLong();
    
            synchronized (gson) {
                final PingResponse response = gson.fromJson(json, PingResponse.class);
                response.setTime((int) (now - pingtime));
                dataOutputStream.close();
                outputStream.close();
                inputStreamReader.close();
                inputStream.close();
                socket.close();
                return response;
            }
        }catch(final IOException e){
            throw e;
        }finally{
            if(socket != null){
                socket.close();
            }
        }
    }
    
    public Gson loadGson(){
        try{
            Class.forName("com.google.gson.Gson");
            return new Gson();
        }catch(final Throwable t){
            BPC.info("Gson wasn't found... Please update to spigot 1.8.3 or earlier."
                    + "BPC will try to dynamically load it.");
        }
        final File bpcFolder = BPC.getInstance().getDataFolder();
        final File gsonPath = new File(bpcFolder + File.separator + "lib" + File.separator
                + "gson.jar");
        new File(bpcFolder + File.separator + "lib").mkdir();

        // Download the driver if it doesn't exist
        if (!gsonPath.exists()) {
            BPC.info("Gson was not found. It is being downloaded, please wait ...");

            final String gsonDL = "https://repo1.maven.org/maven2/com/google/code/gson/gson/2.3.1/gson-2.3.1.jar";
            FileOutputStream fos = null;
            try {
                final ReadableByteChannel rbc = Channels.newChannel(new URL(gsonDL).openStream());
                fos = new FileOutputStream(gsonPath);
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            } catch (final IOException e) {
                BPC.severe("An error occured during the download of Gson.", e);
                return null;
            } finally {
                if(fos != null){
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            BPC.info("Gson has been successfully downloaded.");
        }

        try {
            URLClassLoader systemClassLoader;
            URL gsonUrl;
            Class<URLClassLoader> sysclass;
            gsonUrl = gsonPath.toURI().toURL();
            systemClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            sysclass = URLClassLoader.class;
            final Method method = sysclass.getDeclaredMethod("addURL", new Class[] { URL.class });
            method.setAccessible(true);
            method.invoke(systemClassLoader, new Object[] { gsonUrl });

            return (Gson) Class.forName("com.google.gson.Gson", true, systemClassLoader).newInstance();
        } catch (final Throwable t) {
            BPC.severe("Gson cannot be loaded.", t);
        }
        return null;
    }
    
    @Getter
    public class PingResponse {
        private String description;
        private Players players;
        private Version version;
        private String favicon;
        @Setter
        private int time;
        
        public boolean isFull(){
            return players.max <= players.online;
        }
        
        @Getter
        public class Players {
            private int max;
            private int online;
            private List<Player> sample;
            
            @Getter
            public class Player {
                private String name;
                private String id;

            }
        }
        
        @Getter
        public class Version {
            private String name;
            private String protocol;
        }
    }
    
    static class VarIntStreams {
        /**
         * Enhanced DataIS which reads VarInt type
         */
        public static class VarIntDataInputStream extends DataInputStream{

            public VarIntDataInputStream(final InputStream is) {
                super(is);
            }
            
            public int readVarInt() throws IOException {
                int i = 0;
                int j = 0;
                while (true) {
                    int k = readByte();
                    i |= (k & 0x7F) << j++ * 7;
                    if (j > 5)
                        throw new RuntimeException("VarInt too big");
                    if ((k & 0x80) != 128)
                        break;
                }
                return i;
            }
            
        }
        /**
         * Enhanced DataOS which writes VarInt type
         */
        public static class VarIntDataOutputStream extends DataOutputStream{

            public VarIntDataOutputStream(final OutputStream os) {
                super(os);
            }
            
            public void writeVarInt(int paramInt) throws IOException {
                while (true) {
                    if ((paramInt & 0xFFFFFF80) == 0) {
                        writeByte(paramInt);
                        return;
                    }

                    writeByte(paramInt & 0x7F | 0x80);
                    paramInt >>>= 7;
                }
            }
        }
    }
    
}