package com.persiki84.itemmodifiers;

import com.persiki84.battlecraft.BattleCraftCommands;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.zones.ZonesMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.persiki84.shared.Names;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModifierCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("ie")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> BattleCraftCommands.openMenu(ctx, ModuleMenuStates.MODIFIERS))

                .then(Commands.literal("toggle")
                        .executes(ctx -> {
                            boolean v = !ModifierConfig.MOD_ENABLED.get();
                            ModifierConfig.MOD_ENABLED.set(v);
                            ModifierConfig.save();
                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.toggle", v).withStyle(ChatFormatting.YELLOW), true);
                            return 1;
                        })
                )

                .then(Commands.literal("info")
                        .executes(ctx -> {
                            Set<String> modifiedItems = new HashSet<>();

                            List<String> potions = ModifierConfig.getPotionEffects();
                            for (String entry : potions) {
                                String[] parts = entry.split("\\|");
                                if (parts.length > 0) modifiedItems.add(parts[0]);
                            }

                            List<String> attrs = ModifierConfig.getAttributes();
                            for (String entry : attrs) {
                                String[] parts = entry.split("\\|");
                                if (parts.length > 0) modifiedItems.add(parts[0]);
                            }

                            if (modifiedItems.isEmpty()) {
                                ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.info.none").withStyle(ChatFormatting.YELLOW), false);
                            } else {
                                ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.info.header").withStyle(ChatFormatting.GOLD), false);
                                for (String item : modifiedItems) {
                                    ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.info.item", Names.item(item)).withStyle(ChatFormatting.AQUA), false);
                                }
                            }
                            return 1;
                        })
                )

                .then(Commands.literal("lore")
                        .then(Commands.literal("add")
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            ItemStack stack = ctx.getSource().getPlayerOrException().getMainHandItem();
                                            if(stack.isEmpty()) return 0;

                                            String rawText = StringArgumentType.getString(ctx, "text");
                                            String formattedText = rawText.replace("&", "\u00a7");

                                            CompoundTag tag = stack.getOrCreateTagElement("display");
                                            ListTag lore = tag.getList("Lore", Tag.TAG_STRING);
                                            lore.add(StringTag.valueOf(loreLine(formattedText)));

                                            tag.put("Lore", lore);
                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.lore.added", formattedText).withStyle(ChatFormatting.GREEN), true);
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("clear")
                                .executes(ctx -> {
                                    ItemStack stack = ctx.getSource().getPlayerOrException().getMainHandItem();
                                    if(stack.isEmpty()) return 0;
                                    clearLore(stack);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.lore.cleared").withStyle(ChatFormatting.YELLOW), true);
                                    return 1;
                                })
                        )
                )

                .then(Commands.literal("clear")
                        .executes(ctx -> {
                            String id = getHeldItemId(ctx);
                            if (id == null) return 0;

                            ModifierConfig.clearAll(id);
                            refresh(ctx);
                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.clear.all", Names.item(id)).withStyle(ChatFormatting.YELLOW), true);
                            return 1;
                        })
                        .then(Commands.literal("potions")
                                .executes(ctx -> {
                                    String id = getHeldItemId(ctx);
                                    if (id == null) return 0;
                                    ModifierConfig.clearPotions(id);
                                    refresh(ctx);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.clear.potions", Names.item(id)).withStyle(ChatFormatting.YELLOW), true);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("attributes")
                                .executes(ctx -> {
                                    String id = getHeldItemId(ctx);
                                    if (id == null) return 0;
                                    ModifierConfig.clearAttributes(id);
                                    refresh(ctx);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.clear.attributes", Names.item(id)).withStyle(ChatFormatting.YELLOW), true);
                                    return 1;
                                })
                        )
                )

                .then(Commands.literal("remove")
                        .then(Commands.literal("potion")
                                .then(Commands.argument("effect", ResourceArgument.resource(buildContext, Registries.MOB_EFFECT))
                                        .executes(ctx -> {
                                            String id = getHeldItemId(ctx);
                                            if (id == null) return 0;
                                            Holder.Reference<MobEffect> ref = ResourceArgument.getResource(ctx, "effect", Registries.MOB_EFFECT);
                                            String effId = ForgeRegistries.MOB_EFFECTS.getKey(ref.value()).toString();
                                            ModifierConfig.removePotion(id, effId);
                                            refresh(ctx);
                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.remove.potion", Names.effect(effId), Names.item(id)).withStyle(ChatFormatting.YELLOW), true);
                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("attribute")
                                .then(Commands.argument("attribute", ResourceArgument.resource(buildContext, Registries.ATTRIBUTE))
                                        .executes(ctx -> {
                                            String id = getHeldItemId(ctx);
                                            if (id == null) return 0;
                                            Holder.Reference<Attribute> ref = ResourceArgument.getResource(ctx, "attribute", Registries.ATTRIBUTE);
                                            String attrId = ForgeRegistries.ATTRIBUTES.getKey(ref.value()).toString();
                                            ModifierConfig.removeAttribute(id, attrId);
                                            refresh(ctx);
                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.remove.attribute", Names.attribute(attrId), Names.item(id)).withStyle(ChatFormatting.YELLOW), true);
                                            return 1;
                                        })
                                )
                        )
                )

                .then(Commands.literal("addpotion")
                        .then(Commands.argument("effect", ResourceArgument.resource(buildContext, Registries.MOB_EFFECT))
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 255))
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests((c, b) -> {
                                                    b.suggest("BUFF"); b.suggest("DEBUFF");
                                                    return b.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    String id = getHeldItemId(ctx);
                                                    if (id == null) return 0;
                                                    Holder.Reference<MobEffect> ref = ResourceArgument.getResource(ctx, "effect", Registries.MOB_EFFECT);
                                                    int lvl = IntegerArgumentType.getInteger(ctx, "level");
                                                    String type = StringArgumentType.getString(ctx, "type");
                                                    ModifierConfig.addPotionEntry(id + "|" + ForgeRegistries.MOB_EFFECTS.getKey(ref.value()) + "|" + lvl + "|" + type);
                                                    refresh(ctx);
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.addpotion", Names.item(id)).withStyle(ChatFormatting.GREEN), true);
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
                .then(Commands.literal("addattribute")
                        .then(Commands.argument("attribute", ResourceArgument.resource(buildContext, Registries.ATTRIBUTE))
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("operation", IntegerArgumentType.integer(0, 2))
                                                .then(Commands.argument("slot", StringArgumentType.word())
                                                        .suggests((c, b) -> {
                                                            b.suggest("mainhand"); b.suggest("offhand"); b.suggest("head");
                                                            b.suggest("chest"); b.suggest("legs"); b.suggest("feet"); b.suggest("any");
                                                            return b.buildFuture();
                                                        })
                                                        .executes(ctx -> {
                                                            String id = getHeldItemId(ctx);
                                                            if (id == null) return 0;
                                                            Holder.Reference<Attribute> ref = ResourceArgument.getResource(ctx, "attribute", Registries.ATTRIBUTE);
                                                            double amt = DoubleArgumentType.getDouble(ctx, "amount");
                                                            int op = IntegerArgumentType.getInteger(ctx, "operation");
                                                            String slot = StringArgumentType.getString(ctx, "slot");
                                                            ModifierConfig.addAttributeEntry(id + "|" + ForgeRegistries.ATTRIBUTES.getKey(ref.value()) + "|" + amt + "|" + op + "|" + slot);
                                                            refresh(ctx);
                                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.addattribute", Names.item(id)).withStyle(ChatFormatting.GREEN), true);
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("item")
                        .then(Commands.argument("item", ResourceArgument.resource(buildContext, Registries.ITEM))
                                .then(Commands.literal("clear")
                                        .executes(ctx -> {
                                            String id = getArgItemId(ctx);
                                            ModifierConfig.clearAll(id);
                                            refresh(ctx);
                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.clear.all", Names.item(id)).withStyle(ChatFormatting.YELLOW), true);
                                            return 1;
                                        })
                                        .then(Commands.literal("potions")
                                                .executes(ctx -> {
                                                    String id = getArgItemId(ctx);
                                                    ModifierConfig.clearPotions(id);
                                                    refresh(ctx);
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.clear.potions", Names.item(id)).withStyle(ChatFormatting.YELLOW), true);
                                                    return 1;
                                                })
                                        )
                                        .then(Commands.literal("attributes")
                                                .executes(ctx -> {
                                                    String id = getArgItemId(ctx);
                                                    ModifierConfig.clearAttributes(id);
                                                    refresh(ctx);
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.clear.attributes", Names.item(id)).withStyle(ChatFormatting.YELLOW), true);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("remove")
                                        .then(Commands.literal("potion")
                                                .then(Commands.argument("effect", ResourceArgument.resource(buildContext, Registries.MOB_EFFECT))
                                                        .executes(ctx -> {
                                                            String id = getArgItemId(ctx);
                                                            Holder.Reference<MobEffect> ref = ResourceArgument.getResource(ctx, "effect", Registries.MOB_EFFECT);
                                                            String effId = ForgeRegistries.MOB_EFFECTS.getKey(ref.value()).toString();
                                                            ModifierConfig.removePotion(id, effId);
                                                            refresh(ctx);
                                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.remove.potion", Names.effect(effId), Names.item(id)).withStyle(ChatFormatting.YELLOW), true);
                                                            return 1;
                                                        })
                                                )
                                        )
                                        .then(Commands.literal("attribute")
                                                .then(Commands.argument("attribute", ResourceArgument.resource(buildContext, Registries.ATTRIBUTE))
                                                        .executes(ctx -> {
                                                            String id = getArgItemId(ctx);
                                                            Holder.Reference<Attribute> ref = ResourceArgument.getResource(ctx, "attribute", Registries.ATTRIBUTE);
                                                            String attrId = ForgeRegistries.ATTRIBUTES.getKey(ref.value()).toString();
                                                            ModifierConfig.removeAttribute(id, attrId);
                                                            refresh(ctx);
                                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.remove.attribute", Names.attribute(attrId), Names.item(id)).withStyle(ChatFormatting.YELLOW), true);
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                                .then(Commands.literal("addpotion")
                                        .then(Commands.argument("effect", ResourceArgument.resource(buildContext, Registries.MOB_EFFECT))
                                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 255))
                                                        .then(Commands.argument("type", StringArgumentType.word())
                                                                .suggests((c, b) -> {
                                                                    b.suggest("BUFF"); b.suggest("DEBUFF");
                                                                    return b.buildFuture();
                                                                })
                                                                .executes(ctx -> {
                                                                    String id = getArgItemId(ctx);
                                                                    Holder.Reference<MobEffect> ref = ResourceArgument.getResource(ctx, "effect", Registries.MOB_EFFECT);
                                                                    int lvl = IntegerArgumentType.getInteger(ctx, "level");
                                                                    String type = StringArgumentType.getString(ctx, "type");
                                                                    ModifierConfig.addPotionEntry(id + "|" + ForgeRegistries.MOB_EFFECTS.getKey(ref.value()) + "|" + lvl + "|" + type);
                                                                    refresh(ctx);
                                                                    ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.addpotion", Names.item(id)).withStyle(ChatFormatting.GREEN), true);
                                                                    return 1;
                                                                })
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("addattribute")
                                        .then(Commands.argument("attribute", ResourceArgument.resource(buildContext, Registries.ATTRIBUTE))
                                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("operation", IntegerArgumentType.integer(0, 2))
                                                                .then(Commands.argument("slot", StringArgumentType.word())
                                                                        .suggests((c, b) -> {
                                                                            b.suggest("mainhand"); b.suggest("offhand"); b.suggest("head");
                                                                            b.suggest("chest"); b.suggest("legs"); b.suggest("feet"); b.suggest("any");
                                                                            return b.buildFuture();
                                                                        })
                                                                        .executes(ctx -> {
                                                                            String id = getArgItemId(ctx);
                                                                            Holder.Reference<Attribute> ref = ResourceArgument.getResource(ctx, "attribute", Registries.ATTRIBUTE);
                                                                            double amt = DoubleArgumentType.getDouble(ctx, "amount");
                                                                            int op = IntegerArgumentType.getInteger(ctx, "operation");
                                                                            String slot = StringArgumentType.getString(ctx, "slot");
                                                                            ModifierConfig.addAttributeEntry(id + "|" + ForgeRegistries.ATTRIBUTES.getKey(ref.value()) + "|" + amt + "|" + op + "|" + slot);
                                                                            refresh(ctx);
                                                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.addattribute", Names.item(id)).withStyle(ChatFormatting.GREEN), true);
                                                                            return 1;
                                                                        })
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("hand")
                        .then(Commands.literal("info")
                                .executes(ctx -> {
                                    ItemStack stack = ctx.getSource().getPlayerOrException().getMainHandItem();
                                    if (stack.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.translatable("itemmodifiers.error.no_held_item"));
                                        return 0;
                                    }
                                    if (!hasModifiers(stack)) {
                                        ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.cmd.hand.none").withStyle(ChatFormatting.YELLOW), false);
                                        return 1;
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.cmd.hand.header").withStyle(ChatFormatting.GOLD), false);
                                    if (stack.hasTag() && stack.getTag().contains("ItemModifiersEffects", 9)) {
                                        net.minecraft.nbt.ListTag list = stack.getTag().getList("ItemModifiersEffects", 10);
                                        for (int i = 0; i < list.size(); i++) {
                                            net.minecraft.nbt.CompoundTag c = list.getCompound(i);
                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.cmd.hand.effect_entry", Names.effect(c.getString("Effect")), c.getInt("Level"), c.getString("Type")).withStyle(ChatFormatting.AQUA), false);
                                        }
                                    }
                                    if (stack.hasTag() && stack.getTag().contains("ItemModifiersAttributes", 9)) {
                                        net.minecraft.nbt.ListTag list = stack.getTag().getList("ItemModifiersAttributes", 10);
                                        for (int i = 0; i < list.size(); i++) {
                                            net.minecraft.nbt.CompoundTag c = list.getCompound(i);
                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.cmd.hand.attribute_entry", Names.attribute(c.getString("Attribute")), c.getDouble("Amount"), c.getInt("Operation"), Names.slot(c.getString("Slot"))).withStyle(ChatFormatting.AQUA), false);
                                        }
                                    }
                                    return 1;
                                })
                        )
                        .then(Commands.literal("clear")
                                .executes(ctx -> {
                                    ItemStack stack = ctx.getSource().getPlayerOrException().getMainHandItem();
                                    if (stack.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.translatable("itemmodifiers.error.no_held_item"));
                                        return 0;
                                    }
                                    if (stack.hasTag()) {
                                        stack.getTag().remove("ItemModifiersEffects");
                                        stack.getTag().remove("ItemModifiersAttributes");
                                        if (stack.getTag().isEmpty()) {
                                            stack.setTag(null);
                                        }
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.cmd.hand.cleared").withStyle(ChatFormatting.YELLOW), true);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("addpotion")
                                .then(Commands.argument("effect", ResourceArgument.resource(buildContext, Registries.MOB_EFFECT))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 255))
                                                .then(Commands.argument("type", StringArgumentType.word())
                                                        .suggests((c, b) -> {
                                                            b.suggest("BUFF"); b.suggest("DEBUFF");
                                                            return b.buildFuture();
                                                        })
                                                        .executes(ctx -> {
                                                            ItemStack stack = ctx.getSource().getPlayerOrException().getMainHandItem();
                                                            if (stack.isEmpty()) {
                                                                ctx.getSource().sendFailure(Component.translatable("itemmodifiers.error.no_held_item"));
                                                                return 0;
                                                            }
                                                            Holder.Reference<MobEffect> ref = ResourceArgument.getResource(ctx, "effect", Registries.MOB_EFFECT);
                                                            String effId = ForgeRegistries.MOB_EFFECTS.getKey(ref.value()).toString();
                                                            int lvl = IntegerArgumentType.getInteger(ctx, "level");
                                                            String type = StringArgumentType.getString(ctx, "type").toUpperCase();
                                                            
                                                            net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
                                                            net.minecraft.nbt.ListTag list = tag.getList("ItemModifiersEffects", 10);
                                                            boolean updated = false;
                                                            for (int i = 0; i < list.size(); i++) {
                                                                net.minecraft.nbt.CompoundTag c = list.getCompound(i);
                                                                if (c.getString("Effect").equals(effId)) {
                                                                    c.putInt("Level", lvl);
                                                                    c.putString("Type", type);
                                                                    updated = true;
                                                                    break;
                                                                }
                                                            }
                                                            if (!updated) {
                                                                net.minecraft.nbt.CompoundTag c = new net.minecraft.nbt.CompoundTag();
                                                                c.putString("Effect", effId);
                                                                c.putInt("Level", lvl);
                                                                c.putString("Type", type);
                                                                list.add(c);
                                                            }
                                                            tag.put("ItemModifiersEffects", list);
                                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.cmd.hand.effect_added", Names.effect(effId), lvl).withStyle(ChatFormatting.GREEN), true);
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("addattribute")
                                .then(Commands.argument("attribute", ResourceArgument.resource(buildContext, Registries.ATTRIBUTE))
                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("operation", IntegerArgumentType.integer(0, 2))
                                                        .then(Commands.argument("slot", StringArgumentType.word())
                                                                .suggests((c, b) -> {
                                                                    b.suggest("mainhand"); b.suggest("offhand"); b.suggest("head");
                                                                    b.suggest("chest"); b.suggest("legs"); b.suggest("feet"); b.suggest("any");
                                                                    return b.buildFuture();
                                                                })
                                                                .executes(ctx -> {
                                                                    ItemStack stack = ctx.getSource().getPlayerOrException().getMainHandItem();
                                                                    if (stack.isEmpty()) {
                                                                        ctx.getSource().sendFailure(Component.translatable("itemmodifiers.error.no_held_item"));
                                                                        return 0;
                                                                    }
                                                                    Holder.Reference<Attribute> ref = ResourceArgument.getResource(ctx, "attribute", Registries.ATTRIBUTE);
                                                                    String attrId = ForgeRegistries.ATTRIBUTES.getKey(ref.value()).toString();
                                                                    double amt = DoubleArgumentType.getDouble(ctx, "amount");
                                                                    int op = IntegerArgumentType.getInteger(ctx, "operation");
                                                                    String slot = StringArgumentType.getString(ctx, "slot").toLowerCase();
                                                                    
                                                                    net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
                                                                    net.minecraft.nbt.ListTag list = tag.getList("ItemModifiersAttributes", 10);
                                                                    boolean updated = false;
                                                                    for (int i = 0; i < list.size(); i++) {
                                                                        net.minecraft.nbt.CompoundTag c = list.getCompound(i);
                                                                        if (c.getString("Attribute").equals(attrId) && c.getString("Slot").equalsIgnoreCase(slot)) {
                                                                            c.putDouble("Amount", amt);
                                                                            c.putInt("Operation", op);
                                                                            updated = true;
                                                                            break;
                                                                        }
                                                                    }
                                                                    if (!updated) {
                                                                        net.minecraft.nbt.CompoundTag c = new net.minecraft.nbt.CompoundTag();
                                                                        c.putString("Attribute", attrId);
                                                                        c.putDouble("Amount", amt);
                                                                        c.putInt("Operation", op);
                                                                        c.putString("Slot", slot);
                                                                        list.add(c);
                                                                    }
                                                                    tag.put("ItemModifiersAttributes", list);
                                                                    ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.cmd.hand.attribute_added", Names.attribute(attrId), amt, Names.slot(slot)).withStyle(ChatFormatting.GREEN), true);
                                                                    return 1;
                                                                })
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("remove")
                                .then(Commands.literal("potion")
                                        .then(Commands.argument("effect", ResourceArgument.resource(buildContext, Registries.MOB_EFFECT))
                                                .executes(ctx -> {
                                                    ItemStack stack = ctx.getSource().getPlayerOrException().getMainHandItem();
                                                    if (stack.isEmpty()) {
                                                        ctx.getSource().sendFailure(Component.translatable("itemmodifiers.error.no_held_item"));
                                                        return 0;
                                                    }
                                                    Holder.Reference<MobEffect> ref = ResourceArgument.getResource(ctx, "effect", Registries.MOB_EFFECT);
                                                    String effId = ForgeRegistries.MOB_EFFECTS.getKey(ref.value()).toString();
                                                    
                                                    if (stack.hasTag() && stack.getTag().contains("ItemModifiersEffects", 9)) {
                                                        net.minecraft.nbt.ListTag list = stack.getTag().getList("ItemModifiersEffects", 10);
                                                        for (int i = 0; i < list.size(); i++) {
                                                            if (list.getCompound(i).getString("Effect").equals(effId)) {
                                                                list.remove(i);
                                                                ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.cmd.hand.effect_removed", Names.effect(effId)).withStyle(ChatFormatting.YELLOW), true);
                                                                return 1;
                                                            }
                                                        }
                                                    }
                                                    ctx.getSource().sendFailure(Component.translatable("itemmodifiers.cmd.hand.effect_not_found", Names.effect(effId)));
                                                    return 0;
                                                })
                                        )
                                )
                                .then(Commands.literal("attribute")
                                        .then(Commands.argument("attribute", ResourceArgument.resource(buildContext, Registries.ATTRIBUTE))
                                                .then(Commands.argument("slot", StringArgumentType.word())
                                                        .suggests((c, b) -> {
                                                            b.suggest("mainhand"); b.suggest("offhand"); b.suggest("head");
                                                            b.suggest("chest"); b.suggest("legs"); b.suggest("feet"); b.suggest("any");
                                                            return b.buildFuture();
                                                        })
                                                        .executes(ctx -> {
                                                            ItemStack stack = ctx.getSource().getPlayerOrException().getMainHandItem();
                                                            if (stack.isEmpty()) {
                                                                ctx.getSource().sendFailure(Component.translatable("itemmodifiers.error.no_held_item"));
                                                                return 0;
                                                            }
                                                            Holder.Reference<Attribute> ref = ResourceArgument.getResource(ctx, "attribute", Registries.ATTRIBUTE);
                                                            String attrId = ForgeRegistries.ATTRIBUTES.getKey(ref.value()).toString();
                                                            String slot = StringArgumentType.getString(ctx, "slot");
                                                            
                                                            if (stack.hasTag() && stack.getTag().contains("ItemModifiersAttributes", 9)) {
                                                                net.minecraft.nbt.ListTag list = stack.getTag().getList("ItemModifiersAttributes", 10);
                                                                for (int i = 0; i < list.size(); i++) {
                                                                    net.minecraft.nbt.CompoundTag c = list.getCompound(i);
                                                                    if (c.getString("Attribute").equals(attrId) && c.getString("Slot").equalsIgnoreCase(slot)) {
                                                                        list.remove(i);
                                                                        ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.cmd.hand.attribute_removed", Names.attribute(attrId), Names.slot(slot)).withStyle(ChatFormatting.YELLOW), true);
                                                                        return 1;
                                                                    }
                                                                }
                                                            }
                                                            ctx.getSource().sendFailure(Component.translatable("itemmodifiers.cmd.hand.attribute_not_found", Names.attribute(attrId), Names.slot(slot)));
                                                            return 0;
                                                        })
                                                )
                                        )
                                )
                        )
                )
        );
    }

    // WHY: строка описания собиралась склейкой JSON руками: обратная косая или кавычка в тексте
    // WHY: давали неразбираемый тег, и предмет показывал сырой JSON вместо строки
    private static String loreLine(String text) {
        return Component.Serializer.toJson(Component.literal(text)
                .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.WHITE)));
    }

    // WHY: снятое описание оставляло пустой display и корневой тег, и предмет переставал
    // WHY: складываться в стак с точно таким же предметом без описания
    private static void clearLore(ItemStack stack) {
        CompoundTag display = stack.getTagElement("display");
        if (display == null) return;

        display.remove("Lore");
        if (!display.isEmpty() || !stack.hasTag()) return;

        stack.getTag().remove("display");
        if (stack.getTag().isEmpty()) stack.setTag(null);
    }

    private static boolean hasModifiers(ItemStack stack) {
        if (!stack.hasTag()) return false;

        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        return !tag.getList("ItemModifiersEffects", 10).isEmpty()
                || !tag.getList("ItemModifiersAttributes", 10).isEmpty();
    }

    // WHY: пустая рука отвечала молчанием, а предмет без ключа в реестре ронял команду по NPE
    private static String getHeldItemId(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ItemStack stack = ctx.getSource().getPlayerOrException().getMainHandItem();
        ResourceLocation id = stack.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            ctx.getSource().sendFailure(Component.translatable("itemmodifiers.error.no_held_item"));
            return null;
        }
        return id.toString();
    }

    private static String getArgItemId(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Holder.Reference<net.minecraft.world.item.Item> ref = ResourceArgument.getResource(ctx, "item", Registries.ITEM);
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(ref.value());
        return id == null ? "" : id.toString();
    }

    // WHY: снимок для клиента уходит тем же движением, что и сброс кешей: иначе карточка товара
    // WHY: и подсказка предмета показывают старые модификаторы до перезахода игрока
    private static void refresh(CommandContext<CommandSourceStack> ctx) {
        AttributeHandler.markDirty();
        EffectHandler.markDirty();
        if (ctx.getSource().getServer() != null) ZonesMod.syncModifiersToAll();
    }
}
