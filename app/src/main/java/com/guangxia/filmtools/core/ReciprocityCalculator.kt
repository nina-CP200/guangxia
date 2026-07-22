package com.guangxia.filmtools.core

import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.pow

enum class FilmCategory(val label: String) {
    BLACK_AND_WHITE("黑白负片"),
    COLOR_NEGATIVE("彩色负片"),
    COLOR_REVERSAL("彩色反转片"),
}

enum class ReciprocitySource(val label: String) {
    OFFICIAL("官方资料"),
    COMMUNITY_ESTIMATE("玩家估算"),
    REFERENCE_ONLY("资料提示"),
    CUSTOM("自定义参数"),
}

data class ReciprocityPoint(
    val meteredSeconds: Double,
    val correctedSeconds: Double,
    val filter: String? = null,
    val exposureStops: Double? = null,
)

sealed interface ReciprocityCurve {
    data class Table(
        val points: List<ReciprocityPoint>,
        val onsetSeconds: Double = 1.0,
        val maximumSeconds: Double? = null,
    ) : ReciprocityCurve {
        init {
            require(points.isNotEmpty()) { "倒易律表至少需要一个数据点" }
        }
    }

    data class PowerLaw(
        val exponent: Double,
        val onsetSeconds: Double = 1.0,
    ) : ReciprocityCurve

    data object ReferenceOnly : ReciprocityCurve
}

data class FilmReciprocityProfile(
    val id: String,
    val name: String,
    val manufacturer: String,
    val iso: Int?,
    val category: FilmCategory,
    val source: ReciprocitySource,
    val sourceNote: String,
    val curve: ReciprocityCurve,
    val colorNote: String? = null,
)

data class ReciprocityResult(
    val meteredSeconds: Double,
    val correctedSeconds: Double?,
    val exposureStops: Double?,
    val filter: String?,
    val source: ReciprocitySource,
    val warning: String? = null,
) {
    val isEstimate get() = source == ReciprocitySource.COMMUNITY_ESTIMATE || source == ReciprocitySource.CUSTOM
}

object ReciprocityCalculator {
    val profiles: List<FilmReciprocityProfile> = listOf(
        profile(
            id = "kodak-trix-320",
            name = "Tri-X 320",
            manufacturer = "Kodak",
            iso = 320,
            category = FilmCategory.BLACK_AND_WHITE,
            source = ReciprocitySource.OFFICIAL,
            sourceNote = "Kodak F-4017 长曝光表",
            curve = table(1.0 to 2.0, 10.0 to 50.0, 100.0 to 1200.0),
        ),
        profile(
            id = "kodak-trix-400",
            name = "Tri-X 400",
            manufacturer = "Kodak",
            iso = 400,
            category = FilmCategory.BLACK_AND_WHITE,
            source = ReciprocitySource.OFFICIAL,
            sourceNote = "Kodak F-4017 长曝光表",
            curve = table(1.0 to 2.0, 10.0 to 50.0, 100.0 to 1200.0),
        ),
        profile(
            id = "kodak-tmax-100",
            name = "T-MAX 100",
            manufacturer = "Kodak",
            iso = 100,
            category = FilmCategory.BLACK_AND_WHITE,
            source = ReciprocitySource.OFFICIAL,
            sourceNote = "Kodak F-4016 长曝光表",
            curve = table(
                ReciprocityPoint(1.0, 2.0.pow(1.0 / 3.0), exposureStops = 1.0 / 3.0),
                ReciprocityPoint(10.0, 15.0, exposureStops = 0.5),
                ReciprocityPoint(100.0, 200.0, exposureStops = 1.0),
            ),
        ),
        profile(
            id = "kodak-tmax-400",
            name = "T-MAX 400",
            manufacturer = "Kodak",
            iso = 400,
            category = FilmCategory.BLACK_AND_WHITE,
            source = ReciprocitySource.OFFICIAL,
            sourceNote = "Kodak F-4043 长曝光表",
            curve = table(
                ReciprocityPoint(1.0, 1.0),
                ReciprocityPoint(10.0, 10.0 * 2.0.pow(1.0 / 3.0), exposureStops = 1.0 / 3.0),
                ReciprocityPoint(100.0, 300.0, exposureStops = 1.5),
            ),
        ),
        profile(
            id = "kodak-portra-400",
            name = "Portra 400",
            manufacturer = "Kodak",
            iso = 400,
            category = FilmCategory.COLOR_NEGATIVE,
            source = ReciprocitySource.COMMUNITY_ESTIMATE,
            sourceNote = "玩家测试表：Portra 100/400 估算",
            curve = table(
                2.0 to 3.0,
                4.0 to 6.0,
                8.0 to 15.0,
                15.0 to 35.0,
                30.0 to 89.0,
                60.0 to 227.0,
            ),
            colorNote = "彩色负片的色偏受乳剂、冲洗和扫描影响，结果仅供试拍。",
        ),
        profile(
            id = "kodak-ektar-100",
            name = "Ektar 100",
            manufacturer = "Kodak",
            iso = 100,
            category = FilmCategory.COLOR_NEGATIVE,
            source = ReciprocitySource.COMMUNITY_ESTIMATE,
            sourceNote = "玩家实测表：Ektar 100 估算",
            curve = table(
                2.0 to 2.0,
                4.0 to 6.0,
                8.0 to 14.0,
                15.0 to 30.0,
                30.0 to 68.0,
                60.0 to 149.0,
            ),
            colorNote = "Kodak 未发布统一的 Ektar 长曝光修正表，建议包围曝光。",
        ),
        profile(
            id = "kodak-portra-160",
            name = "Portra 160",
            manufacturer = "Kodak",
            iso = 160,
            category = FilmCategory.COLOR_NEGATIVE,
            source = ReciprocitySource.COMMUNITY_ESTIMATE,
            sourceNote = "玩家测试：Kodak Portra Lovers 论坛帖，作者称与 Portra 400 共用曲线",
            curve = table(
                2.0 to 3.0,
                4.0 to 6.0,
                8.0 to 15.0,
                15.0 to 35.0,
                30.0 to 89.0,
                60.0 to 227.0,
            ),
            colorNote = "玩家估算且未使用密度计；乳剂批次、冲洗和扫描会影响色偏。",
        ),
        profile(
            id = "kodak-portra-800",
            name = "Portra 800",
            manufacturer = "Kodak",
            iso = 800,
            category = FilmCategory.COLOR_NEGATIVE,
            source = ReciprocitySource.COMMUNITY_ESTIMATE,
            sourceNote = "冲印店博客经验表：Kubus Photo Service",
            curve = ReciprocityCurve.Table(
                points = listOf(
                    ReciprocityPoint(1.0, 1.2),
                    ReciprocityPoint(10.0, 35.0),
                    ReciprocityPoint(30.0, 120.0),
                ),
                maximumSeconds = 30.0,
            ),
            colorNote = "仅覆盖博客给出的 1–30 秒测光范围；超过 30 秒请包围曝光。",
        ),
        profile(
            id = "kodak-gold-200",
            name = "Gold 200",
            manufacturer = "Kodak",
            iso = 200,
            category = FilmCategory.COLOR_NEGATIVE,
            source = ReciprocitySource.COMMUNITY_ESTIMATE,
            sourceNote = "冲印店博客经验表：Kubus Photo Service",
            curve = ReciprocityCurve.Table(
                points = listOf(
                    ReciprocityPoint(1.0, 2.0),
                    ReciprocityPoint(10.0, 60.0),
                ),
                maximumSeconds = 10.0,
            ),
            colorNote = "博客认为 10 秒以上色偏和结果不稳定；超过 10 秒不自动估算。",
        ),
        profile(
            id = "kodak-ultramax-400",
            name = "Ultramax 400",
            manufacturer = "Kodak",
            iso = 400,
            category = FilmCategory.COLOR_NEGATIVE,
            source = ReciprocitySource.COMMUNITY_ESTIMATE,
            sourceNote = "冲印店博客经验表：Kubus Photo Service",
            curve = ReciprocityCurve.Table(
                points = listOf(
                    ReciprocityPoint(1.0, 1.5),
                    ReciprocityPoint(10.0, 45.0),
                ),
                maximumSeconds = 10.0,
            ),
            colorNote = "博客认为 10 秒以上结果不可预测；超过 10 秒不自动估算。",
        ),
        referenceProfile("kodak-colorplus-200", "ColorPlus 200", 200, FilmCategory.COLOR_NEGATIVE),
        profile(
            id = "kodak-e100",
            name = "Ektachrome E100",
            manufacturer = "Kodak",
            iso = 100,
            category = FilmCategory.COLOR_REVERSAL,
            source = ReciprocitySource.OFFICIAL,
            sourceNote = "Kodak Ektachrome E100 Data Sheet",
            curve = ReciprocityCurve.Table(
                points = listOf(ReciprocityPoint(10.0, 10.0)),
                maximumSeconds = 10.0,
            ),
            colorNote = "官方资料仅明确给出 10 秒以内无需补偿；更长时间请查表并包围曝光。",
        ),
        profile(
            id = "fujichrome-velvia-50",
            name = "Velvia 50",
            manufacturer = "Fujifilm US",
            iso = 50,
            category = FilmCategory.COLOR_REVERSAL,
            source = ReciprocitySource.OFFICIAL,
            sourceNote = "Fujifilm Velvia 50 Product Information Bulletin",
            curve = table(
                ReciprocityPoint(1.0, 1.0),
                ReciprocityPoint(4.0, 4.0 * 2.0.pow(1.0 / 3.0), "5M", 1.0 / 3.0),
                ReciprocityPoint(8.0, 8.0 * 2.0.pow(0.5), "7.5M", 0.5),
                ReciprocityPoint(16.0, 16.0 * 2.0.pow(2.0 / 3.0), "10M", 2.0 / 3.0),
                ReciprocityPoint(32.0, 32.0 * 2.0, "12.5M", 1.0),
                ReciprocityPoint(64.0, 64.0, "不建议", null),
            ),
            colorNote = "4 秒以上同时需要曝光和色彩平衡补偿。",
        ),
        profile(
            id = "fujichrome-provia-100f",
            name = "Provia 100F",
            manufacturer = "Fujifilm US",
            iso = 100,
            category = FilmCategory.COLOR_REVERSAL,
            source = ReciprocitySource.OFFICIAL,
            sourceNote = "Fujifilm Provia 100F Data Sheet",
            curve = table(
                ReciprocityPoint(128.0, 128.0),
                ReciprocityPoint(240.0, 240.0 * 2.0.pow(1.0 / 3.0), "2.5G", 1.0 / 3.0),
                ReciprocityPoint(480.0, 480.0, "不建议", null),
            ),
            colorNote = "128 秒以内无需补偿；4 分钟建议使用 2.5G。",
        ),
        profile(
            id = "fujichrome-velvia-100",
            name = "Velvia 100",
            manufacturer = "Fujifilm US",
            iso = 100,
            category = FilmCategory.COLOR_REVERSAL,
            source = ReciprocitySource.OFFICIAL,
            sourceNote = "Fujifilm US Professional Data Guide",
            curve = table(
                ReciprocityPoint(60.0, 60.0),
                ReciprocityPoint(120.0, 120.0 * 2.0.pow(1.0 / 3.0), "5B", 1.0 / 3.0),
                ReciprocityPoint(240.0, 240.0 * 2.0.pow(0.5), "5B", 0.5),
                ReciprocityPoint(480.0, 480.0 * 2.0.pow(2.0 / 3.0), "5B", 2.0 / 3.0),
            ),
        ),
        profile(
            id = "fujichrome-velvia-100f",
            name = "Velvia 100F",
            manufacturer = "Fujifilm US",
            iso = 100,
            category = FilmCategory.COLOR_REVERSAL,
            source = ReciprocitySource.OFFICIAL,
            sourceNote = "Fujifilm US Professional Data Guide",
            curve = ReciprocityCurve.Table(
                points = listOf(
                    ReciprocityPoint(32.0, 32.0),
                    ReciprocityPoint(64.0, 64.0 * 2.0.pow(2.0 / 3.0), "5G", 2.0 / 3.0),
                    ReciprocityPoint(240.0, 240.0 * 2.0, "7.5G", 1.0),
                ),
                maximumSeconds = 240.0,
            ),
            colorNote = "官方资料不建议超过 4–8 分钟范围；色彩滤镜需随曝光一起考虑。",
        ),
        profile(
            id = "fujichrome-provia-400f",
            name = "Provia 400F",
            manufacturer = "Fujifilm US",
            iso = 400,
            category = FilmCategory.COLOR_REVERSAL,
            source = ReciprocitySource.OFFICIAL,
            sourceNote = "Fujifilm US Professional Data Guide",
            curve = table(
                ReciprocityPoint(60.0, 60.0),
                ReciprocityPoint(120.0, 120.0 * 2.0.pow(1.0 / 3.0), "5B", 1.0 / 3.0),
                ReciprocityPoint(240.0, 240.0 * 2.0.pow(0.5), "5B", 0.5),
                ReciprocityPoint(480.0, 480.0 * 2.0.pow(2.0 / 3.0), "5B", 2.0 / 3.0),
            ),
        ),
        profile(
            id = "fujichrome-astia-100f",
            name = "Astia 100F",
            manufacturer = "Fujifilm US",
            iso = 100,
            category = FilmCategory.COLOR_REVERSAL,
            source = ReciprocitySource.OFFICIAL,
            sourceNote = "Fujifilm Astia 100F Data Sheet",
            curve = table(
                ReciprocityPoint(60.0, 60.0),
                ReciprocityPoint(120.0, 120.0 * 2.0.pow(1.0 / 3.0), "5B", 1.0 / 3.0),
                ReciprocityPoint(240.0, 240.0 * 2.0.pow(0.5), "5B", 0.5),
                ReciprocityPoint(480.0, 480.0 * 2.0.pow(2.0 / 3.0), "5B", 2.0 / 3.0),
            ),
        ),
        referenceProfile("fujicolor-pro-160s", "Pro 160S", 160, FilmCategory.COLOR_NEGATIVE),
        referenceProfile("fujicolor-pro-160c", "Pro 160C", 160, FilmCategory.COLOR_NEGATIVE),
        profile(
            id = "fujicolor-pro-400h",
            name = "Pro 400H",
            manufacturer = "Fujifilm US",
            iso = 400,
            category = FilmCategory.COLOR_NEGATIVE,
            source = ReciprocitySource.OFFICIAL,
            sourceNote = "Fujifilm US Professional Data Guide / Pro 400H Data Sheet",
            curve = ReciprocityCurve.Table(
                points = listOf(
                    ReciprocityPoint(1.0, 1.0),
                    ReciprocityPoint(4.0, 4.0 * 2.0.pow(0.5), exposureStops = 0.5),
                    ReciprocityPoint(16.0, 32.0, exposureStops = 1.0),
                ),
                maximumSeconds = 16.0,
            ),
            colorNote = "官方资料明确不建议超过 16 秒；玩家经验也认为其长曝光表现相对温和。",
        ),
        referenceProfile("fujicolor-pro-800z", "Pro 800Z", 800, FilmCategory.COLOR_NEGATIVE),
        profile(
            id = "fujicolor-superia-100",
            name = "Superia 100",
            manufacturer = "Fujifilm US",
            iso = 100,
            category = FilmCategory.COLOR_NEGATIVE,
            source = ReciprocitySource.OFFICIAL,
            sourceNote = "Fujifilm Superia 100 Data Sheet",
            curve = table(
                ReciprocityPoint(2.0, 2.0),
                ReciprocityPoint(4.0, 4.0 * 2.0.pow(1.0 / 3.0), exposureStops = 1.0 / 3.0),
                ReciprocityPoint(16.0, 16.0 * 2.0.pow(2.0 / 3.0), exposureStops = 2.0 / 3.0),
            ),
        ),
        profile(
            id = "fujicolor-superia-200",
            name = "Superia 200",
            manufacturer = "Fujifilm US",
            iso = 200,
            category = FilmCategory.COLOR_NEGATIVE,
            source = ReciprocitySource.OFFICIAL,
            sourceNote = "Fujifilm Superia 200 Data Sheet",
            curve = table(
                ReciprocityPoint(2.0, 2.0),
                ReciprocityPoint(4.0, 4.0 * 2.0.pow(1.0 / 3.0), exposureStops = 1.0 / 3.0),
                ReciprocityPoint(16.0, 16.0 * 2.0.pow(2.0 / 3.0), exposureStops = 2.0 / 3.0),
                ReciprocityPoint(64.0, 128.0, exposureStops = 1.0),
            ),
        ),
        profile(
            id = "fujicolor-superia-xtra-400",
            name = "Superia X-TRA 400",
            manufacturer = "Fujifilm US",
            iso = 400,
            category = FilmCategory.COLOR_NEGATIVE,
            source = ReciprocitySource.OFFICIAL,
            sourceNote = "Fujifilm US Professional Data Guide / X-TRA 400 Data Sheet",
            curve = table(
                ReciprocityPoint(2.0, 2.0),
                ReciprocityPoint(4.0, 4.0 * 2.0.pow(1.0 / 3.0), exposureStops = 1.0 / 3.0),
                ReciprocityPoint(16.0, 16.0 * 2.0.pow(2.0 / 3.0), exposureStops = 2.0 / 3.0),
                ReciprocityPoint(64.0, 128.0, exposureStops = 1.0),
            ),
        ),
        profile(
            id = "fujicolor-superia-xtra-800",
            name = "Superia X-TRA 800",
            manufacturer = "Fujifilm US",
            iso = 800,
            category = FilmCategory.COLOR_NEGATIVE,
            source = ReciprocitySource.OFFICIAL,
            sourceNote = "Fujifilm Superia X-TRA 800 Data Sheet",
            curve = table(
                ReciprocityPoint(2.0, 2.0),
                ReciprocityPoint(4.0, 4.0 * 2.0.pow(2.0 / 3.0), exposureStops = 2.0 / 3.0),
                ReciprocityPoint(16.0, 16.0 * 2.0.pow(1.5), exposureStops = 1.5),
            ),
            colorNote = "官方资料覆盖至 16 秒；更长时间建议实拍测试。",
        ),
    )

    fun calculate(profile: FilmReciprocityProfile, meteredSeconds: Double): ReciprocityResult {
        require(meteredSeconds > 0.0) { "测光时间必须大于 0 秒" }
        return when (val curve = profile.curve) {
            ReciprocityCurve.ReferenceOnly -> ReciprocityResult(
                meteredSeconds = meteredSeconds,
                correctedSeconds = null,
                exposureStops = null,
                filter = null,
                source = profile.source,
                warning = "当前胶卷缺少可靠的长曝光数据，建议查阅厂家资料或使用包围曝光。",
            )
            is ReciprocityCurve.PowerLaw -> calculatePowerLaw(profile, curve, meteredSeconds)
            is ReciprocityCurve.Table -> calculateTable(profile, curve, meteredSeconds)
        }
    }

    fun customProfile(name: String, exponent: Double, onsetSeconds: Double): FilmReciprocityProfile {
        require(name.isNotBlank()) { "胶卷名称不能为空" }
        require(exponent >= 1.0) { "倒易律参数必须不小于 1" }
        require(onsetSeconds > 0.0) { "起始时间必须大于 0 秒" }
        return profile(
            id = "custom",
            name = name.trim(),
            manufacturer = "自定义",
            iso = null,
            category = FilmCategory.COLOR_NEGATIVE,
            source = ReciprocitySource.CUSTOM,
            sourceNote = "用户输入的倒易律参数",
            curve = ReciprocityCurve.PowerLaw(exponent, onsetSeconds),
        )
    }

    private fun calculatePowerLaw(profile: FilmReciprocityProfile, curve: ReciprocityCurve.PowerLaw, time: Double): ReciprocityResult {
        val corrected = if (time <= curve.onsetSeconds) time else time.pow(curve.exponent)
        return ReciprocityResult(
            meteredSeconds = time,
            correctedSeconds = corrected,
            exposureStops = log2(corrected / time).takeIf { it > 0.005 },
            filter = null,
            source = profile.source,
            warning = profile.colorNote,
        )
    }

    private fun calculateTable(profile: FilmReciprocityProfile, curve: ReciprocityCurve.Table, time: Double): ReciprocityResult {
        val points = curve.points.sortedBy { it.meteredSeconds }
        val first = points.first()
        val last = points.last()
        val lastRecommendedSeconds = if (last.filter == "不建议" && points.size > 1) points[points.lastIndex - 1].meteredSeconds else last.meteredSeconds
        val outOfRange = curve.maximumSeconds?.let { time > it } == true || time > lastRecommendedSeconds
        if (outOfRange) {
            return ReciprocityResult(time, null, null, last.filter, profile.source, "超出资料建议范围，不建议继续自动估算。${profile.colorNote?.let { " $it" } ?: ""}")
        }
        if (time < curve.onsetSeconds || time < first.meteredSeconds) {
            return ReciprocityResult(time, time, null, first.filter, profile.source, profile.colorNote)
        }
        val upperIndex = points.indexOfFirst { it.meteredSeconds >= time }.coerceAtLeast(1)
        val lower = points[upperIndex - 1]
        val upper = points[upperIndex]
        val fraction = (ln(time) - ln(lower.meteredSeconds)) / (ln(upper.meteredSeconds) - ln(lower.meteredSeconds))
        val corrected = (ln(lower.correctedSeconds) + fraction * (ln(upper.correctedSeconds) - ln(lower.correctedSeconds))).let(::exp)
        val filter = if (fraction >= 0.5) upper.filter else lower.filter
        return ReciprocityResult(time, corrected, log2(corrected / time).takeIf { it > 0.005 }, filter, profile.source, profile.colorNote)
    }

    private fun exp(value: Double): Double = kotlin.math.exp(value)

    private fun table(vararg points: Pair<Double, Double>): ReciprocityCurve.Table =
        ReciprocityCurve.Table(points.map { ReciprocityPoint(it.first, it.second) })

    private fun table(vararg points: ReciprocityPoint): ReciprocityCurve.Table =
        ReciprocityCurve.Table(points.toList())

    private fun profile(
        id: String,
        name: String,
        manufacturer: String,
        iso: Int?,
        category: FilmCategory,
        source: ReciprocitySource,
        sourceNote: String,
        curve: ReciprocityCurve,
        colorNote: String? = null,
    ) = FilmReciprocityProfile(id, name, manufacturer, iso, category, source, sourceNote, curve, colorNote)

    private fun referenceProfile(id: String, name: String, iso: Int, category: FilmCategory) = profile(
        id = id,
        name = name,
        manufacturer = if (id.startsWith("kodak")) "Kodak" else "Fujifilm US",
        iso = iso,
        category = category,
        source = ReciprocitySource.REFERENCE_ONLY,
        sourceNote = "厂家资料未提供可直接套用的完整倒易律表",
        curve = ReciprocityCurve.ReferenceOnly,
        colorNote = "彩色胶片的乳剂、冲洗和扫描条件都会影响结果。",
    )
}
