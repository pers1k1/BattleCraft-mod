package com.persiki84.zones.shop;

import com.persiki84.battlecraft.BattleCraftCommands;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.mojang.brigadier.StringReader;
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
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class ShopCommand {
    private static final int INVENTORY_SLOTS = 41;

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

    private static final SuggestionProvider<CommandSourceStack> TARGET_CHILDREN = (context, builder) -> {
        ShopSection section = ShopCatalog.section(StringArgumentType.getString(context, "target"));
        return SharedSuggestionProvider.suggest(section == null ? java.util.List.of() : section.childIds(), builder);
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
    private static final int TITLE_LIMIT = 48;

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
                                .executes(ShopCommand::removeSection)))
                .then(Commands.literal("rename")
                        .then(Commands.argument("section", StringArgumentType.word()).suggests(SECTIONS)
                                .then(Commands.argument("title", StringArgumentType.greedyString())
                                        .executes(ShopCommand::renameSection))))
                .then(Commands.literal("order")
                        .then(order(Commands.argument("section", StringArgumentType.word()).suggests(SECTIONS),
                                ShopCommand::orderSection)
                                .then(Commands.literal("to")
                                        .then(Commands.argument("position", IntegerArgumentType.integer(1))
                                                .executes(ShopCommand::placeSection)))));
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
                                        .executes(ShopCommand::removeSubsection))))
                .then(Commands.literal("rename")
                        .then(sectionNode(Commands.argument("subsection", StringArgumentType.word())
                                .suggests(CHILDREN)
                                .then(Commands.argument("title", StringArgumentType.greedyString())
                                        .executes(ShopCommand::renameSubsection)))))
                .then(Commands.literal("order")
                        .then(sectionNode(order(Commands.argument("subsection", StringArgumentType.word())
                                .suggests(CHILDREN), ShopCommand::orderSubsection)
                                .then(Commands.literal("to")
                                        .then(Commands.argument("position", IntegerArgumentType.integer(1))
                                                .executes(ShopCommand::placeSubsection))))))
                .then(Commands.literal("move")
                        .then(sectionNode(Commands.argument("subsection", StringArgumentType.word())
                                .suggests(CHILDREN)
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .suggests(SECTIONS)
                                        .executes(ShopCommand::moveSubsection)))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> order(
            ArgumentBuilder<CommandSourceStack, ?> target, Reorder reorder) {
        return target
                .then(Commands.literal("up").executes(context -> reorder.apply(context, ShopOrder.UP)))
                .then(Commands.literal("down").executes(context -> reorder.apply(context, ShopOrder.DOWN)));
    }

    private interface Reorder {
        int apply(CommandContext<CommandSourceStack> context, int delta);
    }

    private static int orderSection(CommandContext<CommandSourceStack> context, int delta) {
        String id = StringArgumentType.getString(context, "section");
        if (!ShopCatalog.moveSection(id, delta)) return fail(context, "zones.shop.error.no_move", id);
        return report(context, "zones.shop.success.moved", id);
    }

    private static int orderSubsection(CommandContext<CommandSourceStack> context, int delta) {
        ShopSection section = requireSection(context);
        if (section == null) return 0;

        String childId = StringArgumentType.getString(context, "subsection");
        if (!section.moveChild(childId, delta)) return fail(context, "zones.shop.error.no_move", childId);

        ShopCatalog.persist();
        return report(context, "zones.shop.success.moved", childId);
    }

    private static int orderEntry(CommandContext<CommandSourceStack> context, int delta) {
        ShopSection section = requireSection(context);
        if (section == null) return 0;

        String entryId = StringArgumentType.getString(context, "entry");
        if (!section.moveEntry(entryId, delta)) return fail(context, "zones.shop.error.no_move", entryId);

        ShopCatalog.persist();
        return report(context, "zones.shop.success.moved", entryId);
    }

    // WHY: витрина переставляет плитку сразу на место, а не по шагу: посылать десяток «вниз»
    // WHY: значит десять раз переписать каталог и десять раз разослать его игрокам
    private static int placeEntry(CommandContext<CommandSourceStack> context) {
        ShopSection section = requireSection(context);
        if (section == null) return 0;

        String entryId = StringArgumentType.getString(context, "entry");
        ShopSection owner = section.ownerOf(entryId);
        if (owner == null) return fail(context, "zones.shop.error.no_entry", entryId);

        int from = owner.entryIds().indexOf(entryId);
        int to = IntegerArgumentType.getInteger(context, "position") - 1;
        if (!owner.moveEntry(entryId, to - from)) return fail(context, "zones.shop.error.no_move", entryId);

        ShopCatalog.persist();
        return report(context, "zones.shop.success.moved", entryId);
    }

    private static int placeSection(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "section");
        int position = IntegerArgumentType.getInteger(context, "position") - 1;
        if (!ShopCatalog.moveSectionTo(id, position)) return fail(context, "zones.shop.error.no_move", id);
        return report(context, "zones.shop.success.moved", id);
    }

    private static int placeSubsection(CommandContext<CommandSourceStack> context) {
        ShopSection section = requireSection(context);
        if (section == null) return 0;

        String childId = StringArgumentType.getString(context, "subsection");
        int position = IntegerArgumentType.getInteger(context, "position") - 1;
        if (!ShopOrder.moveTo(section.children(), childId, position)) {
            return fail(context, "zones.shop.error.no_move", childId);
        }
        ShopCatalog.persist();
        return report(context, "zones.shop.success.moved", childId);
    }

    private static int renameSection(CommandContext<CommandSourceStack> context) {
        ShopSection section = requireSection(context);
        if (section == null) return 0;
        return rename(context, section);
    }

    private static int renameSubsection(CommandContext<CommandSourceStack> context) {
        ShopSection section = requireSection(context);
        if (section == null) return 0;

        String childId = StringArgumentType.getString(context, "subsection");
        ShopSection child = section.child(childId);
        if (child == null) return fail(context, "zones.shop.error.no_subsection", childId);
        return rename(context, child);
    }

    private static int rename(CommandContext<CommandSourceStack> context, ShopSection target) {
        String title = cleanTitle(context);
        if (title == null) return 0;

        target.rename(title);
        ShopCatalog.persist();
        return report(context, "zones.shop.success.renamed", target.id(), title);
    }

    private static String cleanTitle(CommandContext<CommandSourceStack> context) {
        String title = StringArgumentType.getString(context, "title").trim();
        if (title.isEmpty() || title.length() > TITLE_LIMIT) {
            fail(context, "zones.shop.error.bad_title", TITLE_LIMIT);
            return null;
        }
        return title;
    }

    private static int moveSubsection(CommandContext<CommandSourceStack> context) {
        ShopSection section = requireSection(context);
        if (section == null) return 0;

        String childId = StringArgumentType.getString(context, "subsection");
        ShopSection child = section.child(childId);
        if (child == null) return fail(context, "zones.shop.error.no_subsection", childId);

        String targetId = StringArgumentType.getString(context, "target");
        ShopSection target = ShopCatalog.section(targetId);
        if (target == null) return fail(context, "zones.shop.error.no_section", targetId);
        if (target == section) return fail(context, "zones.shop.error.same_place", childId);
        if (target.child(childId) != null) return fail(context, "zones.shop.error.subsection_taken", childId);

        section.children().remove(childId);
        target.children().put(childId, adopt(target, child));
        ShopCatalog.persist();
        return report(context, "zones.shop.success.subsection_moved", childId, target.title());
    }

    // WHY: товар ищется по идентификатору внутри раздела вместе с отделами, поэтому переехавший
    // WHY: отдел отдаёт свои товары под свободными именами, иначе один из двух станет недоступен
    private static ShopSection adopt(ShopSection target, ShopSection child) {
        for (String entryId : child.entryIds()) {
            if (target.ownerOf(entryId) == null) continue;

            ShopEntry entry = child.entries().remove(entryId);
            child.place(entry.renamed(freeEntryId(entry.stack(), target, child)));
        }
        return child;
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
                .then(slotBranch())
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
                .then(Commands.literal("order").then(sectionNode(order(entryNode(), ShopCommand::orderEntry)
                        .then(Commands.literal("to")
                                .then(Commands.argument("position", IntegerArgumentType.integer(1))
                                        .executes(ShopCommand::placeEntry))))))
                .then(moveItemBranch())
                .then(Commands.literal("count").then(sectionNode(entryNode()
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, ShopEntry.BUNDLE_LIMIT))
                                .executes(ShopCommand::resizeEntry)))))
                .then(Commands.literal("copy").then(sectionNode(entryNode().executes(ShopCommand::copyEntry))))
                .then(stockBranch())
                .then(restockBranch())
                .then(scopeBranch());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> moveItemBranch() {
        return Commands.literal("move").then(sectionNode(entryNode()
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(SECTIONS)
                        .executes(context -> moveEntry(context, null))
                        .then(Commands.argument("subsection", StringArgumentType.word())
                                .suggests(TARGET_CHILDREN)
                                .executes(context -> moveEntry(context,
                                        StringArgumentType.getString(context, "subsection")))))));
    }

    private static int moveEntry(CommandContext<CommandSourceStack> context, String childId) {
        ShopSection owner = entryOwner(context);
        if (owner == null) return 0;

        String targetId = StringArgumentType.getString(context, "target");
        ShopSection top = ShopCatalog.section(targetId);
        if (top == null) return fail(context, "zones.shop.error.no_section", targetId);

        ShopSection target = childId == null ? top : top.child(childId);
        if (target == null) return fail(context, "zones.shop.error.no_subsection", childId);

        String entryId = StringArgumentType.getString(context, "entry");
        if (target == owner) return fail(context, "zones.shop.error.same_place", entryId);

        ShopEntry entry = owner.entries().remove(entryId);
        ShopEntry placed = top.ownerOf(entryId) == null ? entry : entry.renamed(freeEntryId(entry.stack(), top));
        target.place(placed);
        ShopCatalog.persist();
        return report(context, "zones.shop.success.item_moved", placed.id(), target.title());
    }

    private static int resizeEntry(CommandContext<CommandSourceStack> context) {
        ShopEntry entry = requireEntry(context);
        if (entry == null) return 0;

        int count = IntegerArgumentType.getInteger(context, "count");
        entry.setBundle(count);
        ShopCatalog.persist();
        return report(context, "zones.shop.success.resized", entry.id(), count);
    }

    private static int copyEntry(CommandContext<CommandSourceStack> context) {
        ShopSection owner = entryOwner(context);
        ShopSection top = owner == null ? null : requireSection(context);
        if (top == null) return 0;

        ShopEntry entry = owner.entry(StringArgumentType.getString(context, "entry"));
        ShopEntry copy = entry.copied(freeEntryId(entry.stack(), top));
        owner.place(copy);
        ShopOrder.moveTo(owner.entries(), copy.id(), owner.entryIds().indexOf(entry.id()) + 1);
        ShopCatalog.persist();
        return report(context, "zones.shop.success.copied", entry.id(), copy.id());
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

    private static LiteralArgumentBuilder<CommandSourceStack> slotBranch() {
        return Commands.literal("slot")
                .then(Commands.argument("section", StringArgumentType.word())
                        .suggests(SECTIONS)
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0, INVENTORY_SLOTS - 1))
                                .then(Commands.argument("price", IntegerArgumentType.integer(0))
                                        .executes(context -> addFromSlot(context, null))
                                        .then(Commands.argument("subsection", StringArgumentType.word())
                                                .suggests(CHILDREN)
                                                .executes(context -> addFromSlot(context,
                                                        StringArgumentType.getString(context, "subsection")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> byIdBranch() {
        return Commands.literal("id")
                .then(Commands.argument("section", StringArgumentType.word())
                        .suggests(SECTIONS)
                        .then(Commands.argument("item", StringArgumentType.string())
                                .suggests(ITEM_IDS)
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .then(Commands.argument("price", IntegerArgumentType.integer(0))
                                                .executes(context -> addById(context, null))
                                                .then(Commands.argument("subsection", StringArgumentType.word())
                                                        .suggests(CHILDREN)
                                                        .executes(context -> addById(context,
                                                                StringArgumentType.getString(context, "subsection"))))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> stockBranch() {
        return Commands.literal("stock").then(sectionNode(entryNode()
                .then(Commands.argument("count", IntegerArgumentType.integer(-1))
                        .executes(context -> setStock(context, KEEP_RESTOCK))
                        .then(Commands.argument("restock", IntegerArgumentType.integer(0))
                                .executes(context -> setStock(context,
                                        IntegerArgumentType.getInteger(context, "restock")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> scopeBranch() {
        RequiredArgumentBuilder<CommandSourceStack, String> entry = entryNode();
        for (StockScope scope : StockScope.values()) {
            entry.then(Commands.literal(scope.id()).executes(context -> setScope(context, scope)));
        }
        return Commands.literal("scope").then(sectionNode(entry));
    }

    // WHY: область склада решает, чей остаток тратит покупка: общий на всех, свой у команды
    // WHY: или свой у каждого игрока - поэтому смена области возвращает всем полный запас
    private static int setScope(CommandContext<CommandSourceStack> context, StockScope scope) {
        ShopEntry entry = requireEntry(context);
        if (entry == null) return 0;

        entry.setScope(scope);
        ShopCatalog.persist();
        return report(context, "zones.shop.success.scope_set", entry.id(),
                Component.translatable(scope.label()));
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

    // WHY: занятый идентификатор отказывается, а не перезаписывается: новый раздел под тем же
    // WHY: именем молча стирал весь прежний вместе с товарами, складом и доступом
    private static int addSection(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "section");
        if (ShopCatalog.section(id) != null) return fail(context, "zones.shop.error.section_taken", id);

        String title = cleanTitle(context);
        if (title == null) return 0;

        ShopCatalog.createSection(id, title);
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
        if (section.child(childId) != null) return fail(context, "zones.shop.error.subsection_taken", childId);

        String title = cleanTitle(context);
        if (title == null) return 0;

        section.children().put(childId, new ShopSection(childId, title));
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

    // WHY: редактор называет слот инвентаря, а сам стак сервер берёт у себя: присланный клиентом
    // WHY: предмет с NBT был бы заявкой, которой верить нельзя
    private static int addFromSlot(CommandContext<CommandSourceStack> context, String childId) throws CommandSyntaxException {
        ShopSection target = targetSection(context, childId);
        if (target == null) return 0;

        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack stack = player.getInventory().getItem(IntegerArgumentType.getInteger(context, "slot"));
        if (stack.isEmpty()) return fail(context, "zones.shop.error.empty_hand");

        return storeEntry(context, target, stack.copy(), IntegerArgumentType.getInteger(context, "price"));
    }

    private static int addById(CommandContext<CommandSourceStack> context, String childId) {
        ShopSection target = targetSection(context, childId);
        if (target == null) return 0;

        ItemStack stack = parseItem(StringArgumentType.getString(context, "item"));
        if (stack.isEmpty()) return fail(context, "zones.shop.error.no_item");

        stack.setCount(IntegerArgumentType.getInteger(context, "count"));
        return storeEntry(context, target, stack, IntegerArgumentType.getInteger(context, "price"));
    }

    // WHY: редактор шлёт предмет вместе с тегом (ствол TACZ это один предмет с GunId), поэтому
    // WHY: строка разбирается синтаксисом аргумента предмета, а не голым идентификатором
    private static ItemStack parseItem(String text) {
        try {
            ItemParser.ItemResult parsed = ItemParser.parseForItem(BuiltInRegistries.ITEM.asLookup(), new StringReader(text));
            ItemStack stack = new ItemStack(parsed.item());
            if (parsed.nbt() != null) stack.setTag(parsed.nbt().copy());
            return stack;
        } catch (CommandSyntaxException invalid) {
            return ItemStack.EMPTY;
        }
    }

    private static int storeEntry(CommandContext<CommandSourceStack> context, ShopSection target, ItemStack stack, int price) {
        ShopSection top = requireSection(context);
        if (top == null) return 0;

        ShopEntry entry = new ShopEntry(freeEntryId(stack, top), stack, price, null);
        target.place(entry);
        ShopCatalog.persist();
        return report(context, "zones.shop.success.item_added", entry.id(), price);
    }

    // WHY: раздел ищет товар у себя и во всех своих отделах, поэтому одинаковый идентификатор
    // WHY: в разделе и в его отделе сделал бы второй товар недоступным ни одной команде
    private static String freeEntryId(ItemStack stack, ShopSection... scopes) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String base = key == null ? "item" : key.getPath();
        String candidate = base;
        int suffix = 2;
        while (taken(candidate, scopes)) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private static boolean taken(String entryId, ShopSection[] scopes) {
        for (ShopSection scope : scopes) {
            if (scope.ownerOf(entryId) != null) return true;
        }
        return false;
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
        String text = StringArgumentType.getString(context, "text");
        if (text.length() > ShopEntry.DESCRIPTION_LIMIT) {
            context.getSource().sendFailure(Component.translatable("zones.shop.error.description_too_long",
                    ShopEntry.DESCRIPTION_LIMIT).withStyle(ChatFormatting.RED));
            return 0;
        }
        entry.setDescription(text);
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
