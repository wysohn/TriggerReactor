/*******************************************************************************
 *     Copyright (C) 2017 wysohn
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package io.github.wysohn.triggerreactor.main;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javax.script.ScriptException;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.conversations.Conversable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import io.github.wysohn.triggerreactor.core.lexer.LexerException;
import io.github.wysohn.triggerreactor.core.parser.ParserException;
import io.github.wysohn.triggerreactor.manager.AreaSelectionManager;
import io.github.wysohn.triggerreactor.manager.ExecutorManager;
import io.github.wysohn.triggerreactor.manager.Manager;
import io.github.wysohn.triggerreactor.manager.PermissionManager;
import io.github.wysohn.triggerreactor.manager.PlayerLocationManager;
import io.github.wysohn.triggerreactor.manager.ScriptEditManager;
import io.github.wysohn.triggerreactor.manager.TriggerConditionManager;
import io.github.wysohn.triggerreactor.manager.TriggerManager.Trigger;
import io.github.wysohn.triggerreactor.manager.VariableManager;
import io.github.wysohn.triggerreactor.manager.location.SimpleLocation;
import io.github.wysohn.triggerreactor.manager.trigger.AreaTriggerManager;
import io.github.wysohn.triggerreactor.manager.trigger.AreaTriggerManager.AreaTrigger;
import io.github.wysohn.triggerreactor.manager.trigger.ClickTriggerManager;
import io.github.wysohn.triggerreactor.manager.trigger.CommandTriggerManager;
import io.github.wysohn.triggerreactor.manager.trigger.CustomTriggerManager;
import io.github.wysohn.triggerreactor.manager.trigger.CustomTriggerManager.CustomTrigger;
import io.github.wysohn.triggerreactor.manager.trigger.InventoryTriggerManager;
import io.github.wysohn.triggerreactor.manager.trigger.InventoryTriggerManager.InventoryTrigger;
import io.github.wysohn.triggerreactor.manager.trigger.NamedTriggerManager;
import io.github.wysohn.triggerreactor.manager.trigger.RepeatingTriggerManager;
import io.github.wysohn.triggerreactor.manager.trigger.RepeatingTriggerManager.RepeatingTrigger;
import io.github.wysohn.triggerreactor.manager.trigger.WalkTriggerManager;
import io.github.wysohn.triggerreactor.tools.FileUtil;
import io.github.wysohn.triggerreactor.tools.ScriptEditor.SaveHandler;
import io.github.wysohn.triggerreactor.tools.TimeUtil;

public class TriggerReactor extends JavaPlugin {
    private static TriggerReactor instance;
    public static TriggerReactor getInstance() {
        return instance;
    }

    public static final ExecutorService cachedThreadPool = Executors.newCachedThreadPool(new ThreadFactory(){
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(){{this.setPriority(MIN_PRIORITY);}};
        }
    });

    private BungeeCordHelper bungeeHelper;

    private ExecutorManager executorManager;
    private VariableManager variableManager;
    private ScriptEditManager scriptEditManager;
    private TriggerConditionManager conditionManager;
    private PlayerLocationManager locationManager;
    private PermissionManager permissionManager;
    private AreaSelectionManager selectionManager;

    private ClickTriggerManager clickManager;
    private WalkTriggerManager walkManager;
    private CommandTriggerManager cmdManager;
    private InventoryTriggerManager invManager;
    private AreaTriggerManager areaManager;
    private CustomTriggerManager customManager;
    private RepeatingTriggerManager repeatManager;

    private NamedTriggerManager namedTriggerManager;

    private Thread bungeeConnectionThread;
    @Override
    public void onEnable() {
        super.onEnable();
        instance = this;
        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

        File file = new File(getDataFolder(), "config.yml");
        if(!file.exists()){
            try{
                String configStr = FileUtil.readFromStream(getResource("config.yml"));
                FileUtil.writeToFile(file, configStr);
            }catch(IOException e){
                e.printStackTrace();
                this.setEnabled(false);
            }
        }

        bungeeHelper = new BungeeCordHelper();
        bungeeConnectionThread = new Thread(bungeeHelper);
        bungeeConnectionThread.setPriority(Thread.MIN_PRIORITY);
        bungeeConnectionThread.start();

        try {
            executorManager = new ExecutorManager(this);
        } catch (ScriptException | IOException e) {
            initFailed(e);
            return;
        }

        try {
            variableManager = new VariableManager(this);
        } catch (IOException | InvalidConfigurationException e) {
            initFailed(e);
            return;
        }

        scriptEditManager = new ScriptEditManager(this);
        conditionManager = new TriggerConditionManager(this);
        locationManager = new PlayerLocationManager(this);
        permissionManager = new PermissionManager(this);
        selectionManager = new AreaSelectionManager(this);

        clickManager = new ClickTriggerManager(this);
        walkManager = new WalkTriggerManager(this);
        cmdManager = new CommandTriggerManager(this);
        invManager = new InventoryTriggerManager(this);
        areaManager = new AreaTriggerManager(this);
        customManager = new CustomTriggerManager(this);
        repeatManager = new RepeatingTriggerManager(this);

        namedTriggerManager = new NamedTriggerManager(this);
    }

    private void initFailed(Exception e) {
        e.printStackTrace();
        getLogger().severe("Initialization failed!");
        getLogger().severe(e.getMessage());
        this.setEnabled(false);
    }

    public BungeeCordHelper getBungeeHelper() {
        return bungeeHelper;
    }

    public ExecutorManager getExecutorManager() {
        return executorManager;
    }

    public VariableManager getVariableManager() {
        return variableManager;
    }

    public ScriptEditManager getScriptEditManager() {
        return scriptEditManager;
    }

    public TriggerConditionManager getConditionManager() {
        return conditionManager;
    }

    public ClickTriggerManager getClickManager() {
        return clickManager;
    }

    public WalkTriggerManager getWalkManager() {
        return walkManager;
    }

    public InventoryTriggerManager getInvManager() {
        return invManager;
    }

    public AreaTriggerManager getAreaManager() {
        return areaManager;
    }

    public NamedTriggerManager getNamedTriggerManager() {
        return namedTriggerManager;
    }

    @Override
    public void onDisable() {
        super.onDisable();

        getLogger().info("Finalizing the scheduled script executions...");
        cachedThreadPool.shutdown();
        bungeeConnectionThread.interrupt();
        getLogger().info("Shut down complete!");
    }

    private static final String INTEGER_REGEX = "^[0-9]+$";
    private static final String DOUBLE_REGEX = "^[0-9]+.[0-9]{0,}$";

    private boolean debugging = false;
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(command.getName().equalsIgnoreCase("triggerreactor")){
            if(!sender.hasPermission("triggerreactor.admin"))
                return true;

            if(args.length > 0){
                if(args[0].equalsIgnoreCase("debug")){
                    debugging = !debugging;

                    getLogger().info("Debugging is set to "+debugging);
                    return true;
                }else if(args[0].equalsIgnoreCase("click") || args[0].equalsIgnoreCase("c")){
                    if(args.length == 1){
                        scriptEditManager.startEdit((Player) sender, "Click Trigger", "", new SaveHandler(){
                            @Override
                            public void onSave(String script) {
                                if(clickManager.startLocationSet((Player) sender, script)){
                                    sender.sendMessage(ChatColor.GRAY+"Now click the block to set click trigger.");
                                }else{
                                    sender.sendMessage(ChatColor.GRAY+"Already on progress.");
                                }
                            }
                        });
                    }else{
                        StringBuilder builder = new StringBuilder();
                        for(int i = 1; i < args.length; i++)
                            builder.append(args[i] + " ");
                        if(clickManager.startLocationSet((Player) sender, builder.toString())){
                            sender.sendMessage(ChatColor.GRAY+"Now click the block to set click trigger.");
                        }else{
                            sender.sendMessage(ChatColor.GRAY+"Already on progress.");
                        }
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("walk") || args[0].equalsIgnoreCase("w")) {
                    if(args.length == 1){
                        scriptEditManager.startEdit((Player) sender, "Walk Trigger", "", new SaveHandler(){
                            @Override
                            public void onSave(String script) {
                                if (walkManager.startLocationSet((Player) sender, script)) {
                                    sender.sendMessage(ChatColor.GRAY + "Now click the block to set walk trigger.");
                                } else {
                                    sender.sendMessage(ChatColor.GRAY + "Already on progress.");
                                }
                            }
                        });
                    }else{
                        StringBuilder builder = new StringBuilder();
                        for (int i = 1; i < args.length; i++)
                            builder.append(args[i] + " ");
                        if (walkManager.startLocationSet((Player) sender, builder.toString())) {
                            sender.sendMessage(ChatColor.GRAY + "Now click the block to set walk trigger.");
                        } else {
                            sender.sendMessage(ChatColor.GRAY + "Already on progress.");
                        }
                    }
                    return true;
                } else if(args.length > 1 && (args[0].equalsIgnoreCase("command") || args[0].equalsIgnoreCase("cmd"))){
                    if(cmdManager.hasCommandTrigger(args[1])){
                        sender.sendMessage(ChatColor.GRAY + "This command is already binded!");
                    }else{
                        if(args.length == 2){
                            scriptEditManager.startEdit((Player) sender, "Command Trigger", "", new SaveHandler(){
                                @Override
                                public void onSave(String script) {
                                    cmdManager.addCommandTrigger(sender, args[1], script);

                                    sender.sendMessage(ChatColor.GREEN+"Command trigger is binded!");

                                    saveAsynchronously(cmdManager);
                                }
                            });
                        }else{
                            StringBuilder builder = new StringBuilder();
                            for (int i = 2; i < args.length; i++)
                                builder.append(args[i] + " ");

                            cmdManager.addCommandTrigger(sender, args[1], builder.toString());

                            sender.sendMessage(ChatColor.GREEN+"Command trigger is binded!");

                            saveAsynchronously(cmdManager);
                        }
                    }
                    return true;
                } else if ((args[0].equalsIgnoreCase("variables") || args[0].equalsIgnoreCase("vars"))) {
                    if(args.length == 3){
                        if(args[1].equalsIgnoreCase("Item")){
                            String name = args[2];
                            if(!VariableManager.isValidName(name)){
                                sender.sendMessage(ChatColor.RED+name+" is not a valid key!");
                                return true;
                            }

                            ItemStack IS = ((Player) sender).getInventory().getItemInMainHand();
                            if(IS == null || IS.getType() == Material.AIR){
                                sender.sendMessage(ChatColor.RED+"You are holding nothing on your main hand!");
                                return true;
                            }

                            variableManager.put(name, IS);

                            sender.sendMessage(ChatColor.GREEN+"Item saved!");
                        }else if(args[1].equalsIgnoreCase("Location")){
                            String name = args[2];
                            if(!VariableManager.isValidName(name)){
                                sender.sendMessage(ChatColor.RED+name+" is not a valid key!");
                                return true;
                            }

                            Location loc = ((Player) sender).getLocation();
                            variableManager.put(name, loc);

                            sender.sendMessage(ChatColor.GREEN+"Location saved!");
                        }else{
                            String name = args[1];
                            String value = args[2];

                            if(!VariableManager.isValidName(name)){
                                sender.sendMessage(ChatColor.RED+name+" is not a valid key!");
                                return true;
                            }

                            if(value.matches(INTEGER_REGEX)){
                                variableManager.put(name, Integer.parseInt(value));
                            }else if(value.matches(DOUBLE_REGEX)){
                                variableManager.put(name, Double.parseDouble(value));
                            }else if(value.equals("true") || value.equals("false")){
                                variableManager.put(name, Boolean.parseBoolean(value));
                            }else{
                                variableManager.put(name, value);
                            }

                            sender.sendMessage(ChatColor.GREEN+"Variable saved!");
                        }
                        return true;
                    }else if(args.length == 2){
                        String name = args[1];
                        sender.sendMessage(ChatColor.GRAY+"Value of "+name+": "+variableManager.get(name));

                        return true;
                    }
                } else if(args[0].equalsIgnoreCase("inventory") || args[0].equalsIgnoreCase("i")){
                    if(args.length > 3 && args[2].equalsIgnoreCase("create")){
                        String name = args[1];
                        int size = -1;
                        try{
                            size = Integer.parseInt(args[3]);
                        }catch(NumberFormatException e){
                            sender.sendMessage(ChatColor.RED+""+size+" is not a valid number");
                            return true;
                        }

                        if(args.length == 4){
                            final int sizeCopy = size;
                            scriptEditManager.startEdit((Player) sender, "Inventory Trigger", "", new SaveHandler() {
                                @Override
                                public void onSave(String script) {
                                    try {
                                        if(invManager.createTrigger(sizeCopy, name, script)){
                                            sender.sendMessage(ChatColor.GREEN+"Inventory Trigger created!");

                                            saveAsynchronously(invManager);
                                        }else{
                                            sender.sendMessage(ChatColor.GRAY+"Another Inventory Trigger with that name already exists");
                                        }
                                    } catch (IOException | LexerException | ParserException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        }else{
                            String script = mergeArguments(args, 4, args.length - 1);

                            try {
                                if(invManager.createTrigger(size, name, script)){
                                    sender.sendMessage(ChatColor.GREEN+"Inventory Trigger created!");

                                    saveAsynchronously(invManager);
                                }else{
                                    sender.sendMessage(ChatColor.GRAY+"Another Inventory Trigger with that name already exists");
                                }
                            } catch (IOException | LexerException | ParserException e) {
                                e.printStackTrace();
                            }
                        }
                    } else if(args.length == 3 && args[2].equalsIgnoreCase("delete")){
                        String name = args[1];

                        if(invManager.deleteTrigger(name)){
                            sender.sendMessage(ChatColor.GREEN+"Deleted!");

                            saveAsynchronously(invManager);
                        }else{
                            sender.sendMessage(ChatColor.GRAY+"No such inventory trigger found.");
                        }
                    } else if(args.length == 4 && args[2].equals("item")){
                        ItemStack IS = ((Player) sender).getInventory().getItemInMainHand();
                        IS = IS == null ? null : IS.clone();

                        String name = args[1];

                        int index = -1;
                        try{
                            index = Integer.parseInt(args[3]);
                        }catch(NumberFormatException e){
                            sender.sendMessage(ChatColor.RED+""+index+" is not a valid number.");
                            return true;
                        }

                        InventoryTrigger trigger = invManager.getTriggerForName(name);
                        if(trigger == null){
                            sender.sendMessage(ChatColor.GRAY+"No such Inventory Trigger named "+name);
                            return true;
                        }

                        if(index > trigger.getItems().length - 1){
                            sender.sendMessage(ChatColor.RED+""+index+" is out of bound. (Size: "+trigger.getItems().length+")");
                            return true;
                        }

                        trigger.getItems()[index] = IS;

                        saveAsynchronously(invManager);
                    } else if(args.length > 2 && args[2].equalsIgnoreCase("open")){
                        String name = args[1];
                        Player forWhom;
                        if(args.length == 3){
                            forWhom = (Player) sender;
                        }else{
                            forWhom = Bukkit.getPlayer(args[3]);
                        }

                        if(forWhom == null){
                            sender.sendMessage(ChatColor.GRAY+"Can't find that player.");
                            return true;
                        }

                        Inventory opened = invManager.openGUI(forWhom, name);
                        if(opened == null){
                            sender.sendMessage(ChatColor.GRAY+"No such Inventory Trigger named "+name);
                            return true;
                        }
                    } /*else if(args.length == 3 && args[2].equalsIgnoreCase("sync")){
                        String name = args[1];

                        InventoryTrigger trigger = invManager.getTriggerForName(name);
                        if(trigger == null){
                            sender.sendMessage(ChatColor.GRAY+"No such Inventory Trigger named "+name);
                            return true;
                        }

                        trigger.setSync(!trigger.isSync());

                        invManager.saveAll();

                        sender.sendMessage(ChatColor.GRAY+"Sync mode: "+(trigger.isSync() ? ChatColor.GREEN : ChatColor.RED)+trigger.isSync());
                    } */else {
                        sendCommandDesc(sender, "/triggerreactor[trg] inventory[i] <inventory name> create <size> [...]", "create a new inventory. <size> must be multiple of 9."
                                + " The <size> cannot be larger than 54");
                        sendDetails(sender, "/trg i MyInventory create 54");
                        sendCommandDesc(sender, "/triggerreactor[trg] inventory[i] <inventory name> delete", "delete this inventory");
                        sendDetails(sender, "/trg i MyInventory delete");
                        sendCommandDesc(sender, "/triggerreactor[trg] inventory[i] <inventory name> item <index>", "set item of inventory to the holding item. "
                                + "Clears the slot if you are holding nothing.");
                        sendDetails(sender, "/trg i MyInventory item 0");
                        sendCommandDesc(sender, "/triggerreactor[trg] inventory[i] <inventory name> open", "Simply open GUI");
                        sendCommandDesc(sender, "/triggerreactor[trg] inventory[i] <inventory name> open <player name>", "Simply open GUI for <player name>");
                        //sendCommandDesc(sender, "/triggerreactor[trg] inventory[i] <inventory name> sync", "Toggle sync/async mode.");
                    }
                    return true;
                } else if(args[0].equalsIgnoreCase("misc")){
                    if(args.length > 2 && args[1].equalsIgnoreCase("title")){
                        ItemStack IS = ((Player) sender).getInventory().getItemInMainHand();
                        if(IS == null || IS.getType() == Material.AIR){
                            sender.sendMessage(ChatColor.RED+"You are holding nothing.");
                            return true;
                        }

                        String title = mergeArguments(args, 2, args.length - 1);
                        ItemMeta IM = IS.getItemMeta();
                        IM.setDisplayName(title);
                        IS.setItemMeta(IM);

                        ((Player) sender).getInventory().setItemInMainHand(IS);
                        return true;
                    }else if(args.length > 3 && args[1].equalsIgnoreCase("lore") && args[2].equalsIgnoreCase("add")){
                        ItemStack IS = ((Player) sender).getInventory().getItemInMainHand();
                        if(IS == null || IS.getType() == Material.AIR){
                            sender.sendMessage(ChatColor.RED+"You are holding nothing.");
                            return true;
                        }

                        String lore = mergeArguments(args, 3, args.length - 1);
                        ItemMeta IM = IS.getItemMeta();
                        List<String> lores = IM.hasLore() ? IM.getLore() : new ArrayList<>();
                        lores.add(lore);
                        IM.setLore(lores);
                        IS.setItemMeta(IM);

                        ((Player) sender).getInventory().setItemInMainHand(IS);
                        return true;
                    }else if(args.length > 4 && args[1].equalsIgnoreCase("lore") && args[2].equalsIgnoreCase("set")){
                        ItemStack IS = ((Player) sender).getInventory().getItemInMainHand();
                        if(IS == null || IS.getType() == Material.AIR){
                            sender.sendMessage(ChatColor.RED+"You are holding nothing.");
                            return true;
                        }

                        int index = -1;
                        try{
                            index = Integer.parseInt(args[3]);
                        }catch(NumberFormatException e){
                            sender.sendMessage(ChatColor.RED+""+index+" is not a valid number");
                            return true;
                        }

                        String lore = mergeArguments(args, 4, args.length - 1);
                        ItemMeta IM = IS.getItemMeta();
                        List<String> lores = IM.hasLore() ? IM.getLore() : new ArrayList<>();
                        if(index > lores.size() - 1){
                            sender.sendMessage(ChatColor.RED+""+index+" is out of bound. (Lore size: "+lores.size()+")");
                            return true;
                        }

                        lores.set(index, lore);
                        IM.setLore(lores);
                        IS.setItemMeta(IM);

                        ((Player) sender).getInventory().setItemInMainHand(IS);
                        return true;
                    } else if (args.length == 4 && args[1].equalsIgnoreCase("lore") && args[2].equalsIgnoreCase("remove")){
                        ItemStack IS = ((Player) sender).getInventory().getItemInMainHand();
                        if(IS == null || IS.getType() == Material.AIR){
                            sender.sendMessage(ChatColor.RED+"You are holding nothing.");
                            return true;
                        }

                        int index = -1;
                        try{
                            index = Integer.parseInt(args[3]);
                        }catch(NumberFormatException e){
                            sender.sendMessage(ChatColor.RED+""+index+" is not a valid number");
                            return true;
                        }

                        ItemMeta IM = IS.getItemMeta();
                        List<String> lores = IM.getLore();
                        if(lores == null || index > lores.size() - 1 || index < 0){
                            sender.sendMessage(ChatColor.GRAY+"No lore at index "+index);
                            return true;
                        }

                        lores.remove(index);
                        IM.setLore(lores);
                        IS.setItemMeta(IM);

                        ((Player) sender).getInventory().setItemInMainHand(IS);
                        return true;
                    } else{
                        sendCommandDesc(sender, "/triggerreactor[trg] misc title <item title>", "Change the title of holding item");
                        sendCommandDesc(sender, "/triggerreactor[trg] misc lore add <string>", "Append lore to the holding item");
                        sendCommandDesc(sender, "/triggerreactor[trg] misc lore set <index> <string>", "Replace lore at the specified index."
                                + "(Index start from 0)");
                        sendCommandDesc(sender, "/triggerreactor[trg] misc lore remove <index>", "Append lore to the holding item");
                    }

                    return true;
                } else if(args.length > 0 && (args[0].equalsIgnoreCase("area") || args[0].equalsIgnoreCase("a"))){
                    if(args.length == 2 && args[1].equalsIgnoreCase("toggle")){
                        boolean result = selectionManager.toggleSelection((Player) sender);

                        sender.sendMessage(ChatColor.GRAY+"Area selection mode enabled: "+ChatColor.GOLD+result);
                    } else if (args.length == 3 && args[2].equals("create")){
                        String name = args[1];

                        AreaTrigger trigger = areaManager.getArea(name);
                        if(trigger != null){
                            sender.sendMessage(ChatColor.RED+"Area Trigger "+name+" is already exists!");
                            return true;
                        }

                        AreaTriggerManager.Area selected = selectionManager.getSelection((Player) sender);
                        if(selected == null){
                            sender.sendMessage(ChatColor.GRAY+"Invalid or incomplete area selection.");
                            return true;
                        }

                        Set<AreaTriggerManager.Area> conflicts = areaManager.getConflictingAreas(selected);
                        if(!conflicts.isEmpty()){
                            sender.sendMessage(ChatColor.GRAY+"Found ["+conflicts.size()+"] conflicting areas:");
                            for(AreaTriggerManager.Area conflict : conflicts){
                                sender.sendMessage(ChatColor.LIGHT_PURPLE+"  "+conflict);
                            }
                            return true;
                        }

                        if(areaManager.createArea(name, selected.getSmallest(), selected.getLargest())){
                            sender.sendMessage(ChatColor.GREEN+"Area Trigger has created!");

                            saveAsynchronously(areaManager);

                            selectionManager.resetSelections((Player) sender);
                        }else{
                            sender.sendMessage(ChatColor.GRAY+"Area Trigger "+name+" already exists.");
                        }
                    } else if (args.length == 3 && args[2].equals("delete")){
                        String name = args[1];

                        if(areaManager.deleteArea(name)){
                            sender.sendMessage(ChatColor.GREEN+"Area Trigger deleted");

                            saveAsynchronously(areaManager);

                            selectionManager.resetSelections((Player) sender);
                        }else{
                            sender.sendMessage(ChatColor.GRAY+"Area Trigger "+name+" does not exists.");
                        }
                    }else if (args.length > 2 && args[2].equals("enter")){
                        String name = args[1];

                        AreaTrigger trigger = areaManager.getArea(name);
                        if(trigger == null){
                            sender.sendMessage(ChatColor.GRAY+"No Area Trigger found with that name.");
                            return true;
                        }

                        if(args.length == 3){
                            scriptEditManager.startEdit((Player) sender, "Area Trigger [Enter]", "", new SaveHandler(){
                                @Override
                                public void onSave(String script) {
                                    try {
                                        trigger.setEnterTrigger(script);

                                        saveAsynchronously(areaManager);
                                    } catch (IOException | LexerException | ParserException e) {
                                        e.printStackTrace();
                                        sender.sendMessage(ChatColor.RED+"Could not save!");
                                        sender.sendMessage(e.getMessage());
                                        sender.sendMessage(ChatColor.RED+"See console for more information.");
                                    }
                                }
                            });
                        }else{
                            try {
                                trigger.setEnterTrigger(mergeArguments(args, 3, args.length - 1));

                                saveAsynchronously(areaManager);
                            } catch (IOException | LexerException | ParserException e) {
                                e.printStackTrace();
                                sender.sendMessage(ChatColor.RED+"Could not save!");
                                sender.sendMessage(e.getMessage());
                                sender.sendMessage(ChatColor.RED+"See console for more information.");
                            }
                        }
                    } else if (args.length > 2 && args[2].equals("exit")){
                        String name = args[1];

                        AreaTrigger trigger = areaManager.getArea(name);
                        if(trigger == null){
                            sender.sendMessage(ChatColor.GRAY+"No Area Trigger found with that name.");
                            return true;
                        }

                        if(args.length == 3){
                            scriptEditManager.startEdit((Player) sender, "Area Trigger [Exit]", "", new SaveHandler(){
                                @Override
                                public void onSave(String script) {
                                    try {
                                        trigger.setExitTrigger(script);

                                        saveAsynchronously(areaManager);
                                    } catch (IOException | LexerException | ParserException e) {
                                        e.printStackTrace();
                                        sender.sendMessage(ChatColor.RED+"Could not save!");
                                        sender.sendMessage(e.getMessage());
                                        sender.sendMessage(ChatColor.RED+"See console for more information.");
                                    }
                                }
                            });
                        }else{
                            try {
                                trigger.setExitTrigger(mergeArguments(args, 3, args.length - 1));

                                saveAsynchronously(areaManager);
                            } catch (IOException | LexerException | ParserException e) {
                                e.printStackTrace();
                                sender.sendMessage(ChatColor.RED+"Could not save!");
                                sender.sendMessage(e.getMessage());
                                sender.sendMessage(ChatColor.RED+"See console for more information.");
                            }
                        }
                    } else if (args.length == 3 && args[2].equals("sync")){
                        String name = args[1];

                        AreaTrigger trigger = areaManager.getArea(name);
                        if(trigger == null){
                            sender.sendMessage(ChatColor.GRAY+"No Area Trigger found with that name.");
                            return true;
                        }

                        trigger.setSync(!trigger.isSync());

                        saveAsynchronously(areaManager);

                        sender.sendMessage(ChatColor.GRAY+"Sync mode: "+(trigger.isSync() ? ChatColor.GREEN : ChatColor.RED)+trigger.isSync());
                    } else {
                        sendCommandDesc(sender, "/triggerreactor[trg] area[a] toggle", "Enable/Disable area selection mode.");
                        sendCommandDesc(sender, "/triggerreactor[trg] area[a] <name> create", "Create area trigger out of selected region.");
                        sendCommandDesc(sender, "/triggerreactor[trg] area[a] <name> delete", "Delete area trigger. BE CAREFUL!");
                        sendCommandDesc(sender, "/triggerreactor[trg] area[a] <name> enter [...]", "Enable/Disable area selection mode.");
                        sendDetails(sender, "/trg a TestingArea enter #MESSAGE \"Welcome\"");
                        sendCommandDesc(sender, "/triggerreactor[trg] area[a] <name> exit [...]", "Enable/Disable area selection mode.");
                        sendDetails(sender, "/trg a TestingArea exit #MESSAGE \"Bye\"");
                        sendCommandDesc(sender, "/triggerreactor[trg] area[a] <name> sync", "Enable/Disable sync mode.");
                        sendDetails(sender, "Setting it to true when you want to cancel event (with #CANCELEVENT)."
                                + " However, setting sync mode will make the trigger run on server thread; keep in mind that"
                                + " it can lag the server if you have too much things going on within the code."
                                + " Set it to false always if you are not sure.");
                    }
                    return true;
                } else if (args.length > 2 && args[0].equalsIgnoreCase("custom")) {
                    String eventName = args[1];
                    String name = args[2];

                    if(customManager.getTriggerForName(name) != null){
                        sender.sendMessage(ChatColor.GRAY+"No Area Trigger found with that name.");
                        return true;
                    }

                    if(args.length == 3){
                        scriptEditManager.startEdit((Player) sender,
                                "Custom Trigger[" + eventName.substring(Math.max(0, eventName.length() - 10)) + "]", "",
                                new SaveHandler() {
                                    @Override
                                    public void onSave(String script) {
                                        try {
                                            customManager.createCustomTrigger(eventName, name, script);

                                            saveAsynchronously(customManager);

                                            sender.sendMessage(ChatColor.GREEN+"Custom Trigger created!");
                                        } catch (ClassNotFoundException | IOException | LexerException
                                                | ParserException e) {
                                            e.printStackTrace();
                                            sender.sendMessage(ChatColor.RED+"Could not save! "+e.getMessage());
                                            sender.sendMessage(ChatColor.RED+"See console for detailed messages.");
                                        }
                                    }
                                });
                    }else{
                        String script = mergeArguments(args, 3, args.length - 1);

                        try {
                            customManager.createCustomTrigger(eventName, name, script);

                            saveAsynchronously(customManager);

                            sender.sendMessage(ChatColor.GREEN+"Custom Trigger created!");
                        } catch (IOException | LexerException | ParserException e) {
                            e.printStackTrace();
                            sender.sendMessage(ChatColor.RED+"Could not save! "+e.getMessage());
                            sender.sendMessage(ChatColor.RED+"See console for detailed messages.");
                        } catch(ClassNotFoundException e2){
                            sender.sendMessage(ChatColor.RED+"Could not save! "+e2.getMessage());
                            sender.sendMessage(ChatColor.RED+"Provided event name is not valid.");
                        }
                    }
                    return true;
                }  else if(args.length > 0 && (args[0].equalsIgnoreCase("repeat") || args[0].equalsIgnoreCase("r"))){
                    if(args.length == 2){
                        String name = args[1];

                        if(repeatManager.getTrigger(name) != null){
                            sender.sendMessage(ChatColor.GRAY+"This named is already in use.");
                            return true;
                        }

                        this.scriptEditManager.startEdit((Conversable) sender, "Repeating Trigger", "", new SaveHandler(){
                            @Override
                            public void onSave(String script) {
                                try {
                                    repeatManager.createTrigger(name, script);
                                } catch (IOException | LexerException | ParserException e) {
                                    e.printStackTrace();
                                    sender.sendMessage(ChatColor.RED+"Could not save!");
                                    sender.sendMessage(e.getMessage());
                                    sender.sendMessage(ChatColor.RED+"See console for more information.");
                                }

                                saveAsynchronously(repeatManager);
                            }
                        });
                    } else if (args.length == 4 && args[2].equalsIgnoreCase("interval")) {
                        String name = args[1];

                        RepeatingTrigger trigger = repeatManager.getTrigger(name);

                        if(trigger == null){
                            sender.sendMessage(ChatColor.GRAY+"No Repeating Trigger with name "+name);
                            return true;
                        }

                        String intervalValue = args[3];
                        long interval = TimeUtil.parseTime(intervalValue);

                        trigger.setInterval(interval);

                        saveAsynchronously(repeatManager);

                        sender.sendMessage(ChatColor.GREEN+"Now "+
                                ChatColor.GOLD+"["+name+"]"+
                                ChatColor.GREEN+" will run every "+
                                ChatColor.GOLD+"["+TimeUtil.milliSecondsToString(interval)+"]");
                    } else if (args.length == 3 && args[2].equalsIgnoreCase("autostart")) {
                        String name = args[1];

                        RepeatingTrigger trigger = repeatManager.getTrigger(name);

                        if(trigger == null){
                            sender.sendMessage(ChatColor.GRAY+"No Repeating Trigger with name "+name);
                            return true;
                        }

                        trigger.setAutoStart(!trigger.isAutoStart());

                        saveAsynchronously(repeatManager);

                        sender.sendMessage("Auto start: "+(trigger.isAutoStart() ? ChatColor.GREEN : ChatColor.RED)+trigger.isAutoStart());
                    } else if (args.length == 3 && args[2].equalsIgnoreCase("toggle")) {
                        String name = args[1];

                        RepeatingTrigger trigger = repeatManager.getTrigger(name);

                        if(trigger == null){
                            sender.sendMessage(ChatColor.GRAY+"No Repeating Trigger with name "+name);
                            return true;
                        }

                        if(repeatManager.isRunning(name)){
                            repeatManager.stopTrigger(name);
                            sender.sendMessage(ChatColor.GREEN+"Scheduled stop. It may take some time depends on CPU usage.");
                        } else {
                            repeatManager.startTrigger(name);
                            sender.sendMessage(ChatColor.GREEN+"Scheduled start up. It may take some time depends on CPU usage.");
                        }
                    } else if (args.length == 3 && args[2].equalsIgnoreCase("pause")) {
                        String name = args[1];

                        RepeatingTrigger trigger = repeatManager.getTrigger(name);

                        if(trigger == null){
                            sender.sendMessage(ChatColor.GRAY+"No Repeating Trigger with name "+name);
                            return true;
                        }

                        trigger.setPaused(!trigger.isPaused());

                        sender.sendMessage("Paused: "+(trigger.isPaused() ? ChatColor.GREEN : ChatColor.RED)+trigger.isPaused());
                    } else if (args.length == 3 && args[2].equalsIgnoreCase("status")) {
                        String name = args[1];

                        RepeatingTrigger trigger = repeatManager.getTrigger(name);

                        if(trigger == null){
                            sender.sendMessage(ChatColor.GRAY+"No Repeating Trigger with name "+name);
                            return true;
                        }

                        repeatManager.showTriggerInfo(sender, trigger);
                    } else if (args.length == 3 && args[2].equalsIgnoreCase("delete")) {
                        String name = args[1];

                        RepeatingTrigger trigger = repeatManager.getTrigger(name);

                        if(trigger == null){
                            sender.sendMessage(ChatColor.GRAY+"No Repeating Trigger with name "+name);
                            return true;
                        }

                        repeatManager.deleteTrigger(name);
                    } else {
                        sendCommandDesc(sender, "/triggerreactor[trg] repeat[r] <name>", "Create Repeating Trigger.");
                        sendDetails(sender, ChatColor.DARK_RED+"Quick create is not supported.");
                        sendDetails(sender, "This creates a Repeating Trigger with default settings. You probably will want to change default values"
                                + " using other commands below. Also, creating Repeating Trigger doesn't start it automatically.");
                        sendCommandDesc(sender, "/triggerreactor[trg] repeat[r] <name> interval <time format>", "Change the interval of this trigger.");
                        sendDetails(sender, "Notice the <time format> is not just a number but has specific format for it. For example, you first"
                                + " type what number you want to set and also define the unit of it. If you want it to repeat it every 1 hour, 20 minutes,"
                                + " and 50seconds, then it will be "+ChatColor.GOLD+"/trg r BlahBlah interval 1h20m50s."+ChatColor.GRAY+" Currently only h, m,"
                                + " and s are supported for this format. Also notice that if you have two numbers with same format, they will add up as well. For example,"
                                + ChatColor.GOLD+" /trg r BlahBlah interval 30s40s"+ChatColor.GRAY+" will be added up to 70seconds total. All units other than"
                                + " h, m, or s will be ignored.");
                        sendCommandDesc(sender, "/triggerreactor[trg] repeat[r] <name> autostart", "Enable/Disable automatic start for this trigger.");
                        sendDetails(sender, "By setting this to "+ChatColor.GREEN+"true"+ChatColor.GRAY+", this trigger will start on plugin enables itself. "
                                + "Otherwise, you have to start it yourself every time.");
                        sendCommandDesc(sender, "/triggerreactor[trg] repeat[r] <name> toggle", "Start or stop the Repeating Trigger.");
                        sendCommandDesc(sender, "/triggerreactor[trg] repeat[r] <name> pause", "Pause or unpause the Repeating Trigger.");
                        sendCommandDesc(sender, "/triggerreactor[trg] repeat[r] <name> status", "See brief information about this trigger.");
                        sendCommandDesc(sender, "/triggerreactor[trg] repeat[r] <name> delete", "Delete repeating trigger.");
                    }

                    return true;
                }  else if (args.length == 2 && (args[0].equalsIgnoreCase("synccustom") || args[0].equalsIgnoreCase("sync"))) {
                    String name = args[1];

                    CustomTrigger trigger = customManager.getTriggerForName(name);
                    if(trigger == null){
                        sender.sendMessage(ChatColor.GRAY+"No Custom Trigger found with that name.");
                        return true;
                    }

                    trigger.setSync(!trigger.isSync());

                    saveAsynchronously(customManager);

                    sender.sendMessage(ChatColor.GRAY+"Sync mode: "+(trigger.isSync() ? ChatColor.GREEN : ChatColor.RED)+trigger.isSync());
                    return true;
                } else if (args.length == 3 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("del"))) {
                    String key = args[2];
                    switch (args[1]) {
                    case "vars":
                    case "variables":
                        variableManager.remove(key);
                        sender.sendMessage(ChatColor.GREEN+"Removed the variable "+ChatColor.GOLD+key);
                        break;
                    case "cmd":
                    case "command":
                        if(cmdManager.removeCommandTrigger(key)){
                            sender.sendMessage(ChatColor.GREEN+"Removed the command trigger "+ChatColor.GOLD+key);

                            saveAsynchronously(cmdManager);
                        }else{
                            sender.sendMessage(ChatColor.GRAY+"Command trigger "+ChatColor.GOLD+key+ChatColor.GRAY+" does not exist");
                        }
                        break;
                    case "custom":
                        if(customManager.removeTriggerForName(key)){
                            sender.sendMessage(ChatColor.GREEN+"Removed the custom trigger "+ChatColor.GOLD+key);

                            saveAsynchronously(customManager);
                        }else{
                            sender.sendMessage(ChatColor.GRAY+"Custom Trigger "+ChatColor.GOLD+key+ChatColor.GRAY+" does not exist");
                        }
                        break;
                    default:
                        sender.sendMessage("Ex) /trg del vars player.count");
                        sender.sendMessage("List: variables[vars], command[cmd], custom");
                        break;
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("search")) {
                    Chunk chunk = ((Player) sender).getLocation().getChunk();
                    showGlowStones(sender, clickManager.getTriggersInChunk(chunk));
                    showGlowStones(sender, walkManager.getTriggersInChunk(chunk));
                    sender.sendMessage(ChatColor.GRAY+"Now trigger blocks will be shown as "+ChatColor.GOLD+"glowstone");
                    return true;
                } else if(args[0].equalsIgnoreCase("saveall")){
                    for(Manager manager : Manager.getManagers())
                        manager.saveAll();
                    sender.sendMessage("Save complete!");
                    return true;
                } else if (args[0].equalsIgnoreCase("reload")) {
                    for(Manager manager : Manager.getManagers())
                        manager.reload();

                    executorManager.reload();

                    sender.sendMessage("Reload Complete!");
                    return true;
                }
            }

            showHelp(sender);
        }

        return true;
    }

    /**
     *
     * @param args
     * @param indexFrom inclusive
     * @param indexTo inclusive
     * @return
     */
    private String mergeArguments(String[] args, int indexFrom, int indexTo) {
        StringBuilder builder = new StringBuilder(args[indexFrom]);
        for(int i = indexFrom + 1; i <= indexTo; i++){
            builder.append(" "+args[i]);
        }
        return builder.toString();
    }

    public File getJarFile(){
        return super.getFile();
    }

    private final Set<Class<? extends Manager>> savings = new HashSet<>();

    public boolean saveAsynchronously(final Manager manager){
        if(savings.contains(manager))
            return false;

        new Thread(new Runnable(){
            @Override
            public void run() {
                try{
                    synchronized(savings){
                        savings.add(manager.getClass());
                    }

                    getLogger().info("Saving "+manager.getClass().getSimpleName());
                    manager.saveAll();
                    getLogger().info("Saving Done!");
                }catch(Exception e){
                    e.printStackTrace();
                    getLogger().warning("Failed to save "+manager.getClass().getSimpleName());
                }finally{
                    synchronized(savings){
                        savings.remove(manager.getClass());
                    }
                }
            }
        }){{this.setPriority(MIN_PRIORITY);}}.start();
        return true;
    }

    public boolean isDebugging() {
        return debugging;
    }


    @SuppressWarnings("deprecation")
    private void showGlowStones(CommandSender sender, Set<Entry<SimpleLocation, Trigger>> set) {
        for (Entry<SimpleLocation, Trigger> entry : set) {
            SimpleLocation sloc = entry.getKey();
            ((Player) sender).sendBlockChange(
                    new Location(Bukkit.getWorld(sloc.getWorld()), sloc.getX(), sloc.getY(), sloc.getZ()),
                    Material.GLOWSTONE, (byte) 0);
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GRAY+"-----     "+ChatColor.GOLD+this.getDescription().getFullName()+ChatColor.GRAY+"    ----");

        sendCommandDesc(sender, "/triggerreactor[trg] walk[w] [...]", "create a walk trigger.");
        sendDetails(sender, "/trg w #MESSAGE \"HEY YOU WALKED!\"");
        sendDetails(sender, "To create lines of script, simply type &b/trg w &7without extra parameters.");

        sendCommandDesc(sender, "/triggerreactor[trg] click[c] [...]", "create a click trigger.");
        sendDetails(sender, "/trg c #MESSAGE \"HEY YOU CLICKED!\"");
        sendDetails(sender, "To create lines of script, simply type &b/trg c &7without extra parameters.");

        sendCommandDesc(sender, "/triggerreactor[trg] command[cmd] <command name> [...]", "create a command trigger.");
        sendDetails(sender, "/trg cmd test #MESSAGE \"I'M test COMMAND!\"");
        sendDetails(sender, "To create lines of script, simply type &b/trg cmd <command name> &7without extra parameters.");

        sendCommandDesc(sender, "/triggerreactor[trg] inventory[i] <inventory name>", "Create an inventory trigger named <inventory name>");
        sendDetails(sender, "/trg i to see more commands...");

        sendCommandDesc(sender, "/triggerreactor[trg] area[a]", "Create an area trigger.");
        sendDetails(sender, "/trg a to see more commands...");

        sendCommandDesc(sender, "/triggerreactor[trg] repeat[r]", "Create an repeating trigger.");
        sendDetails(sender, "/trg r to see more commands...");

        sendCommandDesc(sender, "/triggerreactor[trg] custom <event> <name> [...]", "Create an area trigger.");
        sendDetails(sender, "/trg custom onJoin Greet #BROADCAST \"&aPlease welcome &6\"+player.getName()+\"&a!\"");
        sendCommandDesc(sender, "/triggerreactor[trg] synccustom[sync] <name>", "Toggle Sync/Async mode of custom trigger <name>");
        sendDetails(sender, "/trg synccustom Greet");

        sendCommandDesc(sender, "/triggerreactor[trg] misc", "Miscellaneous. Type it to see the list.");

        sendCommandDesc(sender, "/triggerreactor[trg] variables[vars] [...]", "set global variables.");
        sendDetails(sender, "&cWarning - This command will delete the previous data associated with the key if exists.");
        sendDetails(sender, "/trg vars Location test &8- &7save current location into global variable 'test'");
        sendDetails(sender, "/trg vars Item gifts.item1 &8- &7save hand held item into global variable 'test'");
        sendDetails(sender, "/trg vars test 13.5 &8- &7save 13.5 into global variable 'test'");

        sendCommandDesc(sender, "/triggerreactor[trg] variables[vars] <variable name>", "get the value saved in <variable name>. null if nothing.");

        sendCommandDesc(sender, "/triggerreactor[trg] delete[del] <type> <name>", "Delete specific trigger/variable/etc.");
        sendDetails(sender, "/trg del vars test &8- &7delete the variable saved in 'test'");
        sendDetails(sender, "/trg del cmd test &8- &7delete the command trigger 'test'");
        sendDetails(sender, "/trg del custom Greet &8- &7delete the command trigger 'test'");

        sendCommandDesc(sender, "/triggerreactor[trg] search", "Show all trigger blocks in this chunk as glowing stones.");

        sendCommandDesc(sender, "/triggerreactor[trg] saveall", "Save all scripts, variables, and settings.");

        sendCommandDesc(sender, "/triggerreactor[trg] reload", "Reload all scripts, variables, and settings.");
    }

    private void sendCommandDesc(CommandSender sender, String command, String desc){
        sender.sendMessage(ChatColor.AQUA+command+" "+ChatColor.DARK_GRAY+"- "+ChatColor.GRAY+desc);
    }

    private void sendDetails(CommandSender sender, String detail){
        detail = ChatColor.translateAlternateColorCodes('&', detail);
        sender.sendMessage("  "+ChatColor.GRAY+detail);
    }

    public class BungeeCordHelper implements PluginMessageListener, Runnable{
        private final String CHANNEL = "BungeeCord";

        private final String SUB_SERVERLIST = "ServerList";
        private final String SUB_USERCOUNT = "UserCount";

        private final Map<String, Integer> playerCounts = new ConcurrentHashMap<>();

        /**
         * constructor should only be called from onEnable()
         */
        private BungeeCordHelper() {
            getServer().getMessenger().registerOutgoingPluginChannel(TriggerReactor.this, CHANNEL);
            getServer().getMessenger().registerIncomingPluginChannel(TriggerReactor.this, CHANNEL, this);
        }

        @Override
        public void onPluginMessageReceived(String channel, Player player, byte[] message) {
            if (!channel.equals(CHANNEL)) {
                return;
            }

            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String subchannel = in.readUTF();
            if (subchannel.equals(SUB_SERVERLIST)) {
                String[] serverList = in.readUTF().split(", ");
                Set<String> serverListSet = Sets.newHashSet(serverList);

                for(String server : serverListSet){
                    if(!playerCounts.containsKey(server))
                        playerCounts.put(server, -1);
                }

                Set<String> deleteServer = new HashSet<>();
                for(Entry<String, Integer> entry : playerCounts.entrySet()){
                    if(!serverListSet.contains(entry.getKey()))
                        deleteServer.add(entry.getKey());
                }

                for(String delete : deleteServer){
                    playerCounts.remove(delete);
                }
            } else if(subchannel.equals(SUB_USERCOUNT)){
                String server = in.readUTF(); // Name of server, as given in the arguments
                int playercount = in.readInt();

                playerCounts.put(server, playercount);
            }
        }

        public void sendToServer(Player player, String serverName){
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(serverName);

            player.sendPluginMessage(TriggerReactor.this, CHANNEL, out.toByteArray());
        }

        public String[] getServerNames(){
            String[] servers = playerCounts.keySet().toArray(new String[playerCounts.size()]);
            return servers;
        }

        public int getPlayerCount(String serverName){
            return playerCounts.getOrDefault(serverName, -1);
        }

        @Override
        public void run(){
            while(!Thread.interrupted()){
                Player player = Iterables.getFirst(Bukkit.getOnlinePlayers(), null);
                if(player == null)
                    return;

                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF(SUB_SERVERLIST);
                out.writeUTF("GetServers");
                player.sendPluginMessage(instance, SUB_SERVERLIST, out.toByteArray());

                if(!playerCounts.isEmpty()){
                    for(Entry<String, Integer> entry : playerCounts.entrySet()){
                        ByteArrayDataOutput out2 = ByteStreams.newDataOutput();
                        out2.writeUTF(SUB_USERCOUNT);
                        out2.writeUTF("PlayerCount");
                        out2.writeUTF(entry.getKey());
                        player.sendPluginMessage(instance, SUB_USERCOUNT, out2.toByteArray());
                    }
                }

                try {
                    Thread.sleep(5 * 1000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
