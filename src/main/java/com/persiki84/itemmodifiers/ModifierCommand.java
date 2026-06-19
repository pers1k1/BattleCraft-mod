package com.persiki84.itemmodifiers;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
                                    ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.info.item", item).withStyle(ChatFormatting.AQUA), false);
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
                                            ListTag lore = tag.getList("Lore", 8);
                                            String escaped = formattedText.replace("\\", "\\\\").replace("\"", "\\\"");
                                            lore.add(StringTag.valueOf("{\"text\":\"" + escaped + "\",\"italic\":false,\"color\":\"white\"}"));

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
                                    CompoundTag tag = stack.getTagElement("display");
                                    if(tag != null) tag.remove("Lore");
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
                            refresh();
                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.clear.all", id).withStyle(ChatFormatting.YELLOW), true);
                            return 1;
                        })
                        .then(Commands.literal("potions")
                                .executes(ctx -> {
                                    String id = getHeldItemId(ctx);
                                    if (id == null) return 0;
                                    ModifierConfig.clearPotions(id);
                                    refresh();
                                    ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.clear.potions", id).withStyle(ChatFormatting.YELLOW), true);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("attributes")
                                .executes(ctx -> {
                                    String id = getHeldItemId(ctx);
                                    if (id == null) return 0;
                                    ModifierConfig.clearAttributes(id);
                                    refresh();
                                    ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.clear.attributes", id).withStyle(ChatFormatting.YELLOW), true);
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
                                            refresh();
                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.remove.potion", effId, id).withStyle(ChatFormatting.YELLOW), true);
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
                                            refresh();
                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.remove.attribute", attrId, id).withStyle(ChatFormatting.YELLOW), true);
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
                                                    refresh();
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.addpotion", id).withStyle(ChatFormatting.GREEN), true);
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
                                                            refresh();
                                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.addattribute", id).withStyle(ChatFormatting.GREEN), true);
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
                                            refresh();
                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.clear.all", id).withStyle(ChatFormatting.YELLOW), true);
                                            return 1;
                                        })
                                        .then(Commands.literal("potions")
                                                .executes(ctx -> {
                                                    String id = getArgItemId(ctx);
                                                    ModifierConfig.clearPotions(id);
                                                    refresh();
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.clear.potions", id).withStyle(ChatFormatting.YELLOW), true);
                                                    return 1;
                                                })
                                        )
                                        .then(Commands.literal("attributes")
                                                .executes(ctx -> {
                                                    String id = getArgItemId(ctx);
                                                    ModifierConfig.clearAttributes(id);
                                                    refresh();
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.clear.attributes", id).withStyle(ChatFormatting.YELLOW), true);
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
                                                            refresh();
                                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.remove.potion", effId, id).withStyle(ChatFormatting.YELLOW), true);
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
                                                            refresh();
                                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.remove.attribute", attrId, id).withStyle(ChatFormatting.YELLOW), true);
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
                                                                    refresh();
                                                                    ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.addpotion", id).withStyle(ChatFormatting.GREEN), true);
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
                                                                            refresh();
                                                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.command.addattribute", id).withStyle(ChatFormatting.GREEN), true);
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
                                    ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.cmd.hand.header").withStyle(ChatFormatting.GOLD), false);
                                    if (stack.hasTag() && stack.getTag().contains("ItemModifiersEffects", 9)) {
                                        net.minecraft.nbt.ListTag list = stack.getTag().getList("ItemModifiersEffects", 10);
                                        for (int i = 0; i < list.size(); i++) {
                                            net.minecraft.nbt.CompoundTag c = list.getCompound(i);
                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.cmd.hand.effect_entry", c.getString("Effect"), c.getInt("Level"), c.getString("Type")).withStyle(ChatFormatting.AQUA), false);
                                        }
                                    }
                                    if (stack.hasTag() && stack.getTag().contains("ItemModifiersAttributes", 9)) {
                                        net.minecraft.nbt.ListTag list = stack.getTag().getList("ItemModifiersAttributes", 10);
                                        for (int i = 0; i < list.size(); i++) {
                                            net.minecraft.nbt.CompoundTag c = list.getCompound(i);
                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.cmd.hand.attribute_entry", c.getString("Attribute"), c.getDouble("Amount"), c.getInt("Operation"), c.getString("Slot")).withStyle(ChatFormatting.AQUA), false);
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
                                                            ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.cmd.hand.effect_added", effId, lvl).withStyle(ChatFormatting.GREEN), true);
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
                                                                    ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.cmd.hand.attribute_added", attrId, amt, slot).withStyle(ChatFormatting.GREEN), true);
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
                                                                ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.cmd.hand.effect_removed", effId).withStyle(ChatFormatting.YELLOW), true);
                                                                return 1;
                                                            }
                                                        }
                                                    }
                                                    ctx.getSource().sendFailure(Component.translatable("itemmodifiers.cmd.hand.effect_not_found", effId));
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
                                                                        ctx.getSource().sendSuccess(() -> Component.translatable("itemmodifiers.cmd.hand.attribute_removed", attrId, slot).withStyle(ChatFormatting.YELLOW), true);
                                                                        return 1;
                                                                    }
                                                                }
                                                            }
                                                            ctx.getSource().sendFailure(Component.translatable("itemmodifiers.cmd.hand.attribute_not_found", attrId, slot));
                                                            return 0;
                                                        })
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static String getHeldItemId(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ItemStack stack = ctx.getSource().getPlayerOrException().getMainHandItem();
        if (stack.isEmpty()) return null;
        return ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
    }

    private static String getArgItemId(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Holder.Reference<net.minecraft.world.item.Item> ref = ResourceArgument.getResource(ctx, "item", Registries.ITEM);
        return ForgeRegistries.ITEMS.getKey(ref.value()).toString();
    }

    private static void refresh() {
        AttributeHandler.markDirty();
        EffectHandler.markDirty();
    }
}
