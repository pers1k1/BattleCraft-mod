package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.FieldRow;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.PanelScreen;
import com.persiki84.shared.AmountText;
import com.persiki84.shared.Names;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ModifierScreen extends PanelScreen {
    private static final String COMMAND = "ie hand";
    private static final int MAX_LEVEL = 255;
    private static final int AMOUNT_SCALE = 100_000;
    private static final int DECIMALS = String.valueOf(AMOUNT_SCALE).length() - 1;
    private static final int AMOUNT_STEP = 1_000;
    private static final int MAX_AMOUNT = 1_000 * AMOUNT_SCALE;
    private static final int HAND_TARGET = 0;
    private static final int PICKED_TARGET = 2;
    private static final int LORE_LENGTH = 128;
    private static final String[] SLOTS = {"mainhand", "offhand", "head", "chest", "legs", "feet", "any"};

    private final List<ResourceLocation> effectIds = new ArrayList<>();
    private final List<Component> effectNames = new ArrayList<>();
    private final List<ResourceLocation> attributeIds = new ArrayList<>();
    private final List<Component> attributeNames = new ArrayList<>();

    private FieldRow loreRow;
    private String picked;
    private int effect;
    private int level;
    private int kind;
    private int attribute;
    private int amount = AMOUNT_SCALE;
    private int operation;
    private int slot;
    private int target;

    public ModifierScreen() {
        super(Component.translatable("itemmodifiers.menu.title"));
        collectEffects();
        collectAttributes();
    }

    private void collectEffects() {
        for (ResourceLocation id : BuiltInRegistries.MOB_EFFECT.keySet()) {
            MobEffect value = BuiltInRegistries.MOB_EFFECT.get(id);
            if (value == null) continue;

            effectIds.add(id);
            effectNames.add(value.getDisplayName());
        }
    }

    private void collectAttributes() {
        for (ResourceLocation id : BuiltInRegistries.ATTRIBUTE.keySet()) {
            Attribute value = BuiltInRegistries.ATTRIBUTE.get(id);
            if (value == null) continue;

            attributeIds.add(id);
            attributeNames.add(Component.translatable(value.getDescriptionId()));
        }
    }

    @Override
    protected String menuId() {
        return ModuleMenuStates.MODIFIERS;
    }

    @Override
    protected List<Page> pages() {
        return List.of(
                new Page(Component.translatable("itemmodifiers.menu.tab.main"), this::mainRows),
                new Page(Component.translatable("itemmodifiers.menu.tab.items"), this::itemRows),
                new Page(Component.translatable("itemmodifiers.menu.tab.item"), this::pickedRows),
                new Page(Component.translatable("itemmodifiers.menu.tab.effect"), this::effectRows),
                new Page(Component.translatable("itemmodifiers.menu.tab.attribute"), this::attributeRows),
                new Page(Component.translatable("itemmodifiers.menu.tab.lore"), this::loreRows));
    }

    private List<AbstractWidget> loreRows() {
        return List.of(
                reading("itemmodifiers.menu.lore.held", ModifierScreen::heldLabel),
                loreField(),
                action("itemmodifiers.menu.lore.add", "itemmodifiers.menu.action.add", this::addLore),
                clearLoreRow());
    }

    // WHY: строка поля переживает пересборку: новая на каждый кадр стирала бы набранный текст
    private FieldRow loreField() {
        if (loreRow == null) {
            loreRow = new FieldRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                    Component.translatable("itemmodifiers.menu.lore.text"),
                    Component.translatable("itemmodifiers.menu.lore.placeholder"),
                    "", LORE_LENGTH, value -> { });
            loreRow.hint("itemmodifiers.menu.lore.text" + HINT_SUFFIX);
        }
        loreRow.setX(rowsLeft());
        loreRow.setWidth(rowsWidth());
        return loreRow;
    }

    private void addLore() {
        String text = loreField().value().trim();
        if (text.isEmpty()) {
            MenuFeedback.show(Component.translatable("itemmodifiers.menu.lore.error_empty"), true);
            return;
        }

        send("ie lore add " + text);
        loreField().box().setValue("");
    }

    private ActionRow clearLoreRow() {
        return new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("itemmodifiers.menu.lore.clear"),
                () -> Component.translatable("itemmodifiers.menu.action.clear"),
                () -> send("ie lore clear")).alerting();
    }

    private static Component heldLabel() {
        String id = heldItem();
        return id.isEmpty() ? Component.translatable("itemmodifiers.menu.lore.empty_hand") : Names.item(id);
    }

    private static List<CompoundTag> tracked() {
        ListTag stored = MenuData.state(ModuleMenuStates.MODIFIERS)
                .getList(ModuleMenuStates.TRACKED_ITEMS, Tag.TAG_COMPOUND);
        List<CompoundTag> items = new ArrayList<>();
        for (int index = 0; index < stored.size(); index++) {
            items.add(stored.getCompound(index));
        }
        return items;
    }

    private static CompoundTag trackedItem(String id) {
        for (CompoundTag item : tracked()) {
            if (item.getString("item").equals(id)) return item;
        }
        return new CompoundTag();
    }

    private List<AbstractWidget> itemRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        for (CompoundTag item : tracked()) {
            String id = item.getString("item");
            int count = item.getList("effects", Tag.TAG_STRING).size()
                    + item.getList("attributes", Tag.TAG_STRING).size();
            rows.add(new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Names.item(id),
                    () -> Component.translatable("itemmodifiers.menu.item.count", count),
                    () -> pickItem(id)));
        }
        if (rows.isEmpty()) rows.add(reading("itemmodifiers.menu.item.empty", Component::empty));
        return rows;
    }

    // WHY: выбор в списке молча ничего не менял, а добавление уходило в руку: теперь выбор сразу
    // WHY: переводит цель на этот предмет, иначе список вводит в заблуждение
    private void pickItem(String id) {
        picked = id;
        target = PICKED_TARGET;
        rebuild();
    }

    private List<AbstractWidget> pickedRows() {
        if (picked == null) return List.of(reading("itemmodifiers.menu.item.none", Component::empty));

        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(reading("itemmodifiers.menu.item.picked", () -> Names.item(picked)));
        CompoundTag item = trackedItem(picked);
        addEntryRows(rows, item.getList("effects", Tag.TAG_STRING), true);
        addEntryRows(rows, item.getList("attributes", Tag.TAG_STRING), false);

        if (rows.size() == 1) rows.add(reading("itemmodifiers.menu.item.clean", Component::empty));
        rows.add(new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("itemmodifiers.menu.item.clear"),
                () -> Component.translatable("itemmodifiers.menu.action.clear"),
                () -> send("ie item " + picked + " clear")).alerting());
        return rows;
    }

    private void addEntryRows(List<AbstractWidget> rows, ListTag stored, boolean effects) {
        for (int index = 0; index < stored.size(); index++) {
            String[] parts = stored.getString(index).split("\\|");
            if (parts.length < 3) continue;

            String id = parts[1];
            Component label = effects ? Names.effect(id) : Names.attribute(id);
            Component value = effects
                    ? Component.translatable("itemmodifiers.menu.item.level", parts[2])
                    : Component.translatable("itemmodifiers.menu.item.value", amountLabel(parts));
            rows.add(new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, label, () -> value,
                    () -> dropEntry(effects, id)).alerting());
        }
    }

    private static String amountLabel(String[] parts) {
        double value = parseAmount(parts[2]);
        boolean percent = parts.length > 3 && !"0".equals(parts[3].trim());
        return AmountText.signed(value, percent);
    }

    private static double parseAmount(String stored) {
        try {
            return Double.parseDouble(stored.trim());
        } catch (NumberFormatException unreadable) {
            return 0.0;
        }
    }

    private void dropEntry(boolean effect, String id) {
        send("ie item " + picked + " remove " + (effect ? "potion " : "attribute ") + id);
    }

    private List<AbstractWidget> mainRows() {
        return List.of(
                toggle("itemmodifiers.menu.enabled", () -> MenuData.state(menuId()).getBoolean("modEnabled"),
                        value -> send("ie toggle")),
                reading("itemmodifiers.menu.warmup",
                        () -> Component.literal(String.valueOf(MenuData.state(menuId()).getInt("buffWarmup")))),
                reading("itemmodifiers.menu.linger",
                        () -> Component.literal(String.valueOf(MenuData.state(menuId()).getInt("debuffLinger")))),
                reading("itemmodifiers.menu.potions",
                        () -> Component.literal(String.valueOf(MenuData.state(menuId()).getInt("potions")))),
                reading("itemmodifiers.menu.attributes",
                        () -> Component.literal(String.valueOf(MenuData.state(menuId()).getInt("attributes")))),
                action("itemmodifiers.menu.hand_info", "itemmodifiers.menu.action.show",
                        () -> send(COMMAND + " info")),
                clearRow());
    }

    private ActionRow clearRow() {
        return new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("itemmodifiers.menu.hand_clear"),
                () -> Component.translatable("itemmodifiers.menu.action.clear"),
                () -> send(COMMAND + " clear")).alerting();
    }

    // WHY: рука пишет модификатор в NBT одного стака, а вид предмета в конфиг: из меню правилась
    // WHY: только рука, и купленный в магазине такой же предмет приходил без модификаторов
    private AbstractWidget targetRow() {
        return pick("itemmodifiers.menu.target", targetOptions(), this::targetIndex, chosen -> {
            target = chosen;
            rebuild();
        });
    }

    private List<Component> targetOptions() {
        List<Component> options = new ArrayList<>();
        options.add(Component.translatable("itemmodifiers.menu.target.hand"));
        options.add(Component.translatable("itemmodifiers.menu.target.kind"));
        if (picked != null) {
            options.add(Component.translatable("itemmodifiers.menu.target.picked", Names.item(picked)));
        }
        return options;
    }

    private int targetIndex() {
        return Math.min(target, targetOptions().size() - 1);
    }

    private String base() {
        if (targetIndex() == PICKED_TARGET) return "ie item " + picked;
        return targetIndex() == HAND_TARGET ? COMMAND : "ie";
    }

    private List<AbstractWidget> effectRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(targetRow());
        rows.add(pick("itemmodifiers.menu.effect", effectNames, () -> effect, picked -> effect = picked));
        rows.add(number("itemmodifiers.menu.level", () -> level, value -> level = value, 0, MAX_LEVEL, 1));
        rows.add(pick("itemmodifiers.menu.kind", kindOptions(), () -> kind, picked -> kind = picked));
        rows.add(action("itemmodifiers.menu.add_effect", "itemmodifiers.menu.action.add", this::addEffect));
        rows.add(new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("itemmodifiers.menu.remove_effect"),
                () -> Component.translatable("itemmodifiers.menu.action.remove"), this::removeEffect).alerting());
        return rows;
    }

    private static List<Component> kindOptions() {
        return List.of(Component.translatable("itemmodifiers.menu.kind.buff"),
                Component.translatable("itemmodifiers.menu.kind.debuff"));
    }

    private void addEffect() {
        if (effectIds.isEmpty()) return;
        send(base() + " addpotion " + effectIds.get(effect) + " " + level + " " + (kind == 1 ? "DEBUFF" : "BUFF"));
    }

    private void removeEffect() {
        if (effectIds.isEmpty()) return;
        send(base() + " remove potion " + effectIds.get(effect));
    }

    // WHY: строка одна на все три операции и всегда в единицах атрибута: переключение единицы
    // WHY: вместе с операцией молча меняло смысл уже набранного числа
    private AbstractWidget amountRow() {
        return number("itemmodifiers.menu.amount", () -> amount, value -> amount = value,
                -MAX_AMOUNT, MAX_AMOUNT, AMOUNT_STEP).scaledBy(AMOUNT_SCALE).trimmed();
    }

    private List<AbstractWidget> attributeRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(targetRow());
        rows.add(pick("itemmodifiers.menu.attribute", attributeNames, () -> attribute, picked -> attribute = picked));
        rows.add(amountRow());
        rows.add(pick("itemmodifiers.menu.operation", operationOptions(), () -> operation,
                picked -> operation = picked));
        rows.add(pick("itemmodifiers.menu.slot", slotOptions(), () -> slot, picked -> slot = picked));
        rows.add(action("itemmodifiers.menu.add_attribute", "itemmodifiers.menu.action.add", this::addAttribute));
        rows.add(new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("itemmodifiers.menu.remove_attribute"),
                () -> Component.translatable("itemmodifiers.menu.action.remove"), this::removeAttribute).alerting());
        return rows;
    }

    private static List<Component> operationOptions() {
        return List.of(Component.translatable("itemmodifiers.menu.operation.add"),
                Component.translatable("itemmodifiers.menu.operation.multiply_base"),
                Component.translatable("itemmodifiers.menu.operation.multiply_total"));
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
        send(base() + " addattribute " + attributeIds.get(attribute) + " " + amountText() + " "
                + operation + " " + SLOTS[slot]);
    }

    private String amountText() {
        return BigDecimal.valueOf(amount, DECIMALS).stripTrailingZeros().toPlainString();
    }

    // WHY: конфиг держит модификатор одной записью на предмет и атрибут, поэтому у команды вида
    // WHY: предмета слота в снятии нет, а у руки он обязателен
    private void removeAttribute() {
        if (attributeIds.isEmpty()) return;
        if (targetIndex() == HAND_TARGET) {
            send(COMMAND + " remove attribute " + attributeIds.get(attribute) + " " + SLOTS[slot]);
            return;
        }
        send(base() + " remove attribute " + attributeIds.get(attribute));
    }
}
