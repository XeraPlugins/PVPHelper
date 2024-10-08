package me.ian.command;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.ian.PVPHelper;
import me.ian.command.commands.ReloadConfig;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * @author SevJ6
 */
@RequiredArgsConstructor
@Getter
public class CommandRegistrar {

    private final List<CommandData> commands;

    public CommandRegistrar() {
        commands = new ArrayList<>();
        commands.add(new ReloadConfig());
    }

    public void registerAllCommands() {
        commands.forEach(this::registerCommand);
    }

    public void registerCommand(CommandData command) {
        PluginCommand bukkitCommand = Bukkit.getPluginCommand(command.getCommandName());
        if (command.isAdminOnly()) bukkitCommand.setPermission("commands.administrator");
        CommandExecutor executor = command.getCommandExecutor();
        bukkitCommand.setExecutor(executor);

        if (command.getCommandExecutor() instanceof TabExecutor) {
            bukkitCommand.setTabCompleter((TabCompleter) executor);
        }

        PVPHelper.INSTANCE.getLogger().log(Level.INFO, String.format("Registered command %s", command.getCommandName()));
    }
}
