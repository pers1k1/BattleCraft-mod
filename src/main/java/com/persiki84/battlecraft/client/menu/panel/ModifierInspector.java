package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.shared.AmountText;
import com.persiki84.shared.Names;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.FieldRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.MenuRow;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.menu.studio.StudioStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// WHY: у выбранного предмета справа сначала то, что на нём уже висит, каждой строкой со снятием,
// WHY: потом формы нового эффекта и атрибута: рука пишет в NBT стака, вид предмета - в конфиг
final class ModifierInspector {
    private static final String HAND_COMMAND = "ie hand";
    private static final int ROW = 22;
    private static final int MAX_LEVEL = 255;
    private static final int ADDITION = 0;
    private static final int AMOUNT_SCALE = 100_000;
    private static final int DECIMALS = String.valueOf(AMOUNT_SCALE).length() - 1;
    private static final int AMOUNT_STEP = 1_000;
    private static final int MAX_AMOUNT = 1_000 * AMOUNT_SCALE;
    private static final int LORE_LENGTH = 128;
    private static final String EFFECTS_KEY = "ItemModifiersEffects";
    private static final String ATTRIBUTES_KEY = "ItemModifiersAttributes";
    private static final String[] SLOTS = {"mainhand", "offhand", "head", "chest", "legs", "feet", "any"};
    private static final String[] OPERATIONS = {"add", "multiply_base", "multiply_total"};

    private final ModifierStudioScreen screen;
    private final Map<String, MenuRow> made = new HashMap<>();
    private final Map<String, HeadingRow> headings = new HashMap<>();
    private final List<ResourceLocation> effectIds = new ArrayList<>();
    private final List<Component> effectNames = new ArrayList<>();
    private final List<ResourceLocation> attributeIds = new ArrayList<>();
    private final List<Component> attributeNames = new ArrayList<>();
    private final FieldRow loreRow;
    private int effect;
    private int level;
    private int kind;
    private int attribute;
    private int amount = AMOUNT_SCALE;
    private int operation;
    private int slot;

    ModifierInspector(ModifierStudioScreen screen) {
        this.screen = screen;
        for (ResourceLocation id : BuiltInRegistries.MOB_EFFECT.keySet()) {
            MobEffect value = BuiltInRegistries.MOB_EFFECT.get(id);
            if (value == null) continue;
            effectIds.add(id);
            effectNames.add(value.getDisplayName());
        }
        for (ResourceLocation id : BuiltInRegistries.ATTRIBUTE.keySet()) {
            Attribute value = BuiltInRegistries.ATTRIBUTE.get(id);
            if (value == null) continue;
            attributeIds.add(id);
            attributeNames.add(Component.translatable(value.getDescriptionId()));
        }
        loreRow = new FieldRow(0, 0, 10, ROW, Component.translatable("itemmodifiers.menu.lore.text"),
                Component.translatable("itemmodifiers.menu.lore.placeholder"), "", LORE_LENGTH, value -> { });
        loreRow.hint("itemmodifiers.menu.lore.text.hint");
    }

    static int handCount() {
        return handEntries(EFFECTS_KEY).size() + handEntries(ATTRIBUTES_KEY).size();
    }

    private static List<CompoundTag> handEntries(String key) {
        ItemStack stack = ModifierStudioScreen.held();
        if (stack.isEmpty() || !stack.hasTag() || !stack.getTag().contains(key, Tag.TAG_LIST)) return List.of();
        ListTag stored = stack.getTag().getList(key, Tag.TAG_COMPOUND);
        List<CompoundTag> entries = new ArrayList<>();
        for (int index = 0; index < stored.size(); index++) {
            entries.add(stored.getCompound(index));
        }
        return entries;
    }

    private static <T extends AbstractWidget> T sized(T widget, int width) {
        widget.setWidth(width);
        return widget;
    }

    private MenuRow row(String key, Supplier<MenuRow> maker) {
        return made.computeIfAbsent(key, unused -> maker.get());
    }

    private void heading(StudioStack stack, int x, int width, String key) {
        HeadingRow heading = headings.computeIfAbsent(key,
                unused -> new HeadingRow(0, 0, 10, ROW, Component.translatable(key)));
        stack.add(sized(heading, width), x, ROW / 3);
    }

    private void add(StudioStack stack, int x, int width, AbstractWidget widget) {
        stack.add(sized(widget, width), x);
    }

    void build(StudioStack stack, int x, int width) {
        if (ModifierStudioScreen.COMMON.equals(screen.node())) {
            buildCommon(stack, x, width);
            return;
        }
        heading(stack, x, width, "studio.modifiers.group.current");
        if (screen.handChosen()) buildHandEntries(stack, x, width);
        else buildKindEntries(stack, x, width);
        buildEffectForm(stack, x, width);
        buildAttributeForm(stack, x, width);
        if (screen.handChosen()) buildLore(stack, x, width);
        add(stack, x, width, row("clear:" + screen.chosen(), () -> new ActionRow(0, 0, 10, ROW,
                Component.translatable(screen.handChosen() ? "itemmodifiers.menu.hand_clear" : "itemmodifiers.menu.item.clear"),
                () -> Component.translatable("itemmodifiers.menu.action.clear"),
                () -> screen.now(base() + " clear")).alerting()));
    }

    private String base() {
        return screen.handChosen() ? HAND_COMMAND : "ie item " + screen.chosen();
    }

    private void buildHandEntries(StudioStack stack, int x, int width) {
        int shown = 0;
        for (CompoundTag entry : handEntries(EFFECTS_KEY)) {
            String id = entry.getString("Effect");
            add(stack, x, width, entryRow("hand:" + entry, Names.effect(id),
                    effectValue(entry.getInt("Level"), "DEBUFF".equals(entry.getString("Type"))),
                    HAND_COMMAND + " remove potion " + id));
            shown++;
        }
        for (CompoundTag entry : handEntries(ATTRIBUTES_KEY)) {
            String id = entry.getString("Attribute");
            String place = entry.getString("Slot");
            add(stack, x, width, entryRow("hand:" + entry,
                    Component.translatable("itemmodifiers.menu.item.slotted", Names.attribute(id), Names.slot(place)),
                    attributeValue(entry.getDouble("Amount"), entry.getInt("Operation")),
                    HAND_COMMAND + " remove attribute " + id + " " + place));
            shown++;
        }
        if (shown == 0) add(stack, x, width, reading("itemmodifiers.menu.item.clean"));
    }

    private void buildKindEntries(StudioStack stack, int x, int width) {
        CompoundTag item = ModifierStudioScreen.tracked(screen.chosen());
        int shown = addStored(stack, x, width, item.getList("effects", Tag.TAG_STRING), true);
        shown += addStored(stack, x, width, item.getList("attributes", Tag.TAG_STRING), false);
        if (shown == 0) add(stack, x, width, reading("itemmodifiers.menu.item.clean"));
    }

    private int addStored(StudioStack stack, int x, int width, ListTag stored, boolean effects) {
        int shown = 0;
        for (int index = 0; index < stored.size(); index++) {
            String[] parts = stored.getString(index).split("\\|");
            if (parts.length < 3) continue;
            String id = parts[1];
            Component label = effects ? Names.effect(id) : attributeLabel(parts, id);
            Component value = effects
                    ? effectValue(parseWhole(parts[2]), parts.length > 3 && "DEBUFF".equals(parts[3].trim()))
                    : attributeValue(parseAmount(parts[2]), parts.length > 3 ? parseWhole(parts[3]) : ADDITION);
            add(stack, x, width, entryRow("kind:" + screen.chosen() + ":" + stored.getString(index), label, value,
                    base() + " remove " + (effects ? "potion " : "attribute ") + id));
            shown++;
        }
        return shown;
    }

    private MenuRow entryRow(String key, Component label, Component value, String removal) {
        return row(key, () -> new ActionRow(0, 0, 10, ROW, label, () -> value, () -> screen.now(removal)).alerting()
                .hint("studio.modifiers.entry.hint"));
    }

    private static Component attributeLabel(String[] parts, String id) {
        if (parts.length < 5) return Names.attribute(id);
        return Component.translatable("itemmodifiers.menu.item.slotted", Names.attribute(id), Names.slot(parts[4].trim()));
    }

    // WHY: одно и то же число прибавляется, множит базу или множит итог, и по самому числу это
    // WHY: не читается: строка называет способ, а не только величину
    private static Component attributeValue(double value, int kind) {
        int clamped = Math.max(0, Math.min(kind, OPERATIONS.length - 1));
        return Component.translatable("itemmodifiers.menu.item.op." + OPERATIONS[clamped],
                AmountText.signed(value, clamped != ADDITION));
    }

    private static Component effectValue(int level, boolean debuff) {
        return Component.translatable("itemmodifiers.menu.item.level", level,
                Component.translatable(debuff ? "itemmodifiers.menu.kind.debuff" : "itemmodifiers.menu.kind.buff"));
    }

    private static double parseAmount(String stored) {
        try {
            return Double.parseDouble(stored.trim());
        } catch (NumberFormatException unreadable) {
            return 0.0;
        }
    }

    private static int parseWhole(String stored) {
        try {
            return Integer.parseInt(stored.trim());
        } catch (NumberFormatException unreadable) {
            return 0;
        }
    }

    private void buildEffectForm(StudioStack stack, int x, int width) {
        heading(stack, x, width, "studio.modifiers.group.effect");
        add(stack, x, width, row("effect", () -> new PickRow(0, 0, 10, ROW, Component.translatable("itemmodifiers.menu.effect"),
                effectNames, () -> effect, picked -> effect = picked)));
        add(stack, x, width, row("level", () -> new NumberRow(0, 0, 10, ROW, Component.translatable("itemmodifiers.menu.level"),
                () -> level, value -> level = value, 0, MAX_LEVEL, 1)));
        add(stack, x, width, row("kind", () -> new PickRow(0, 0, 10, ROW, Component.translatable("itemmodifiers.menu.kind"),
                List.of(Component.translatable("itemmodifiers.menu.kind.buff"),
                        Component.translatable("itemmodifiers.menu.kind.debuff")), () -> kind, picked -> kind = picked)));
        add(stack, x, width, row("addeffect", () -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("itemmodifiers.menu.add_effect"),
                () -> Component.translatable("itemmodifiers.menu.action.add"), this::addEffect)));
    }

    private void addEffect() {
        if (effectIds.isEmpty()) return;
        screen.now(base() + " addpotion " + effectIds.get(effect) + " " + level + " " + (kind == 1 ? "DEBUFF" : "BUFF"));
    }

    // WHY: строка одна на все три операции и всегда в единицах атрибута; итог справа называет, что
    // WHY: уйдёт предмету, потому что у умножающих операций то же число даёт другой результат
    private void buildAttributeForm(StudioStack stack, int x, int width) {
        heading(stack, x, width, "studio.modifiers.group.attribute");
        add(stack, x, width, row("attribute", () -> new PickRow(0, 0, 10, ROW,
                Component.translatable("itemmodifiers.menu.attribute"), attributeNames, () -> attribute,
                picked -> attribute = picked)));
        add(stack, x, width, row("amount", () -> new NumberRow(0, 0, 10, ROW, Component.translatable("itemmodifiers.menu.amount"),
                () -> amount, value -> amount = value, -MAX_AMOUNT, MAX_AMOUNT, AMOUNT_STEP)
                .scaledBy(AMOUNT_SCALE).trimmed().hint("itemmodifiers.menu.amount.hint")));
        add(stack, x, width, row("operation", () -> new PickRow(0, 0, 10, ROW,
                Component.translatable("itemmodifiers.menu.operation"), operationOptions(), () -> operation,
                picked -> operation = picked)));
        add(stack, x, width, row("result", () -> reading("itemmodifiers.menu.result",
                () -> attributeValue(amount / (double) AMOUNT_SCALE, operation))));
        add(stack, x, width, row("slot", () -> new PickRow(0, 0, 10, ROW, Component.translatable("itemmodifiers.menu.slot"),
                slotOptions(), () -> slot, picked -> slot = picked)));
        add(stack, x, width, row("addattribute", () -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("itemmodifiers.menu.add_attribute"),
                () -> Component.translatable("itemmodifiers.menu.action.add"), this::addAttribute)));
    }

    private static List<Component> operationOptions() {
        List<Component> options = new ArrayList<>();
        for (String name : OPERATIONS) {
            options.add(Component.translatable("itemmodifiers.menu.operation." + name));
        }
        return options;
    }

    private static List<Component> slotOptions() {
        List<Component> options = new ArrayList<>();
        for (String name : SLOTS) {
            options.add(Component.translatable("itemmodifiers.menu.slot." + name));
        }
        return options;
    }

    private void addAttribute() {
        if (attributeIds.isEmpty()) return;
        String value = BigDecimal.valueOf(amount, DECIMALS).stripTrailingZeros().toPlainString();
        screen.now(base() + " addattribute " + attributeIds.get(attribute) + " " + value + " " + operation + " "
                + SLOTS[slot]);
    }

    private void buildLore(StudioStack stack, int x, int width) {
        heading(stack, x, width, "itemmodifiers.menu.tab.lore");
        add(stack, x, width, loreRow);
        add(stack, x, width, row("loreadd", () -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("itemmodifiers.menu.lore.add"),
                () -> Component.translatable("itemmodifiers.menu.action.add"), this::addLore)));
        add(stack, x, width, row("loreclear", () -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("itemmodifiers.menu.lore.clear"),
                () -> Component.translatable("itemmodifiers.menu.action.clear"), () -> screen.now("ie lore clear"))
                .alerting()));
    }

    boolean submit() {
        if (!loreRow.capturing()) return false;
        addLore();
        return true;
    }

    private void addLore() {
        String text = loreRow.value().trim();
        if (text.isEmpty()) {
            MenuFeedback.show(Component.translatable("itemmodifiers.menu.lore.error_empty"), true);
            return;
        }
        screen.now("ie lore add " + text);
        loreRow.box().setValue("");
    }

    private void buildCommon(StudioStack stack, int x, int width) {
        heading(stack, x, width, "itemmodifiers.menu.tab.main");
        add(stack, x, width, row("enabled", () -> new ToggleRow(0, 0, 10, ROW,
                Component.translatable("itemmodifiers.menu.enabled"),
                () -> ModifierStudioScreen.state().getBoolean("modEnabled"), value -> screen.now("ie toggle"))
                .hint("itemmodifiers.menu.enabled.hint")));
        add(stack, x, width, row("warmup", () -> number("itemmodifiers.menu.warmup", "buffWarmup")));
        add(stack, x, width, row("linger", () -> number("itemmodifiers.menu.linger", "debuffLinger")));
        add(stack, x, width, row("potions", () -> number("itemmodifiers.menu.potions", "potions")));
        add(stack, x, width, row("attributes", () -> number("itemmodifiers.menu.attributes", "attributes")));
        add(stack, x, width, row("info", () -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("itemmodifiers.menu.hand_info"),
                () -> Component.translatable("itemmodifiers.menu.action.show"), () -> screen.now(HAND_COMMAND + " info"))));
    }

    private static MenuRow number(String label, String field) {
        ActionRow row = new ActionRow(0, 0, 10, ROW, Component.translatable(label),
                () -> Component.literal(String.valueOf(ModifierStudioScreen.state().getInt(field))), () -> { });
        row.hint(label + ".hint");
        row.active = false;
        return row;
    }

    private static MenuRow reading(String label) {
        return reading(label, Component::empty);
    }

    private static MenuRow reading(String label, Supplier<Component> value) {
        ActionRow row = new ActionRow(0, 0, 10, ROW, Component.translatable(label), value, () -> { });
        row.active = false;
        return row;
    }
}
