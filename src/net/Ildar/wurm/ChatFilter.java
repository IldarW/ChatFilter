package net.Ildar.wurm;

import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import com.wurmonline.shared.util.MulticolorLineSegment;
import javassist.ClassPool;
import javassist.CtClass;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class ChatFilter implements WurmClientMod, Initable{
    private static Logger logger = Logger.getLogger(ChatFilter.class.getSimpleName());
    private static HeadsUpDisplay hud;
    private static List<BiFunction<String, String, Boolean>> filters = new ArrayList<>();

    @Override
    public void init() {
        try {
            final ClassPool classPool = HookManager.getInstance().getClassPool();
            final CtClass ctWurmConsole = classPool.getCtClass("com.wurmonline.client.console.WurmConsole");
            ctWurmConsole.getMethod("handleDevInput", "(Ljava/lang/String;[Ljava/lang/String;)Z").insertBefore("if (net.Ildar.wurm.ChatFilter.handleInput($1,$2)) return true;");

            HookManager.getInstance().registerHook("com.wurmonline.client.renderer.gui.HeadsUpDisplay", "init", "(II)V", () -> (proxy, method, args) -> {
                method.invoke(proxy, args);
                hud = (HeadsUpDisplay)proxy;
                return null;
            });
            HookManager.getInstance().registerHook("com.wurmonline.client.renderer.gui.ChatPanelComponent",
                    "addText",
                    "(Ljava/lang/String;Ljava/util/List;Z)V",
                    () -> ((proxy, method, args) -> {
                        if (filterMessage((String)args[0], (String)args[1])) {
                            return null;
                        } else
                            return method.invoke(proxy, args);
                    }));
            HookManager.getInstance().registerHook("com.wurmonline.client.renderer.gui.ChatPanelComponent",
                    "addText",
                    "(Ljava/lang/String;Ljava/lang/String;FFFZ)V",
                    () -> ((proxy, method, args) -> {
                        if (filterMessage((String)args[0], (String)args[1])) {
                            return null;
                        } else
                            return method.invoke(proxy, args);
                    }));

        } catch (Exception e) {
            if (ChatFilter.logger != null) {
                ChatFilter.logger.log(Level.SEVERE, "Error loading mod", e);
                ChatFilter.logger.log(Level.SEVERE, e.getMessage());
            }
        }
    }

    private boolean filterMessage(String tab, Object input) {
        String message;
        if (input instanceof List) {
            StringBuilder sb = new StringBuilder();
            for (Iterator<MulticolorLineSegment> iter = ((List)input).iterator(); iter.hasNext(); ) {
                MulticolorLineSegment segment = iter.next();
                //Utils.consolePrint("segment - " + segment.getText() + " with color " + segment.getColor());
                sb.append(segment.getText());
            }
            message = sb.toString();
        } else
            message = (String)input;
        message = message.substring(11).trim();
        for (BiFunction<String, String, Boolean> filter:filters) {
            if (filter.apply(tab, message))
                return true;
        }
        return false;
    }

    public static boolean handleInput(final String cmd, final String[] data) {
        switch (cmd) {
            case "filter":
                String usage = "Usage: filter {add tab key|clear}";
                if (data.length < 2) {
                    hud.consoleOutput(usage);
                    return true;
                }
                if (data[1].equals("add")) {
                    if (data.length < 4) {
                        hud.consoleOutput(usage);
                        return true;
                    }
                    StringBuilder key = new StringBuilder();
                    for(int i = 3; i < data.length; i++)
                        key.append(data[i]);
                    filters.add((tab, message) -> {
                        if(tab.equals(data[2]))
                            return Pattern.matches(".*"+key+".*", message);
                        else
                            return false;
                    });

                    hud.consoleOutput("New filter added");
                } else if (data[1].equals("clear")) {
                    filters.clear();
                    hud.consoleOutput("Filter list cleared");
                }
                return true;
            default:
                return false;
        }
    }
}
