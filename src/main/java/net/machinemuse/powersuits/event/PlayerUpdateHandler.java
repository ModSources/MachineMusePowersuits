package net.machinemuse.powersuits.event;

import net.machinemuse.api.ModuleManager;
import net.machinemuse.api.moduletrigger.IPlayerTickModule;
import net.machinemuse.general.sound.SoundDictionary;
import net.machinemuse.numina.common.NuminaConfig;
import net.machinemuse.numina.general.MuseMathUtils;
import net.machinemuse.numina.sound.Musique;
import net.machinemuse.powersuits.common.Config;
import net.machinemuse.utils.MuseHeatUtils;
import net.machinemuse.utils.MuseItemUtils;
import net.machinemuse.utils.MusePlayerUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

/**
 * Created by Claire Semple on 9/8/2014.
 *
 * Ported to Java by lehjr on 10/24/16.
 */
public class PlayerUpdateHandler {
    @SubscribeEvent
    public void onPlayerUpdate(LivingEvent.LivingUpdateEvent e) {
        if (e.getEntity() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) e.getEntity();

            List<ItemStack> modularItemsEquipped = MuseItemUtils.modularItemsEquipped(player);
            double totalWeight = MuseItemUtils.getPlayerWeight(player);
            double weightCapacity = Config.getWeightCapacity();

            for (ItemStack stack : modularItemsEquipped) {
                // Temporary Advanced Rocketry hack Not the best way but meh.
                NBTTagList tagList = stack.getEnchantmentTagList();
                if (tagList != null && !tagList.hasNoTags()) {
                    if (tagList.tagCount() == 1) {
                        if (!(tagList.getCompoundTagAt(0).getShort("id") == 128))
                            stack.getTagCompound().removeTag("ench");
                    } else {
                        NBTTagCompound ar = null;
                        for (int i = 0; i < tagList.tagCount(); i++) {
                            NBTTagCompound nbtTag = tagList.getCompoundTagAt(i);
                            if ((nbtTag.getShort("id") == 128)) {
                                ar = nbtTag;
                            }
                        }
                        stack.getTagCompound().removeTag("ench");
                        if (ar != null) {
                            stack.getTagCompound().setTag("ench", ar);
                        }
                    }
                }
            }

            boolean foundItemWithModule;
            for (IPlayerTickModule module : ModuleManager.getPlayerTickModules()) {
                foundItemWithModule = false;
                for (ItemStack itemStack : modularItemsEquipped) {
                    if (module.isValidForItem(itemStack)) {
                        if (ModuleManager.itemHasActiveModule(itemStack, module.getDataName())) {
                            module.onPlayerTickActive(player, itemStack);
                            foundItemWithModule = true;
                        }
                    }
                }
                if (!foundItemWithModule) {
                    for (ItemStack itemStack : modularItemsEquipped) {
                        module.onPlayerTickInactive(player, itemStack);
                    }
                }
            }

            boolean foundItem = modularItemsEquipped.size() > 0;

            if (foundItem) {
                player.fallDistance = (float) MovementManager.computeFallHeightFromVelocity(MuseMathUtils.clampDouble(player.motionY, -1000.0, 0.0));
                if (totalWeight > weightCapacity) {
                    player.motionX *= weightCapacity / totalWeight;
                    player.motionZ *= weightCapacity / totalWeight;
                }

                // Heat update
                MuseHeatUtils.coolPlayer(player, MusePlayerUtils.getPlayerCoolingBasedOnMaterial(player));
                double maxHeat = MuseHeatUtils.getMaxHeat(player);
                double currHeat = MuseHeatUtils.getPlayerHeat(player);
                if (currHeat > maxHeat) {
                    player.attackEntityFrom(MuseHeatUtils.overheatDamage, (float) (Math.sqrt(currHeat - maxHeat)/* was (int) */ / 4));
                    player.setFire(1);
                } else {
                    player.extinguish();
                }

                // Sound update
                double velsq2 = MuseMathUtils.sumsq(player.motionX, player.motionY, player.motionZ) - 0.5;
                if (player.worldObj.isRemote && NuminaConfig.useSounds()) {
                    if (player.isAirBorne && velsq2 > 0) {
                        Musique.playerSound(player, SoundDictionary.SOUND_EVENT_GLIDER, SoundCategory.PLAYERS, (float) (velsq2 / 3), 1.0f, true);
                    } else {
                        Musique.stopPlayerSound(player, SoundDictionary.SOUND_EVENT_GLIDER);
                    }
                }
            } else if (player.worldObj.isRemote && NuminaConfig.useSounds())
                Musique.stopPlayerSound(player, SoundDictionary.SOUND_EVENT_GLIDER);
        }
    }
}