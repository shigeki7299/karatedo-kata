package jp.infinity.krate.katachecker.kata

enum class KataCategory(val id: String, val label: String) {
    KIHON("kihon", "基本形"),
    SHITEI("shitei", "指定形"),
    TOKUI("tokui", "得意形"),
}

enum class KataStyle(val id: String, val label: String) {
    GOJU("goju", "剛柔"),
    SHITO("shito", "糸東"),
    SHOTOKAN("shotokan", "松濤館"),
    WADO("wado", "和道"),
    RYUEI("ryuei", "劉衛"),
}

data class KataEntry(
    val id: String,
    val name: String,
    val style: KataStyle,
    val category: KataCategory,
)

object KataCatalog {
    val all: List<KataEntry> = buildList {
        kihon(KataStyle.GOJU, "ゲキサイ第一", "ゲキサイ第二")
        kihon(KataStyle.SHOTOKAN, "平安初段", "平安二段", "平安三段", "平安四段", "平安五段")
        kihon(KataStyle.SHITO, "平安初段", "平安二段", "平安三段", "平安四段", "平安五段")
        kihon(KataStyle.WADO, "ピンアン初段", "ピンアン二段", "ピンアン三段", "ピンアン四段", "ピンアン五段")

        shitei(KataStyle.GOJU, "セーパイ", "サイファ", "セーサン", "クルルンファ")
        shitei(KataStyle.SHOTOKAN, "ジオン", "カンクウダイ", "エンピ", "カンクウショウ")
        shitei(KataStyle.SHITO, "バッサイダイ", "セイエンチン", "マツムラローハイ", "ニーパイポ")
        shitei(KataStyle.WADO, "セイシャン", "チントウ", "クーシャンクー", "ニーセーシー")

        tokui(
            KataStyle.GOJU,
            "サンチン", "セイユンチン", "シソーチン", "サンセール",
            "スーパーリンペイ", "テンショー",
        )
        tokui(
            KataStyle.SHOTOKAN,
            "抜塞大", "抜塞小", "観空大", "観空小", "鉄騎初段", "鉄騎二段", "鉄騎三段",
            "半月", "十手", "燕飛", "岩鶴", "慈恩", "壮鎮", "二十四歩",
            "五十四歩大", "五十四歩小", "珍手", "雲手", "明鏡", "王冠", "ジイン",
        )
        tokui(
            KataStyle.WADO,
            "ナイハンチ", "バッサイ", "ローハイ", "ワンシュー", "ジオン", "ジッテ",
        )
        tokui(
            KataStyle.SHITO,
            "ジッテ", "ジオン", "ジイン", "松風", "ワンシュー", "ローハイ",
            "バッサイ大", "バッサイ小", "泊バッサイ", "松村バッサイ",
            "公相君大", "公相君小", "四方公相君", "チントー", "チンテー",
            "ソーチン", "ニーセイシ", "五十四歩", "ウンシュー", "セイサン",
            "ナイファンチン初段", "ナイファンチン二段", "ナイファンチン三段",
            "アオヤギ", "十六", "サンチン", "テンショー", "セイパイ", "サンセール",
            "サイファ", "シソーチン", "クルルンファ", "スーパーリンペイ", "ハッファー",
            "パーチュー", "ヘイクー", "パイクー", "アーナン", "アーナンコー",
            "パープーレン", "チャタンヤラ・クーサンクー", "松村ローハイ",
        )
        tokui(
            KataStyle.RYUEI,
            "ニセーシー", "サンセール", "セーサン", "パーチュー", "ヘイクー", "パイクー", "アーナン",
        )
    }

    fun filter(category: KataCategory, style: KataStyle): List<KataEntry> {
        return all.filter { it.category == category && it.style == style }
    }

    private fun MutableList<KataEntry>.kihon(style: KataStyle, vararg names: String) {
        addAll(style, KataCategory.KIHON, names)
    }

    private fun MutableList<KataEntry>.shitei(style: KataStyle, vararg names: String) {
        addAll(style, KataCategory.SHITEI, names)
    }

    private fun MutableList<KataEntry>.tokui(style: KataStyle, vararg names: String) {
        addAll(style, KataCategory.TOKUI, names)
    }

    private fun MutableList<KataEntry>.addAll(
        style: KataStyle,
        category: KataCategory,
        names: Array<out String>,
    ) {
        names.forEachIndexed { index, name ->
            add(
                KataEntry(
                    id = "${style.id}-${category.id}-$index",
                    name = name,
                    style = style,
                    category = category,
                ),
            )
        }
    }
}
