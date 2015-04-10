package fr.Alphart.BungeePlayerCounter.PluginMessage;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.google.common.collect.Sets;

import fr.Alphart.BungeePlayerCounter.BPC;

public class PluginMessageWriter {
    private final String channel;
    
    public PluginMessageWriter(final String channelName){
        this.channel = channelName;
        
        final Set<String> channels = Sets.newHashSet();
        channels.add("BungeeCord"); // Always need to register to the B/C channel in order to send GetServer message
        if(!channelName.equalsIgnoreCase("BungeeCord")){
            channels.add(channelName);
        }
        for(final String channel : channels){
            Bukkit.getMessenger().registerOutgoingPluginChannel(BPC.getInstance(), channel);
            BPC.debug("Registering outgoing plugin message channel. Channel=%s", channel);
        }
    }
    
    public void sendGetServersListMessage(final Player player){
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("GetServers");
            
            player.sendPluginMessage(BPC.getInstance(), "BungeeCord", b.toByteArray());
            BPC.debug("Sending GetServers message.");
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public void sendGetCurrentServerMessage(final Player player){
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("GetServer");
            
            player.sendPluginMessage(BPC.getInstance(), "BungeeCord", b.toByteArray());
            BPC.debug("Sending GetServer message.");
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public void sendGetPlayerCountMessage(final Player p, final Collection<String> serversToQuery){
        try {
            for (final String server : serversToQuery) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(b);
                out.writeUTF("PlayerCount");
                out.writeUTF(server);
                p.sendPluginMessage(BPC.getInstance(), channel, b.toByteArray());
                BPC.debug("Sending PlayerCount message for server=%s", server);
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}