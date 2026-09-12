package com.persiki84.zones.shop;

import com.persiki84.battlecraft.BattleCraftCommands;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.persiki84.shared.gunsmith.GunSlot;
import com.persiki84.shared.gunsmith.GunSmith;
import com.persiki84.shared.gunsmith.Outcome;
import com.persiki84.zones.ZonesMod;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class ShopCommand {

    private static final SuggestionProvider<CommandSourceStack> SECTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(ShopCatalog.sectionIds(), builder);

    private static final SuggestionProvider<CommandSourceStack> CHILDREN = (context, builder) -> {
        ShopSection section = ShopCatalog.section(StringArgumentType.getString(context, "section"));
        return SharedSuggestionProvider.suggest(section == null ? java.util.List.of() : section.childIds(), builder);
    };

    private static final SuggestionProvider<CommandSourceStack> ENTRIES = (context, builder) -> {
        ShopSection section = ShopCatalog.section(StringArgumentType.getString(context, "section"));
        return SharedSuggestionProvider.suggest(
                section == null ? java.util.List.of() : section.deepEntryIds(), builder);
    };

    private static final SuggestionProvider<CommandSourceStack> TEAMS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    context.getSource().getServer().getScoreboard().getTeamNames(), builder);

    private static final SuggestionProvider<CommandSourceStack> ITEM_IDS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(ForgeRegistries.ITEMS.getKeys(), builder);

    private static final SuggestionProvider<CommandSourceStack> ATTACHMENTS = (context, builder) -> {
        ShopEntry entry = requireEntry(context);
        return SharedSuggestionProvider.suggest(
                entry == null ? java.util.List.of() : GunSmith.options(entry.stack()), builder);
    };

    private static final SuggestionProvider<CommandSourceStack> SLOTS = (context, builder) -> {
        ShopEntry entry = requireEntry(context);
        return SharedSuggestionProvider.suggest(
                entry == null ? java.util.List.of() : slotNames(entry), builder);
    };

    private static final int KEEP_RESTOCK = -1;

    private ShopCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("shop")
                .requires(source -> source.hasPermission(2))
                .executes(context -> BattleCraftCommands.openMenu(context, ModuleMenuStates.SHOP))
                .then(sectionBranch())
                .then(subsectionBranch())
                .then(itemBranch())
                .then(accessBranch())
                .then(Commands.literal("list").executes(ShopCommand::list));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sectionBranch() {
        return Commands.literal("section")
                .then(Commands.literal("add")
                        .then(Commands.argument("section", StringArgumentType.word())
                                .then(Commands.argument("title", StringArgumentType.greedyString())
                                        .executes(ShopCommand::addSection))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("section", StringArgumentType.word())
                                .suggests(SECTIONS)
                                .executes(ShopCommand::removeSection)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> subsectionBranch() {
        return Commands.literal("subsection")
                .then(Commands.literal("add")
                        .then(Commands.argument("section", StringArgumentType.word())
                                .suggests(SECTIONS)
                                .then(Commands.argument("subsection", StringArgumentType.word())
                                        .then(Commands.argument("title", StringArgumentType.greedyString())
                                                .executes(ShopCommand::addSubsection)))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("section", StringArgumentType.word())
                                .suggests(SECTIONS)
                                .then(Commands.argument("subsection", StringArgumentType.word())
                                        .suggests(CHILDREN)
                                        .executes(ShopCommand::removeSubsection))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> accessBranch() {
        return Commands.literal("access")
                .then(Commands.literal("section")
                        .then(access(Commands.argument("section", StringArgumentType.word()).suggests(SECTIONS),
                                ShopCommand::sectionAccess)))
                .then(Commands.literal("subsection")
                        .then(Commands.argument("section", StringArgumentType.word()).suggests(SECTIONS)
                                .then(access(Commands.argument("subsection", StringArgumentType.word())
                                        .suggests(CHILDREN), ShopCommand::subsectionAccess))))
                .then(Commands.literal("item")
                        .then(Commands.argument("section", StringArgumentType.word()).suggests(SECTIONS)
                                .then(access(entryNode(), ShopCommand::entryAccess))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> access(
            ArgumentBuilder<CommandSourceStack, ?> target, AccessTarget resolver) {
        return target
                .then(Commands.literal("everyone")
                        .executes(context -> openAccess(context, resolver)))
                .then(Commands.literal("show")
                        .then(Commands.argument("team", StringArgumentType.word()).suggests(TEAMS)
                                .executes(context -> editAccess(context, resolver, true))))
                .then(Commands.literal("hide")
                        .then(Commands.argument("team", StringArgumentType.word()).suggests(TEAMS)
                                .executes(context -> editAccess(context, resolver, false))));
    }

    private interface AccessTarget {
        ShopAccess resolve(CommandContext<CommandSourceStack> context);
    }

    private static ShopAccess sectionAccess(CommandContext<CommandSourceStack> context) {
        ShopSection section = requireSection(context);
        return section == null ? null : section.access();
    }

    private static ShopAccess subsectionAccess(CommandContext<CommandSourceStack> context) {
        ShopSection section = requireSection(context);
        if (section == null) return null;

        String childId = StringArgumentType.getString(context, "subsection");
        ShopSection child = section.child(childId);
        if (child == null) {
            fail(context, "zones.shop.error.no_subsection", childId);
            return null;
        }
        return child.access();
    }

    private static ShopAccess entryAccess(CommandContext<CommandSourceStack> context) {
        ShopEntry entry = requireEntry(context);
        return entry == null ? null : entry.access();
    }

    private static int openAccess(CommandContext<CommandSourceStack> context, AccessTarget resolver) {
        ShopAccess access = resolver.resolve(context);
        if (access == null) return 0;

        access.showEveryone();
        ShopCatalog.persist();
        return report(context, "zones.shop.success.access_everyone");
    }

    private static int editAccess(CommandContext<CommandSourceStack> context, AccessTarget resolver,
                                  boolean allowed) {
        ShopAccess access = resolver.resolve(context);
        if (access == null) return 0;

        String team = StringArgumentType.getString(context, "team");
        if (context.getSource().getServer().getScoreboard().getPlayerTeam(team) == null) {
            return fail(context, "zones.shop.error.unknown_team", team);
        }

        if (allowed) {
            access.allow(team);
        } else {
            access.forbid(team);
        }
        ShopCatalog.persist();
        return report(context, allowed ? "zones.shop.success.access_shown" : "zones.shop.success.access_hidden", team);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> itemBranch() {
        return Commands.literal("item")
                .then(handBranch())
                .then(byIdBranch())
                .then(Commands.literal("remove")
                        .then(sectionNode(entryNode().executes(ShopCommand::removeEntry))))
                .then(Commands.literal("describe").then(sectionNode(entryNode()
                        .executes(ShopCommand::clearDescription)
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ShopCommand::describeEntry)))))
                .then(Commands.literal("price").then(sectionNode(entryNode()
                        .then(Commands.argument("price", IntegerArgumentType.integer(0))
                                .executes(ShopCommand::repriceEntry)))))
                .then(Commands.literal("attach").then(sectionNode(entryNode()
                        .then(Commands.argument("attachment", StringArgumentType.string())
                                .suggests(ATTACHMENTS)
                                .executes(ShopCommand::attachToEntry)))))
                .then(Commands.literal("detach").then(sectionNode(entryNode()
                        .then(Commands.argument("slot", StringArgumentType.word())
                                .suggests(SLOTS)
                                .executes(ShopCommand::detachFromEntry)))))
                .then(stockBranch())
                .then(restockBranch());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> entryNode() {
        return Commands.argument("entry", StringArgumentType.word()).suggests(ENTRIES);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> sectionNode(ArgumentBuilder<CommandSourceStack, ?> child) {
        return Commands.argument("section", StringArgumentType.word()).suggests(SECTIONS).then(child);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> handBranch() {
        return Commands.literal("hand")
                .then(Commands.argument("section", StringArgumentType.word())
                        .suggests(SECTIONS)
                        .then(Commands.argument("price", IntegerArgumentType.integer(0))
                                .executes(context -> addFromHand(context, null))
                                .then(Commands.argument("subsection", StringArgumentType.word())
                                        .suggests(CHILDREN)
                                        .executes(context -> addFromHand(context,
                                                StringArgumentType.getString(context, "subsection"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> byIdBranch() {
        return Commands.literal("id")
                .then(Commands.argument("section", StringArgumentType.word())
                        .suggests(SECTIONS)
                        .then(Commands.argument("item", StringArgumentType.string())
                                .suggests(ITEM_IDS)
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .then(Commands.argument("price", IntegerArgumentType.integer(0))
                                                .executes(ShopCommand::addById)))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> stockBranch() {
        return Commands.literal("stock").then(sectionNode(entryNode()
                .then(Commands.argument("count", IntegerArgumentType.integer(-1))
                        .executes(context -> setStock(context, KEEP_RESTOCK))
                        .then(Commands.argument("restock", IntegerArgumentType.integer(0))
                                .executes(context -> setStock(context,
                                        IntegerArgumentType.getInteger(context, "restock")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> restockBranch() {
        return Commands.literal("restock").then(sectionNode(entryNode()
                .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                        .executes(ShopCommand::setRestock))));
    }

    // WHY: время завоза правится отдельно от запаса: setStock возвращает остаток к полному и сбрасывает
    // WHY: отсчёт, поэтому правка одних секунд заодно наполняла бы склад посреди боя
    private static int setRestock(CommandContext<CommandSourceStack> context) {
        ShopEntry entry = requireEntry(context);
        if (entry == null) return 0;

        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        entry.setRestockSeconds(seconds);
        ShopCatalog.persist();
        return report(context, seconds > 0 ? "zones.shop.success.restock_set" : "zones.shop.success.restock_off",
                entry.id(), seconds);
    }

    private static int setStock(CommandContext<CommandSourceStack> context, int restockSeconds) {
        ShopEntry entry = requireEntry(context);
        if (entry == null) return 0;

        int seconds = restockSeconds == KEEP_RESTOCK ? entry.restockSeconds() : restockSeconds;
        int items = IntegerArgumentType.getInteger(context, "count");
        if (items < 0) {
            entry.setStock(ShopEntry.UNLIMITED, seconds);
            ShopCatalog.persist();
            return report(context, "zones.shop.success.stock_unlimited", entry.id());
        }

        int bundle = entry.bundle();
        if (items % bundle != 0) {
            return fail(context, "zones.shop.error.stock_step", entry.id(), bundle,
                    items / bundle * bundle, (items / bundle + 1) * bundle);
        }

        entry.setStock(items / bundle, seconds);
        ShopCatalog.persist();
        return report(context, "zones.shop.success.stock_set", entry.id(), items, seconds);
    }

    private static int repriceEntry(CommandContext<CommandSourceStack> context) {
        ShopEntry entry = requireEntry(context);
        if (entry == null) return 0;

        int price = IntegerArgumentType.getInteger(context, "price");
        entry.setPrice(price);
        ShopCatalog.persist();
        return report(context, "zones.shop.success.repriced", entry.id(), price);
    }

    private static int attachToEntry(CommandContext<CommandSourceStack> context) {
        ShopEntry entry = requireEntry(context);
        if (entry == null) return 0;

        String option = StringArgumentType.getString(context, "attachment");
        Outcome outcome = GunSmith.attach(entry.stack(), option);
        if (outcome != Outcome.DONE) return fail(context, refusal(outcome), GunSmith.optionName(option));

        ShopCatalog.persist();
        ZonesMod.syncShopToEveryone(context.getSource().getServer());
        return report(context, "zones.shop.success.attached", GunSmith.optionName(option), entry.stack().getHoverName());
    }

    private static java.util.List<String> slotNames(ShopEntry entry) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (GunSlot slot : GunSmith.slots(entry.stack())) {
            names.add(slot.id());
        }
        return names;
    }

    private static int detachFromEntry(CommandContext<CommandSourceStack> context) {
        ShopEntry entry = requireEntry(context);
        if (entry == null) return 0;

        String slot = StringArgumentType.getString(context, "slot");
        Outcome outcome = GunSmith.detach(entry.stack(), slot);
        if (outcome != Outcome.DONE) return fail(context, refusal(outcome), GunSmith.slotName(slot));

        ShopCatalog.persist();
        ZonesMod.syncShopToEveryone(context.getSource().getServer());
        return report(context, "zones.shop.success.detached", GunSmith.slotName(slot), entry.stack().getHoverName());
    }

    private static String refusal(Outcome outcome) {
        return switch (outcome) {
            case NOT_A_GUN -> "zones.shop.error.attach_not_a_gun";
            case INCOMPATIBLE -> "zones.shop.error.attach_incompatible";
            default -> "zones.shop.error.attach_unknown";
        };
    }

    private static ShopEntry requireEntry(CommandContext<CommandSourceStack> context) {
        ShopSection owner = entryOwner(context);
        return owner == null ? null : owner.entry(StringArgumentType.getString(context, "entry"));
    }

    private static ShopSection entryOwner(CommandContext<CommandSourceStack> context) {
        ShopSection section = requireSection(context);
        if (section == null) return null;

        String entryId = StringArgumentType.getString(context, "entry");
        ShopSection owner = section.ownerOf(entryId);
        if (owner == null) fail(context, "zones.shop.error.no_entry_named", entryId);
        return owner;
    }

    private static int addSection(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "section");
        ShopCatalog.createSection(id, StringArgumentType.getString(context, "title"));
        return report(context, "zones.shop.success.section_added", id);
    }

    private static int removeSection(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "section");
        if (!ShopCatalog.removeSection(id)) return fail(context, "zones.shop.error.no_section", id);
        return report(context, "zones.shop.success.section_removed", id);
    }

    private static int addSubsection(CommandContext<CommandSourceStack> context) {
        ShopSection section = requireSection(context);
        if (section == null) return 0;

        String childId = StringArgumentType.getString(context, "subsection");
        section.children().put(childId, new ShopSection(childId, StringArgumentType.getString(context, "title")));
        ShopCatalog.persist();
        return report(context, "zones.shop.success.subsection_added", childId);
    }

    private static int removeSubsection(CommandContext<CommandSourceStack> context) {
        ShopSection section = requireSection(context);
        if (section == null) return 0;

        String childId = StringArgumentType.getString(context, "subsection");
        if (section.children().remove(childId) == null) return fail(context, "zones.shop.error.no_subsection", childId);

        ShopCatalog.persist();
        return report(context, "zones.shop.success.subsection_removed", childId);
    }

    private static int addFromHand(CommandContext<CommandSourceStack> context, String childId) throws CommandSyntaxException {
        ShopSection target = targetSection(context, childId);
        if (target == null) return 0;

        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return fail(context, "zones.shop.error.empty_hand");

        return storeEntry(context, target, held.copy(), IntegerArgumentType.getInteger(context, "price"));
    }

    private static int addById(CommandContext<CommandSourceStack> context) {
        ShopSection target = targetSection(context, null);
        if (target == null) return 0;

        ResourceLocation itemId = ResourceLocation.tryParse(StringArgumentType.getString(context, "item"));
        Item item = itemId == null ? null : ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null) return fail(context, "zones.shop.error.no_item");

        ItemStack stack = new ItemStack(item, IntegerArgumentType.getInteger(context, "count"));
        return storeEntry(context, target, stack, IntegerArgumentType.getInteger(context, "price"));
    }

    private static int storeEntry(CommandContext<CommandSourceStack> context, ShopSection target, ItemStack stack, int price) {
        String entryId = nextEntryId(target, stack);
        target.entries().put(entryId, new ShopEntry(entryId, stack, price, null));
        ShopCatalog.persist();
        return report(context, "zones.shop.success.item_added", entryId, price);
    }

    private static String nextEntryId(ShopSection section, ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String base = key == null ? "item" : key.getPath();
        String candidate = base;
        int suffix = 2;
        while (section.entries().containsKey(candidate)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private static int removeEntry(CommandContext<CommandSourceStack> context) {
        ShopSection owner = entryOwner(context);
        if (owner == null) return 0;

        String entryId = StringArgumentType.getString(context, "entry");
        owner.entries().remove(entryId);
        ShopCatalog.persist();
        return report(context, "zones.shop.success.item_removed", entryId);
    }

    private static int describeEntry(CommandContext<CommandSourceStack> context) {
        ShopEntry entry = requireEntry(context);
        if (entry == null) return 0;

        String entryId = entry.id();
        entry.setDescription(StringArgumentType.getString(context, "text"));
        ShopCatalog.persist();
        return report(context, "zones.shop.success.described", entryId);
    }

    private static int clearDescription(CommandContext<CommandSourceStack> context) {
        ShopEntry entry = requireEntry(context);
        if (entry == null) return 0;

        String entryId = entry.id();
        entry.setDescription(null);
        ShopCatalog.persist();
        return report(context, "zones.shop.success.described_off", entryId);
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        if (ShopCatalog.isEmpty()) {
            context.getSource().sendSuccess(() ->
                    Component.translatable("zones.shop.info.empty").withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        for (ShopSection section : ShopCatalog.sections()) {
            context.getSource().sendSuccess(() -> Component.literal(section.id() + " (" + section.title() + ")")
                    .withStyle(ChatFormatting.GOLD), false);
            describeChildren(context, section);
        }
        return 1;
    }

    private static void describeChildren(CommandContext<CommandSourceStack> context, ShopSection section) {
        for (ShopEntry entry : section.entries().values()) {
            context.getSource().sendSuccess(() -> Component.literal("  " + entry.id() + " x" + entry.stack().getCount()
                    + " = " + entry.price()).withStyle(ChatFormatting.GRAY), false);
        }
        for (ShopSection child : section.children().values()) {
            context.getSource().sendSuccess(() -> Component.literal("  " + child.id() + " (" + child.title() + ")")
                    .withStyle(ChatFormatting.YELLOW), false);
            for (ShopEntry entry : child.entries().values()) {
                context.getSource().sendSuccess(() -> Component.literal("    " + entry.id() + " x"
                        + entry.stack().getCount() + " = " + entry.price()).withStyle(ChatFormatting.GRAY), false);
            }
        }
    }

    private static ShopSection requireSection(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "section");
        ShopSection section = ShopCatalog.section(id);
        if (section == null) fail(context, "zones.shop.error.no_section", id);
        return section;
    }

    private static ShopSection targetSection(CommandContext<CommandSourceStack> context, String childId) {
        ShopSection section = requireSection(context);
        if (section == null) return null;
        if (childId == null) return section;

        ShopSection child = section.child(childId);
        if (child == null) fail(context, "zones.shop.error.no_subsection", childId);
        return child;
    }

    private static int report(CommandContext<CommandSourceStack> context, String key, Object... args) {
        context.getSource().sendSuccess(() ->
                Component.translatable(key, args).withStyle(ChatFormatting.GREEN), true);
        ZonesMod.syncShopToEveryone(context.getSource().getServer());
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> context, String key, Object... args) {
        context.getSource().sendFailure(Component.translatable(key, args).withStyle(ChatFormatting.RED));
        return 0;
    }
}
