package com.example.data.studio

import com.example.data.db.AnnotationLayerEntity
import com.example.data.db.MarkupObjectEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

enum class StudioTool(val label: String, val isCreation: Boolean) {
    SELECT("Select", false),
    FREEHAND("Pen", true),
    HIGHLIGHTER("Highlighter", true),
    LINE("Line", true),
    ARROW("Arrow", true),
    POLYLINE("Polyline", true),
    RECT("Rectangle", true),
    ELLIPSE("Ellipse", true),
    ARC("Arc", true),
    POLYGON("Polygon", true),
    CLOUD("Cloud", true),
    CALLOUT("Callout", true),
    DIMENSION_DISTANCE("Measure", true),
    DIMENSION_ANGLE("Angle", true),
    DIMENSION_AREA("Area", true),
    TEXT_NOTE("Note", true),
    STAMP("Stamp", true),
    SPOTLIGHT("Spotlight", true),
    CALIBRATE("Calibrate", false)
}

enum class DashStyle(val label: String) {
    SOLID("Solid"),
    DASHED("Dashed"),
    DOTTED("Dotted")
}

enum class ArrowHeadStyle(val label: String) {
    NONE("None"),
    OPEN("Open"),
    FILLED("Filled"),
    TICK("Tick")
}

enum class MeasurementUnit(val suffix: String, val label: String) {
    MM("mm", "Millimeters (mm)"),
    CM("cm", "Centimeters (cm)"),
    M("m", "Meters (m)"),
    IN("in", "Inches (in)"),
    FT("ft", "Feet (ft)")
}

enum class StampKind(val badgeText: String, val defaultHex: String) {
    APPROVED("APPROVED", "#2E9D5B"),
    REVIEWED("REVIEWED", "#3B7DDD"),
    CONFIDENTIAL("CONFIDENTIAL", "#D93848"),
    DRAFT("DRAFT", "#E08D1F"),
    CHECK("✓ VERIFIED", "#2E9D5B"),
    STAR("★ KEY DETAIL", "#9B51E0")
}

@JsonClass(generateAdapter = true)
data class StudioPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f
)

@JsonClass(generateAdapter = true)
data class StudioGeometryJson(
    val x1: Float = 0f,
    val y1: Float = 0f,
    val x2: Float = 0f,
    val y2: Float = 0f,
    val points: List<StudioPoint> = emptyList(),
    val cx: Float = 0f,
    val cy: Float = 0f,
    val radiusX: Float = 0f,
    val radiusY: Float = 0f,
    val rotationDeg: Float = 0f,
    val startAngleDeg: Float = 0f,
    val sweepAngleDeg: Float = 180f,
    val sides: Int = 6,
    val calloutTargetX: Float = 0f,
    val calloutTargetY: Float = 0f,
    val text: String = "",
    val stampKind: String = StampKind.REVIEWED.name
) {
    companion object {
        private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        private val adapter = moshi.adapter(StudioGeometryJson::class.java)

        fun toJson(geo: StudioGeometryJson): String = adapter.toJson(geo)
        fun fromJson(json: String?): StudioGeometryJson =
            if (json.isNullOrBlank()) StudioGeometryJson() else runCatching { adapter.fromJson(json) }.getOrNull() ?: StudioGeometryJson()
    }
}

@JsonClass(generateAdapter = true)
data class StudioStyleJson(
    val strokeColorHex: String = "#E03A3E",
    val fillColorHex: String = "",
    val strokeWidthPt: Float = 2.4f,
    val opacity: Float = 1f,
    val dashStyle: String = DashStyle.SOLID.name,
    val startArrow: String = ArrowHeadStyle.NONE.name,
    val endArrow: String = ArrowHeadStyle.NONE.name,
    val cornerRadiusPt: Float = 0f,
    val fontSizeSp: Float = 13f
) {
    companion object {
        private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        private val adapter = moshi.adapter(StudioStyleJson::class.java)

        fun toJson(style: StudioStyleJson): String = adapter.toJson(style)
        fun fromJson(json: String?): StudioStyleJson =
            if (json.isNullOrBlank()) StudioStyleJson() else runCatching { adapter.fromJson(json) }.getOrNull() ?: StudioStyleJson()
    }
}

data class StudioPreset(
    val name: String,
    val style: StudioStyleJson
)

object StudioPresets {
    val all = listOf(
        StudioPreset("Redline", StudioStyleJson(strokeColorHex = "#E03A3E", strokeWidthPt = 2.4f, opacity = 1f)),
        StudioPreset("Blueprint", StudioStyleJson(strokeColorHex = "#2F7CF6", strokeWidthPt = 2.2f, opacity = 1f)),
        StudioPreset("Highlighter", StudioStyleJson(strokeColorHex = "#F4C542", fillColorHex = "#F4C542", strokeWidthPt = 14f, opacity = 0.42f)),
        StudioPreset("Mochi Peach", StudioStyleJson(strokeColorHex = "#E6739F", fillColorHex = "#FFDCE9", strokeWidthPt = 2.4f, opacity = 0.9f)),
        StudioPreset("Forest Ink", StudioStyleJson(strokeColorHex = "#2E8B57", strokeWidthPt = 2.0f, opacity = 1f))
    )
}

sealed class StudioCommand {
    data class AddMarkup(val created: MarkupObjectEntity) : StudioCommand()
    data class UpdateMarkup(val before: MarkupObjectEntity, val after: MarkupObjectEntity) : StudioCommand()
    data class DeleteMarkups(val deleted: List<MarkupObjectEntity>) : StudioCommand()
    data class BatchUpdate(val before: List<MarkupObjectEntity>, val after: List<MarkupObjectEntity>) : StudioCommand()
}

data class StudioSessionState(
    val activeTool: StudioTool = StudioTool.SELECT,
    val activeLayerId: Long = 0L,
    val activeStyle: StudioStyleJson = StudioPresets.all.first().style,
    val snapEnabled: Boolean = true,
    val gridSnapEnabled: Boolean = true,
    val orthoLockEnabled: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val undoStack: List<StudioCommand> = emptyList(),
    val redoStack: List<StudioCommand> = emptyList(),
    val clipboard: List<MarkupObjectEntity> = emptyList(),
    val layers: List<AnnotationLayerEntity> = emptyList(),
    val activeStamp: StampKind = StampKind.APPROVED
)
