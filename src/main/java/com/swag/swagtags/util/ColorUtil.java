package com.swag.swagtags.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles all Minecraft color/format code formats:
 *   Legacy          : &c &a &l &r ...
 *   Bukkit verbose  : &x&R&R&G&G&B&B  (handled by translateAlternateColorCodes)
 *   Hex (3 styles)  : &#RRGGBB  <#RRGGBB>  {#RRGGBB}
 *   MiniMessage     : <red> <bold> <reset> (+ common aliases)
 *   Gradient (N stops): <gradient:#FF0000:#FFFF00:#0000FF>text</gradient>
 *   Rainbow         : <rainbow>text</rainbow>
 */
public class ColorUtil {

    // ── Hex patterns ──────────────────────────────────────────────────────────
    private static final Pattern HEX_AMPERSAND = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern HEX_ANGLE     = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final Pattern HEX_BRACE     = Pattern.compile("\\{#([A-Fa-f0-9]{6})\\}");

    // ── Gradient / rainbow patterns ───────────────────────────────────────────
    // Group 1: all colon-separated color stops (e.g. "#FF0000:#FFFF00:#0000FF")
    // Group 2: text content
    private static final Pattern GRADIENT = Pattern.compile(
            "<gradient:([^>]+)>(.*?)</gradient>", Pattern.DOTALL);
    private static final Pattern RAINBOW = Pattern.compile(
            "<rainbow>(.*?)</rainbow>", Pattern.DOTALL);

    // ── Validation-only patterns (detect malformed tags) ─────────────────────
    private static final Pattern GRADIENT_OPEN  = Pattern.compile("<gradient:([^>]+)>");
    private static final Pattern RAINBOW_OPEN   = Pattern.compile("<rainbow>");
    private static final Pattern RAINBOW_CLOSE  = Pattern.compile("</rainbow>");
    private static final Pattern GRADIENT_CLOSE = Pattern.compile("</gradient>");

    private static final boolean SUPPORTS_HEX = checkHexSupport();

    // ── Named color → java.awt.Color ─────────────────────────────────────────
    private static final Map<String, Color> NAMED_COLORS = new HashMap<>();
    static {
        // Standard Minecraft palette
        NAMED_COLORS.put("black",        new Color(0x000000));
        NAMED_COLORS.put("dark_blue",    new Color(0x0000AA));
        NAMED_COLORS.put("dark_green",   new Color(0x00AA00));
        NAMED_COLORS.put("dark_aqua",    new Color(0x00AAAA));
        NAMED_COLORS.put("dark_red",     new Color(0xAA0000));
        NAMED_COLORS.put("dark_purple",  new Color(0xAA00AA));
        NAMED_COLORS.put("gold",         new Color(0xFFAA00));
        NAMED_COLORS.put("gray",         new Color(0xAAAAAA));
        NAMED_COLORS.put("dark_gray",    new Color(0x555555));
        NAMED_COLORS.put("blue",         new Color(0x5555FF));
        NAMED_COLORS.put("green",        new Color(0x55FF55));
        NAMED_COLORS.put("aqua",         new Color(0x55FFFF));
        NAMED_COLORS.put("red",          new Color(0xFF5555));
        NAMED_COLORS.put("light_purple", new Color(0xFF55FF));
        NAMED_COLORS.put("yellow",       new Color(0xFFFF55));
        NAMED_COLORS.put("white",        new Color(0xFFFFFF));
        // Common aliases
        NAMED_COLORS.put("grey",         new Color(0xAAAAAA));
        NAMED_COLORS.put("dark_grey",    new Color(0x555555));
        NAMED_COLORS.put("purple",       new Color(0xAA00AA));
        NAMED_COLORS.put("pink",         new Color(0xFF55FF));
        NAMED_COLORS.put("orange",       new Color(0xFFAA00));
        NAMED_COLORS.put("maroon",       new Color(0xAA0000));
        NAMED_COLORS.put("navy",         new Color(0x0000AA));
        NAMED_COLORS.put("teal",         new Color(0x00AAAA));
        NAMED_COLORS.put("lime",         new Color(0x55FF55));
        NAMED_COLORS.put("cyan",         new Color(0x55FFFF));
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Process every color format and return a fully translated string ready for
     * display in chat, item names, or lore.
     */
    public static String processColors(String text) {
        if (text == null || text.isEmpty()) return text;

        // 1. Gradient & rainbow expand to raw hex — do these first
        if (SUPPORTS_HEX) {
            text = processGradients(text);
            text = processRainbow(text);
        }

        // 2. MiniMessage named colors / formats → § codes
        text = translateMiniMessage(text);

        // 3. All hex formats → §x§R§G§B§G§B
        if (SUPPORTS_HEX) {
            text = translateHexPattern(text, HEX_ANGLE);
            text = translateHexPattern(text, HEX_BRACE);
            text = translateHexPattern(text, HEX_AMPERSAND);
        }

        // 4. Legacy & codes
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Validate color formatting in a tag suffix.
     * Returns null if everything looks fine, or a human-readable error message.
     */
    public static String validateColors(String text) {
        if (text == null || text.isEmpty()) return null;

        // Check gradient tags
        Matcher gOpen = GRADIENT_OPEN.matcher(text);
        int gradientOpens = 0;
        while (gOpen.find()) {
            gradientOpens++;
            String[] stops = gOpen.group(1).split(":");
            if (stops.length < 2) {
                return "&cGradient tag needs at least two colors: &f<gradient:color1:color2>";
            }
            for (String stop : stops) {
                String s = stop.trim();
                if (s.isEmpty()) {
                    return "&cGradient tag has an empty color stop. Use hex codes or color names.";
                }
                if (parseColor(s) == null) {
                    return "&cUnknown gradient color '&f" + s + "&c'. Use a hex code or color name.";
                }
            }
        }
        int gradientCloses = countPattern(text, GRADIENT_CLOSE);
        if (gradientOpens != gradientCloses) {
            return "&cGradient tag is missing its closing &f</gradient>&c tag.";
        }

        // Check rainbow tags
        int rainbowOpens  = countPattern(text, RAINBOW_OPEN);
        int rainbowCloses = countPattern(text, RAINBOW_CLOSE);
        if (rainbowOpens != rainbowCloses) {
            return "&cRainbow tag is missing its closing &f</rainbow>&c tag.";
        }

        return null;
    }

    /**
     * Strip ALL color codes and return plain visible text (used for min-length validation).
     */
    public static String stripAllColors(String text) {
        if (text == null || text.isEmpty()) return text;

        // Strip gradient/rainbow tags but keep their text content
        text = GRADIENT.matcher(text).replaceAll("$2");
        text = RAINBOW.matcher(text).replaceAll("$1");

        // Strip remaining angle-bracket tags (<#RRGGBB>, <red>, </red>, etc.)
        text = text.replaceAll("<[^>]+>", "");

        // Strip {#RRGGBB}
        text = text.replaceAll("\\{#[A-Fa-f0-9]{6}\\}", "");

        // Strip &#RRGGBB
        text = text.replaceAll("&#[A-Fa-f0-9]{6}", "");

        // Strip legacy &X codes and native § codes
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text));
    }

    public static boolean supportsHex() {
        return SUPPORTS_HEX;
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Gradient / Rainbow
    // ──────────────────────────────────────────────────────────────────────────

    private static String processGradients(String text) {
        Matcher m = GRADIENT.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String[] stopArgs = m.group(1).split(":");
            String content = m.group(2);

            List<Color> stops = new ArrayList<>();
            boolean valid = true;
            for (String arg : stopArgs) {
                Color c = parseColor(arg.trim());
                if (c == null) { valid = false; break; }
                stops.add(c);
            }

            if (!valid || stops.size() < 2) {
                // Leave as-is; validateColors will have caught this
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(applyGradient(content, stops)));
        }
        return m.appendTail(sb).toString();
    }

    private static String processRainbow(String text) {
        Matcher m = RAINBOW.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(applyRainbow(m.group(1))));
        }
        return m.appendTail(sb).toString();
    }

    /** Apply a multi-stop gradient to every character in the string. */
    private static String applyGradient(String text, List<Color> stops) {
        if (text.isEmpty()) return text;
        int len = text.length();
        int segments = stops.size() - 1;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < len; i++) {
            double t = len == 1 ? 0.0 : (double) i / (len - 1);
            // Map t into the segment index and local ratio within that segment
            double scaled = t * segments;
            int seg = (int) Math.min(scaled, segments - 1);
            double ratio = scaled - seg;
            Color start = stops.get(seg);
            Color end   = stops.get(seg + 1);
            int r = (int) (start.getRed()   + ratio * (end.getRed()   - start.getRed()));
            int g = (int) (start.getGreen() + ratio * (end.getGreen() - start.getGreen()));
            int b = (int) (start.getBlue()  + ratio * (end.getBlue()  - start.getBlue()));
            result.append(hexToMinecraft(String.format("%02X%02X%02X", r, g, b)));
            result.append(text.charAt(i));
        }
        return result.toString();
    }

    /** Cycle through the full hue wheel, one color per character. */
    private static String applyRainbow(String text) {
        if (text.isEmpty()) return text;
        int len = text.length();
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < len; i++) {
            float hue = (float) i / len;
            Color c = Color.getHSBColor(hue, 1.0f, 1.0f);
            result.append(hexToMinecraft(String.format("%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue())));
            result.append(text.charAt(i));
        }
        return result.toString();
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Hex helpers
    // ──────────────────────────────────────────────────────────────────────────

    private static String translateHexPattern(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(hexToMinecraft(m.group(1).toUpperCase())));
        }
        return m.appendTail(sb).toString();
    }

    /** Convert a 6-char hex string to Bukkit's §x§R§G§B§G§B format. */
    private static String hexToMinecraft(String hex) {
        return String.valueOf(ChatColor.COLOR_CHAR) + "x"
                + ChatColor.COLOR_CHAR + hex.charAt(0)
                + ChatColor.COLOR_CHAR + hex.charAt(1)
                + ChatColor.COLOR_CHAR + hex.charAt(2)
                + ChatColor.COLOR_CHAR + hex.charAt(3)
                + ChatColor.COLOR_CHAR + hex.charAt(4)
                + ChatColor.COLOR_CHAR + hex.charAt(5);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Color name → Color
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Parse a color argument: #RRGGBB, RRGGBB, or a named color.
     * Returns null if unrecognised.
     */
    private static Color parseColor(String s) {
        if (s == null || s.isEmpty()) return null;
        if (s.startsWith("#")) return parseHex(s.substring(1));
        Color named = NAMED_COLORS.get(s.toLowerCase());
        if (named != null) return named;
        return parseHex(s); // try bare hex
    }

    private static Color parseHex(String hex) {
        if (hex.length() != 6) return null;
        try {
            return new Color(
                    Integer.parseInt(hex.substring(0, 2), 16),
                    Integer.parseInt(hex.substring(2, 4), 16),
                    Integer.parseInt(hex.substring(4, 6), 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  MiniMessage → § codes
    // ──────────────────────────────────────────────────────────────────────────

    private static String translateMiniMessage(String text) {
        // ── Named colors ──
        text = replaceTag(text, "black",        "§0");
        text = replaceTag(text, "dark_blue",    "§1");
        text = replaceTag(text, "navy",         "§1");
        text = replaceTag(text, "dark_green",   "§2");
        text = replaceTag(text, "dark_aqua",    "§3");
        text = replaceTag(text, "teal",         "§3");
        text = replaceTag(text, "dark_red",     "§4");
        text = replaceTag(text, "maroon",       "§4");
        text = replaceTag(text, "dark_purple",  "§5");
        text = replaceTag(text, "purple",       "§5");
        text = replaceTag(text, "gold",         "§6");
        text = replaceTag(text, "orange",       "§6");
        text = replaceTag(text, "gray",         "§7");
        text = replaceTag(text, "grey",         "§7");
        text = replaceTag(text, "dark_gray",    "§8");
        text = replaceTag(text, "dark_grey",    "§8");
        text = replaceTag(text, "blue",         "§9");
        text = replaceTag(text, "green",        "§a");
        text = replaceTag(text, "lime",         "§a");
        text = replaceTag(text, "aqua",         "§b");
        text = replaceTag(text, "cyan",         "§b");
        text = replaceTag(text, "red",          "§c");
        text = replaceTag(text, "light_purple", "§d");
        text = replaceTag(text, "pink",         "§d");
        text = replaceTag(text, "yellow",       "§e");
        text = replaceTag(text, "white",        "§f");

        // ── Format codes (with short aliases) ──
        text = replaceTag(text, "bold",          "§l");
        text = replaceTag(text, "b",             "§l");
        text = replaceTag(text, "italic",        "§o");
        text = replaceTag(text, "i",             "§o");
        text = replaceTag(text, "em",            "§o");
        text = replaceTag(text, "underline",     "§n");
        text = replaceTag(text, "underlined",    "§n");
        text = replaceTag(text, "u",             "§n");
        text = replaceTag(text, "strikethrough", "§m");
        text = replaceTag(text, "st",            "§m");
        text = replaceTag(text, "s",             "§m");
        text = replaceTag(text, "obfuscated",    "§k");
        text = replaceTag(text, "obf",           "§k");
        text = replaceTag(text, "k",             "§k");
        text = replaceTag(text, "magic",         "§k");

        // ── Reset ──
        text = text.replace("<reset>",  "§r");
        text = text.replace("</reset>", "§r");
        text = text.replace("<r>",      "§r");
        text = text.replace("</r>",     "§r");

        return text;
    }

    /**
     * Replace <tag> with code and </tag> with §r.
     */
    private static String replaceTag(String text, String tag, String code) {
        text = text.replace("<" + tag + ">",  code);
        text = text.replace("</" + tag + ">", "§r");
        return text;
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  Utilities
    // ──────────────────────────────────────────────────────────────────────────

    private static int countPattern(String text, Pattern pattern) {
        int count = 0;
        Matcher m = pattern.matcher(text);
        while (m.find()) count++;
        return count;
    }

    private static boolean checkHexSupport() {
        String version = Bukkit.getVersion();
        return version.contains("1.16") || version.contains("1.17") ||
                version.contains("1.18") || version.contains("1.19") ||
                version.contains("1.20") || version.contains("1.21");
    }
}
