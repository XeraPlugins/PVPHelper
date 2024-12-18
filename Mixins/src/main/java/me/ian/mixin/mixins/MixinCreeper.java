package me.ian.mixin.mixins;

import me.txmc.rtmixin.CallbackInfo;
import me.txmc.rtmixin.mixin.At;
import me.txmc.rtmixin.mixin.Inject;
import me.txmc.rtmixin.mixin.MethodInfo;
import net.minecraft.server.v1_12_R1.EntityCreeper;
import net.minecraft.server.v1_12_R1.World;

public class MixinCreeper {

    @Inject(info = @MethodInfo(_class = EntityCreeper.class, name = "<init>", sig = World.class, rtype = void.class), at = @At(pos = At.Position.TAIL))
    public static void mixinCreeper(CallbackInfo ci) {
        EntityCreeper creeper = (EntityCreeper) ci.getSelf();
        if (creeper.getWorld().getWorld().getEnvironment() != org.bukkit.World.Environment.NETHER) creeper.die();
        creeper.setNoAI(true);
        creeper.setInvulnerable(true);
    }
}
