package com.rybouz.sanciones;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

public class SancionesPlugin extends JavaPlugin {

    // Tiempo: número seguido de s, m, h, d o w (ej: 30m, 2h, 1d)
    private static final Pattern TIEMPO = Pattern.compile("\\d+[smhdw]", Pattern.CASE_INSENSITIVE);

    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "sanciones.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);

        getCommand("sancionmute").setExecutor(this);
        getCommand("sancionban").setExecutor(this);
        getCommand("hist").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase()) {
            case "sancionmute" -> sancionar(sender, args, "sanciones.use", "MUTE", "mute-command");
            case "sancionban"  -> sancionar(sender, args, "sanciones.ban", "BAN", "ban-command");
            case "hist"        -> historial(sender, args);
        }
        return true;
    }

    // /sancionmute <Nick> <Motivo> <Tiempo>  o  /sancionmute <Nick> <Tiempo> <Motivo>
    private void sancionar(CommandSender sender, String[] args, String permiso, String tipo, String clave) {
        String uso = tipo.equals("MUTE") ? "/sancionmute" : "/sancionban";

        if (!sender.hasPermission(permiso)) {
            error(sender, "No tienes permiso.");
            return;
        }
        if (args.length < 3) {
            error(sender, "Uso: " + uso + " <Nick> <Motivo> <Tiempo>");
            return;
        }

        String nick = args[0];

        // Buscamos el tiempo entre el resto de argumentos (de derecha a izquierda)
        List<String> resto = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
        int idxTiempo = -1;
        for (int i = resto.size() - 1; i >= 0; i--) {
            if (TIEMPO.matcher(resto.get(i)).matches()) {
                idxTiempo = i;
                break;
            }
        }
        if (idxTiempo == -1) {
            error(sender, "No encuentro el tiempo (ej: 30m, 2h, 1d).");
            return;
        }

        String tiempo = resto.remove(idxTiempo);
        String motivo = String.join(" ", resto).trim();
        if (motivo.isEmpty()) {
            error(sender, "Falta el motivo.");
            return;
        }

        String plantilla = getConfig().getString(clave);
        String comando = plantilla
                .replace("{nick}", nick)
                .replace("{motivo}", motivo)
                .replace("{tiempo}", tiempo);

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), comando);
        registrar(sender.getName(), nick, tipo, motivo, tiempo);

        sender.sendMessage(Component.text(tipo + " aplicado a " + nick + " (" + motivo + ", " + tiempo + ")",
                NamedTextColor.GREEN));
    }

    // /hist <Nick>  o  /historial <Nick>
    private void historial(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sanciones.hist")) {
            error(sender, "No tienes permiso.");
            return;
        }
        if (args.length < 1) {
            error(sender, "Uso: /hist <Nick>");
            return;
        }

        String nick = args[0];
        List<Map<?, ?>> lista = data.getMapList("sanciones");
        List<String> lineas = new ArrayList<>();

        // Las 10 últimas sanciones de ese jugador, la más reciente primero
        for (int i = lista.size() - 1; i >= 0 && lineas.size() < 10; i--) {
            Map<?, ?> m = lista.get(i);
            if (!String.valueOf(m.get("nick")).equalsIgnoreCase(nick)) continue;
            lineas.add(m.get("fecha") + " | " + m.get("tipo") + " | " + m.get("motivo")
                    + " | " + m.get("tiempo") + " | por " + m.get("staff"));
        }

        sender.sendMessage(Component.text("Historial de " + nick + ":", NamedTextColor.GOLD));
        if (lineas.isEmpty()) {
            sender.sendMessage(Component.text("Sin sanciones registradas.", NamedTextColor.GREEN));
            return;
        }
        for (String linea : lineas) {
            sender.sendMessage(Component.text(linea, NamedTextColor.GRAY));
        }
    }

    private void error(CommandSender sender, String msg) {
        sender.sendMessage(Component.text(msg, NamedTextColor.RED));
    }

    private void registrar(String staff, String nick, String tipo, String motivo, String tiempo) {
        List<Map<?, ?>> lista = new ArrayList<>(data.getMapList("sanciones"));

        Map<String, Object> m = new
