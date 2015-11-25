package fr.Alphart.BungeePlayerCounter;

import static fr.Alphart.BungeePlayerCounter.BPC.__;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.google.common.base.Preconditions;

public class BaseCommands implements CommandExecutor{
    public final static String DISPLAY_PERM = "bungeeplayercounter.display";
    public final static String RELOAD_PERM = "bungeeplayercounter.reload";
    public final static String TOGGLE_PERM = "bungeeplayercounter.toggle";
    public final static String DEBUG_PERM = "bungeeplayercounter.debug";
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        try{
        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("reload") && sender.hasPermission(RELOAD_PERM)){
                BPC.getInstance().reload();
                Bukkit.broadcast(__("BungeePlayerCounter has been successfully reloaded !"), BaseCommands.RELOAD_PERM);
            }else if (args[0].equalsIgnoreCase("toggle") && sender.hasPermission(TOGGLE_PERM) && sender.hasPermission(DISPLAY_PERM)) {
                Preconditions.checkArgument(sender instanceof Player, "You must be a player to execute this command !");
                BPC.getInstance().getScoreboardHandler().toggleScoreboard((Player) sender);
                sender.sendMessage(__("Scoreboard toggled !"));
            }else if(args[0].equalsIgnoreCase("debug") && sender.hasPermission(DEBUG_PERM)){
                if(BPC.getInstance().toggleDebug()){
                    sender.sendMessage(__("&eDebug mode initialized ..."
                            + " Debug information are going to be written into the &adebug.log&e file in BPC folder."));
                }else{
                    sender.sendMessage(__("&eDebug mode stopped ..."
                            + " You can find the debug information into the &adebug.log&e file in BPC folder."));
                }
            }else{
                throw new IllegalArgumentException("Invalid command or insufficient permission !");
            }
        }
        }catch(final IllegalArgumentException e){
            sender.sendMessage(__("&c" + e.getMessage()));
        }
        return true;
    }
    
}