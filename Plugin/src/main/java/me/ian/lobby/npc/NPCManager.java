package me.ian.lobby.npc;

import me.ian.PVPHelper;
import me.ian.utils.ItemUtils;
import me.ian.utils.NBTUtils;
import me.ian.utils.Utils;
import net.minecraft.server.v1_12_R1.EntityPlayer;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.NBTTagCompound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @author SevJ6
 */
public class NPCManager implements Listener {

    private final List<NPC> npcs = new ArrayList<>();
    private final File npcDataFolder;

    public NPCManager() {
        Bukkit.getServer().getPluginManager().registerEvents(this, PVPHelper.INSTANCE);

        npcDataFolder = new File(PVPHelper.INSTANCE.getDataFolder(), "npcs");
        try {
            if (!npcDataFolder.exists()) npcDataFolder.mkdirs();
        } catch (Throwable t) {
            t.printStackTrace();
        }

        // Load NPCs and spawn them for all online players
        int npcCount = (int) Arrays.stream(Objects.requireNonNull(npcDataFolder.listFiles()))
                .filter(file -> !file.isDirectory() && file.getName().endsWith(".nbt"))
                .map(file -> {
                    try {
                        // Parse common properties
                        String name = file.getName().replace(".nbt", "");
                        NBTTagCompound compound = NBTUtils.readTagFromFile(file);
                        NPC.SkinTexture texture = new NPC.SkinTexture(
                                compound.getCompound("Skin").getString("texture"),
                                compound.getCompound("Skin").getString("signature")
                        );
                        Location location = NBTUtils.readLocationFromTag(compound);
                        boolean shouldFacePlayers = compound.getBoolean("FacePlayers");
                        InteractionBehavior behavior = InteractionBehavior.valueOf(compound.getString("Behavior"));

                        // Create and add NPC to the list
                        NPC npc = new NPC(location, name, texture, shouldFacePlayers, behavior);

                        // Add the arena name to the new NPC data if the behavior is SEND_TO_ARENA
                        if (behavior.equals(InteractionBehavior.SEND_TO_ARENA)) {
                            NBTTagCompound dataCompound = npc.getData();
                            dataCompound.setString("arena_endpoint", compound.getString("arena_endpoint"));
                            npc.setData(dataCompound);
                        }

                        this.npcs.add(npc);
                        return npc;
                    } catch (Exception e) {
                        PVPHelper.INSTANCE.getLogger().warning("Failed to load NPC from file: " + file.getName());
                        e.printStackTrace();
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .count();

        this.npcs.forEach(NPC::spawn);

        // Log how many NPCs were loaded
        PVPHelper.INSTANCE.getLogger().info(String.format("Successfully loaded %d NPCs.", npcCount));
    }

    public void createNPC(NPC npc) {
        // check to make sure no NPCs having duplicate names
        if (npcs.stream().anyMatch(existingNPC -> existingNPC.getName().equals(npc.getName()))) return;
        npcs.add(npc);

        // save to the filesystem
        File file = new File(npcDataFolder, String.format("%s.nbt", npc.getName()));
        try {
            if (!file.exists()) file.createNewFile();
            NBTUtils.writeTagToFile(npc.getData(), file);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("Failed to save NPC to file: " + file.getName());
            t.printStackTrace();
        }

        npc.spawn();
        Bukkit.getOnlinePlayers().forEach(npc::show);
    }

    public NPC getNPC(String name) {
        return npcs.stream().filter(npc -> npc.getName().equals(name)).findAny().orElse(null);
    }

    public void removeNPC(String name) {
        NPC npc = getNPC(name);
        if (npc == null) return;
        npcs.remove(npc);
        npc.despawn();

        File file = new File(npcDataFolder, String.format("%s.nbt", name));
        if (file.exists()) {
            if (file.delete()) {
                PVPHelper.INSTANCE.getLogger().info("Successfully deleted NPC file: " + file.getName());
            } else {
                PVPHelper.INSTANCE.getLogger().warning("Failed to delete NPC file: " + file.getName());
            }
        } else {
            PVPHelper.INSTANCE.getLogger().warning("NPC file not found for deletion: " + file.getName());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(PVPHelper.INSTANCE, () -> {
            npcs.forEach(npc -> {
                if (npc.getEntityPlayer().getBukkitEntity().getWorld().equals(player.getWorld())) {
                    if (npc.getDistance(player) <= 30) {
                        if (!npc.getPlayersInRange().contains(player)) {
                            npc.getPlayersInRange().add(player);
                            npc.onRangeEnter(player);
                        }
                    }
                }
            });
        }, 20L);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        for (NPC npc : npcs) {
            boolean isWithinRange = npc.getEntityPlayer().getWorld().getWorld() == player.getWorld() && npc.getDistance(player) <= 30;
            boolean isPlayerTracked = npc.getPlayersInRange().contains(player);

            if (isWithinRange) {
                if (!isPlayerTracked) {
                    npc.getPlayersInRange().add(player);
                    npc.onRangeEnter(player);
                } else {
                    npc.lookAtPlayer(player);
                }
            } else if (isPlayerTracked) {
                npc.getPlayersInRange().remove(player);
            }
        }
    }


    /**
     * Handles player interaction with NPCs.
     * <a href="https://bukkit.org/threads/playerinteractevent-triggering-twice.473064/">Event triggers twice per hand</a>
     *
     * @param event PlayerInteractAtEntityEvent triggered when a player interacts with an entity.
     */
    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player) || event.getHand() != EquipmentSlot.HAND) return;

        Player clickedPlayer = (Player) event.getRightClicked();
        Player player = event.getPlayer();
        EntityPlayer entityPlayer = Utils.getHandle(clickedPlayer);

        npcs.stream()
                .filter(npc -> npc.getEntityPlayer().equals(entityPlayer))
                .findFirst()
                .ifPresent(npc -> npc.onInteract(player));
    }

    // Event logic for Item Vendor NPCs
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (player.hasMetadata("vendor_gui")) {
            Inventory inventory = event.getClickedInventory();
            if (inventory != event.getView().getTopInventory()) return;
            if (inventory.getItem(event.getSlot()) == null) return;
            if (inventory.getItem(event.getSlot()).getType() == Material.STONE_BUTTON) {
                ItemStack button = CraftItemStack.asNMSCopy(inventory.getItem(event.getSlot()));
                if (button.hasTag() && button.getTag() != null) {
                    event.setCancelled(true);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 10f, 1f);
                    int index = button.getTag().getInt("next_item_index");

                    org.bukkit.inventory.ItemStack bukkitStack = ItemUtils.ITEM_INDEX.get(index);

                    switch (bukkitStack.getType()) {
                        case TIPPED_ARROW:
                            PotionMeta meta = (PotionMeta) bukkitStack.getItemMeta();
                            meta.setBasePotionData(new PotionData(PotionType.INSTANT_DAMAGE, false, true));
                            bukkitStack.setItemMeta(meta);
                            for (int i = 0; i < 9; i++) {
                                inventory.setItem(i, bukkitStack);
                            }

                            meta.setBasePotionData(new PotionData(PotionType.SPEED, false, true));
                            bukkitStack.setItemMeta(meta);
                            for (int i = 9; i < 18; i++) {
                                inventory.setItem(i, bukkitStack);
                            }

                            meta.setBasePotionData(new PotionData(PotionType.INSTANT_HEAL, false, true));
                            bukkitStack.setItemMeta(meta);
                            for (int i = 18; i < 27; i++) {
                                inventory.setItem(i, bukkitStack);
                            }

                            // normal arrows
                            org.bukkit.inventory.ItemStack arrow = new org.bukkit.inventory.ItemStack(Material.ARROW, 64);
                            for (int i = 27; i < 36; i++) {
                                inventory.setItem(i, arrow);
                            }

                            break;

                        default:
                            for (int i = 0; i < inventory.getSize(); i++) {
                                inventory.setItem(i, bukkitStack);
                            }
                            break;
                    }

                    if (index > 1) inventory.setItem(27, ItemUtils.genButton(index - 1, false));
                    if (index != ItemUtils.ITEM_INDEX.size())
                        inventory.setItem(35, ItemUtils.genButton(index + 1, true));
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        if (event.getReason() == InventoryCloseEvent.Reason.PLUGIN) return;
        if (player.hasMetadata("vendor_gui")) player.removeMetadata("vendor_gui", PVPHelper.INSTANCE);
    }
}
