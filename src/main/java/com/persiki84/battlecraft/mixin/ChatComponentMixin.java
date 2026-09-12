package com.persiki84.battlecraft.mixin;

import com.persiki84.battlecraft.client.hud.ChatSkin;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    private static final int FADE_TICKS = 200;
    private static final int BOTTOM_MARGIN = 40;

    @Unique
    private int battlecraft$newestTime = Integer.MIN_VALUE;

    @Unique
    private int battlecraft$newestLines;

    @Unique
    private boolean battlecraft$clipped;

    @Unique
    private int battlecraft$rowsDrawn;

    @Accessor("trimmedMessages")
    abstract List<GuiMessage.Line> battlecraft$lines();

    @Accessor("chatScrollbarPos")
    abstract int battlecraft$scroll();

    @Invoker("getLineHeight")
    abstract int battlecraft$lineHeight();

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;III)V", at = @At("HEAD"))
    private void battlecraft$slideIn(GuiGraphics graphics, int tickCount, int mouseX, int mouseY,
                                     CallbackInfo callback) {
        List<GuiMessage.Line> lines = battlecraft$lines();
        boolean focused = Minecraft.getInstance().screen instanceof ChatScreen;
        ChatSkin.noteArchive(focused || battlecraft$fresh(lines, tickCount));
        battlecraft$noteArrivals(lines);
        battlecraft$rowsDrawn = 0;

        float drop = ChatSkin.arrivalDrop(graphics);
        graphics.pose().pushPose();
        graphics.pose().translate(focused ? 0.0f : ChatSkin.archiveShift(graphics), drop, 0.0f);

        battlecraft$clipped = drop > 0.0f;
        if (battlecraft$clipped) {
            graphics.enableScissor(0, 0, graphics.guiWidth(), battlecraft$baseline(graphics));
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;III)V", at = @At("RETURN"))
    private void battlecraft$settle(GuiGraphics graphics, int tickCount, int mouseX, int mouseY,
                                    CallbackInfo callback) {
        if (battlecraft$clipped) {
            battlecraft$clipped = false;
            graphics.disableScissor();
        }
        graphics.pose().popPose();
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;III)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V", ordinal = 0))
    private void battlecraft$slideArchived(GuiGraphics graphics, int tickCount, int mouseX, int mouseY,
                                           CallbackInfo callback) {
        int row = battlecraft$rowsDrawn++;
        if (!(Minecraft.getInstance().screen instanceof ChatScreen)) return;

        float shift = ChatSkin.archiveShift(graphics);
        if (shift == 0.0f) return;

        List<GuiMessage.Line> lines = battlecraft$lines();
        int index = row + battlecraft$scroll();
        if (index >= lines.size() || tickCount - lines.get(index).addedTime() < FADE_TICKS) return;

        graphics.pose().translate(shift, 0.0f, 0.0f);
    }

    @Unique
    private boolean battlecraft$fresh(List<GuiMessage.Line> lines, int tickCount) {
        if (lines.isEmpty()) return false;
        return tickCount - lines.get(0).addedTime() < FADE_TICKS;
    }

    @Unique
    private void battlecraft$noteArrivals(List<GuiMessage.Line> lines) {
        if (lines.isEmpty()) {
            battlecraft$newestTime = Integer.MIN_VALUE;
            battlecraft$newestLines = 0;
            return;
        }

        int newestTime = lines.get(0).addedTime();
        int newestLines = battlecraft$linesAt(lines, newestTime);
        int arrived = newestTime == battlecraft$newestTime ? newestLines - battlecraft$newestLines : newestLines;
        battlecraft$newestTime = newestTime;
        battlecraft$newestLines = newestLines;

        if (arrived > 0 && battlecraft$scroll() == 0) ChatSkin.noteLines(arrived, battlecraft$lineHeight());
    }

    @Unique
    private int battlecraft$linesAt(List<GuiMessage.Line> lines, int addedTime) {
        int count = 0;
        while (count < lines.size() && lines.get(count).addedTime() == addedTime) count++;
        return count;
    }

    @Unique
    private int battlecraft$baseline(GuiGraphics graphics) {
        float scale = (float) ((ChatComponent) (Object) this).getScale();
        if (scale <= 0.0f) return graphics.guiHeight();
        return Mth.ceil(Mth.floor((graphics.guiHeight() - BOTTOM_MARGIN) / scale) * scale);
    }
}
