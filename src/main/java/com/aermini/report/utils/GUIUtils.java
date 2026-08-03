package com.aermini.report.utils;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import java.util.List;

public class GUIUtils {
    public static ItemStack createItem(String materialName, int amount, int data, String name, List<String> lore) {
        if (materialName == null) return null;
        Material material = parseMaterial(materialName);
        ItemStack item;
        if (material == null) material=Material.STONE;
        item = (data > 0 && material.getMaxDurability() == 0)? new ItemStack(material, amount, (short) data): new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (name != null) meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        if (lore != null && !lore.isEmpty()) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createPlayerHead(String playerName, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.SKULL_ITEM, 1, (short) SkullType.PLAYER.ordinal());
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwner(playerName);
        if (name != null) meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        if (lore != null && !lore.isEmpty()) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static Material parseMaterial(String name) {
        if (name == null || name.isEmpty()) return Material.STONE;
        if (name.equalsIgnoreCase("_PLAYERHEAD")) return Material.SKULL_ITEM;
        
        // 数字id
        try {
            int id = Integer.parseInt(name);
            for (Material mat : Material.values()) {
                if (mat.getId() == id) return mat;
            }
        } catch (NumberFormatException e) {}
        
        // 名称匹配
        Material material = Material.matchMaterial(name);
        if (material != null) return material;

        // 枚举id
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.STONE;
        }
    }

    public static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static List<String> colorize(List<String> texts) {
        for (int i = 0; i < texts.size(); i++) {
            texts.set(i, colorize(texts.get(i)));
        }
        return texts;
    }

    public static String replacePlaceholders(String text, String... replacements) {
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                text = text.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
        }
        return text;
    }
}
