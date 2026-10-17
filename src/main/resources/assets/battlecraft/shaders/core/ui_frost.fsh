#version 150

uniform sampler2D Sampler0;
uniform vec2 FrostSource;
uniform float FrostPass;
uniform vec2 FrostStep;
uniform vec2 FrostSpread;

out vec4 fragColor;

// WHY: тот же порог в один квант источника, что coverOf в ui_assemble.fsh: стекло с альфой 0.06
// WHY: пишет в стадию её квадрат, и покрытие обязано считаться от корня, иначе оно пропадёт
const float COVER_STEPS = 255.0;
const int MAX_REACH = 16;
const float BINOMIAL[4] = float[4](1.0, 3.0, 3.0, 1.0);
const float BINOMIAL_TOTAL = 64.0;
const float QUARTER = 0.25;
const float HALF_PIXEL = 0.5;
const int PAIR_FIRST[4] = int[4](0, 1, 1, 2);
const float PAIR_FIRST_SHARE[4] = float[4](0.25, 0.75, 0.25, 0.75);

// WHY: прежний диск читал стадию билинейно и уже потом считал покрытие, поэтому кромка панели
// WHY: набирала покрытие от любого задетого текселя. Здесь отсчёт собирается так же, только из
// WHY: заранее прочитанных текселей: точка вне кадра пуста, как пустой отсчёт диска за краем экрана,
// WHY: тексель за краем повторяет крайний, как CLAMP_TO_EDGE стадии, а цвет умножается на покрытие,
// WHY: чтобы дальше всё было линейным и сжималось и размывалось раздельными проходами
vec4 stageTexel(ivec2 texel) {
    ivec2 last = ivec2(FrostSource) - 1;
    return texelFetch(Sampler0, clamp(texel, ivec2(0), last), 0);
}

vec4 decoded(vec4 found) {
    float cover = min(1.0, sqrt(found.a) * COVER_STEPS);
    return vec4(found.rgb * cover, cover);
}

// WHY: покрытие это порог, и четыре отсчёта на тексель ловили ступень кромки с точностью до фазы:
// WHY: на первых кадрах ухода кромка панели вспыхивала до 113 уровней против плотного диска. Внутри
// WHY: четверти пикселя набор задетых текселей один, покрытие постоянно, а среднее билинейного цвета
// WHY: равно его значению в центре четверти, поэтому шестнадцать отсчётов в центрах четвертей дают
// WHY: точный интеграл по квадрату два на два пикселя, то есть ящик с дисперсией 1/3 пикселя.
// WHY: Отсчёт в центре четверти берёт пару текселей с весами 1/4 и 3/4, и все шестнадцать пар лежат
// WHY: в квадрате четыре на четыре: он читается один раз точечно, билинейные смеси считаются здесь
vec4 shrinkStage(ivec2 target) {
    ivec2 corner = target * 2 - 1;
    vec4 texels[16];
    for (int row = 0; row < 4; row++) {
        for (int column = 0; column < 4; column++) {
            texels[row * 4 + column] = stageTexel(corner + ivec2(column, row));
        }
    }
    vec2 start = vec2(target) * 2.0 + QUARTER;
    vec4 sum = vec4(0.0);
    for (int row = 0; row < 4; row++) {
        if (start.y + float(row) * HALF_PIXEL > FrostSource.y) continue;
        int firstRow = PAIR_FIRST[row] * 4;
        float firstRowShare = PAIR_FIRST_SHARE[row];
        for (int column = 0; column < 4; column++) {
            if (start.x + float(column) * HALF_PIXEL > FrostSource.x) continue;
            int first = firstRow + PAIR_FIRST[column];
            float firstShare = PAIR_FIRST_SHARE[column];
            vec4 nearRow = mix(texels[first + 1], texels[first], firstShare);
            vec4 farRow = mix(texels[first + 5], texels[first + 4], firstShare);
            sum += decoded(mix(farRow, nearRow, firstRowShare));
        }
    }
    return sum / 16.0;
}

// WHY: сигма около пикселя на половине кадра ложилась на сетку два на два пикселя: два штриха буквы
// WHY: через пиксель сливались в плашку или нет в зависимости от того, где легла строка. Здесь тот же
// WHY: точный интеграл, только по самому пикселю: четыре четверти, пары текселей из квадрата три на три
vec4 shrinkPixel(ivec2 target) {
    ivec2 corner = target - 1;
    vec4 texels[9];
    for (int row = 0; row < 3; row++) {
        for (int column = 0; column < 3; column++) {
            texels[row * 3 + column] = stageTexel(corner + ivec2(column, row));
        }
    }
    vec4 sum = vec4(0.0);
    for (int row = 0; row < 2; row++) {
        int firstRow = PAIR_FIRST[row] * 3;
        float firstRowShare = PAIR_FIRST_SHARE[row];
        for (int column = 0; column < 2; column++) {
            int first = firstRow + PAIR_FIRST[column];
            float firstShare = PAIR_FIRST_SHARE[column];
            vec4 nearRow = mix(texels[first + 1], texels[first], firstShare);
            vec4 farRow = mix(texels[first + 4], texels[first + 3], firstShare);
            sum += decoded(mix(farRow, nearRow, firstRowShare));
        }
    }
    return sum * QUARTER;
}

// WHY: уровни лежат в общих целях больше себя, поэтому всё за границей уровня читается нулём явно:
// WHY: там остаток прошлого прохода, а не пустота за краем экрана
vec4 linear(ivec2 texel) {
    if (texel.x < 0 || texel.y < 0 || texel.x >= int(FrostSource.x) || texel.y >= int(FrostSource.y)) {
        return vec4(0.0);
    }
    return texelFetch(Sampler0, texel, 0);
}

vec4 shrinkLevel(ivec2 target) {
    ivec2 corner = target * 2 - 1;
    vec4 sum = vec4(0.0);
    for (int row = 0; row < 4; row++) {
        for (int column = 0; column < 4; column++) {
            sum += linear(corner + ivec2(column, row)) * (BINOMIAL[column] * BINOMIAL[row]);
        }
    }
    return sum / BINOMIAL_TOTAL;
}

// WHY: гаусс по одной оси с весами в шейдере; сумма весов включает отсчёты за краем, иначе панель
// WHY: у кромки экрана не тускнела бы так же, как при прежнем диске
vec4 line(ivec2 target) {
    ivec2 step = ivec2(FrostStep);
    int reach = min(int(FrostSpread.y), MAX_REACH);
    float fall = -0.5 / max(1.0e-4, FrostSpread.x * FrostSpread.x);
    vec4 sum = vec4(0.0);
    float weight = 0.0;
    for (int tap = -reach; tap <= reach; tap++) {
        float share = exp(fall * float(tap * tap));
        sum += linear(target + step * tap) * share;
        weight += share;
    }
    return sum / weight;
}

void main() {
    if (FrostPass < 0.5) {
        fragColor = shrinkStage(ivec2(gl_FragCoord.xy));
        return;
    }
    ivec2 target = ivec2(gl_FragCoord.xy);
    if (FrostPass > 2.5) {
        fragColor = shrinkPixel(target);
        return;
    }
    fragColor = FrostPass < 1.5 ? shrinkLevel(target) : line(target);
}
