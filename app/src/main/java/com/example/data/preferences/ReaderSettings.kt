package com.example.data.preferences

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

enum class ReaderTextAlignment(val label: String) {
    LEFT("Left"),
    JUSTIFY("Justify")
}

enum class PageTurnMode(val label: String) {
    CURL("Page Curl"),
    SLIDE("Slide"),
    COVER_FLIP("Cover Flip"),
    STACK("Stack"),
    FADE("Fade"),
    SCROLL("Scroll"),
    NONE("None")
}

enum class CurlGrabMode(val label: String) {
    CORNERS_AND_EDGES("Corners & Edges"),
    CORNERS_ONLY("Corners Only"),
    EDGES_ONLY("Edges Only")
}

enum class PdfFitMode(val label: String) {
    FIT_PAGE("Fit Page"),
    FIT_WIDTH("Fit Width")
}

enum class PageSpreadMode(val label: String) {
    AUTO("Auto (Dual on Tablet)"),
    SINGLE("Single Page"),
    DUAL("Dual Spread")
}

enum class OrientationLock(val label: String) {
    UNLOCKED("Auto"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class TapZonePreset(val label: String) {
    CLASSIC("Classic"),
    LEFT_HANDED("Left-handed"),
    SCROLL_ONLY("Scroll")
}

enum class ReaderGesture(val label: String) {
    TAP_LEFT("Tap Left Edge"),
    TAP_CENTER("Tap Center"),
    TAP_RIGHT("Tap Right Edge"),
    SWIPE_UP("Swipe Up"),
    SWIPE_DOWN("Swipe Down"),
    LONG_PRESS("Long Press"),
    VOLUME_UP("Volume Up"),
    VOLUME_DOWN("Volume Down")
}

enum class ReaderAction(val label: String) {
    NEXT_PAGE("Next Page"),
    PREV_PAGE("Previous Page"),
    TOGGLE_CHROME("Toggle Controls"),
    NEXT_CHAPTER("Next Chapter"),
    PREV_CHAPTER("Previous Chapter"),
    TOGGLE_THEME("Toggle Light/Dark"),
    TOGGLE_AUTO_SCROLL("Toggle Auto-Scroll"),
    OPEN_TOC("Open Contents"),
    OPEN_SEARCH("Search in Book"),
    UNDO_JUMP("Undo Jump"),
    NONE("Do Nothing")
}

enum class AutoScrollMode(val label: String) {
    OFF("Off"),
    CONTINUOUS("Continuous"),
    BY_PIXEL("By Pixel"),
    BY_LINE("By Line"),
    BY_PAGE("By Page"),
    ROLLING_BLIND("Rolling Blind")
}

@JsonClass(generateAdapter = true)
data class ImportedFontInfo(
    val id: String,
    val displayName: String,
    val filePath: String
)

@JsonClass(generateAdapter = true)
data class ReaderSettings(
    val fontId: String = "literata",
    val fontSizeSp: Float = 18f,
    val lineHeightMultiplier: Float = 1.52f,
    val paragraphSpacingDp: Float = 10f,
    val marginHorizontalDp: Float = 24f,
    val marginVerticalDp: Float = 22f,
    val alignment: ReaderTextAlignment = ReaderTextAlignment.LEFT,
    val firstLineIndentDp: Float = 0f,
    val hyphenationEnabled: Boolean = false,
    val letterSpacingSp: Float = 0f,
    val pageTurnMode: PageTurnMode = PageTurnMode.CURL,
    val pageSpreadMode: PageSpreadMode = PageSpreadMode.AUTO,
    val columnsPerPage: Int = 1,
    val orientationLock: OrientationLock = OrientationLock.UNLOCKED,
    val readingRulerEnabled: Boolean = false,
    val miniStatusBarEnabled: Boolean = true,
    val keepScreenAwake: Boolean = false,
    val brightness: Float = -1f, // -1f = follow system
    val tapZonePreset: TapZonePreset = TapZonePreset.CLASSIC,
    val actionMap: Map<String, String> = defaultActionMap(TapZonePreset.CLASSIC),
    val autoScrollMode: AutoScrollMode = AutoScrollMode.OFF,
    val autoScrollSpeed: Float = 1.0f,
    val importedFonts: List<ImportedFontInfo> = emptyList(),
    val quickHighlightEnabled: Boolean = false,
    val activeHighlightColorRole: Int = 0,
    val activeHighlightStyle: String = "HIGHLIGHT",
    val colorBlindPaletteEnabled: Boolean = false,
    val copyWithCitationEnabled: Boolean = true,
    val vaultExportFolderUri: String? = null,
    val highlightUnknownWordsEnabled: Boolean = false,
    val tapToDefineEnabled: Boolean = false,
    val perLanguageDefaultDict: Map<String, Long> = emptyMap(),
    // Milestone F: Interactive Page Curl tuning
    val curlStrength: Float = 0.78f,
    val curlShadowStrength: Float = 0.65f,
    val curlShowPageBack: Boolean = true,
    val curlSpeed: Float = 1.0f,
    val curlGrabMode: CurlGrabMode = CurlGrabMode.CORNERS_AND_EDGES,
    // Milestone E: Fixed-layout PDF & Studio preferences
    val pdfFitMode: PdfFitMode = PdfFitMode.FIT_PAGE,
    val pdfAutoMarginCrop: Boolean = false,
    val pdfSmartDarkInvert: Boolean = false,
    val pdfReflowEnabled: Boolean = false,
    val studioPalmRejection: Boolean = false
) {
    fun actionFor(gesture: ReaderGesture): ReaderAction {
        val raw = actionMap[gesture.name] ?: return defaultActionMap(tapZonePreset)[gesture.name]
            ?.let { ReaderAction.valueOf(it) } ?: ReaderAction.NONE
        return runCatching { ReaderAction.valueOf(raw) }.getOrDefault(ReaderAction.NONE)
    }

    fun withTapPreset(preset: TapZonePreset): ReaderSettings = copy(
        tapZonePreset = preset,
        actionMap = defaultActionMap(preset)
    )

    companion object {
        private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        private val adapter = moshi.adapter(ReaderSettings::class.java)

        fun toJson(settings: ReaderSettings): String = adapter.toJson(settings)
        fun fromJson(json: String?): ReaderSettings? =
            if (json.isNullOrBlank()) null else runCatching { adapter.fromJson(json) }.getOrNull()

        fun defaultActionMap(preset: TapZonePreset): Map<String, String> = when (preset) {
            TapZonePreset.CLASSIC -> mapOf(
                ReaderGesture.TAP_LEFT.name to ReaderAction.PREV_PAGE.name,
                ReaderGesture.TAP_CENTER.name to ReaderAction.TOGGLE_CHROME.name,
                ReaderGesture.TAP_RIGHT.name to ReaderAction.NEXT_PAGE.name,
                ReaderGesture.SWIPE_UP.name to ReaderAction.OPEN_TOC.name,
                ReaderGesture.SWIPE_DOWN.name to ReaderAction.OPEN_SEARCH.name,
                ReaderGesture.LONG_PRESS.name to ReaderAction.TOGGLE_CHROME.name,
                ReaderGesture.VOLUME_UP.name to ReaderAction.PREV_PAGE.name,
                ReaderGesture.VOLUME_DOWN.name to ReaderAction.NEXT_PAGE.name
            )
            TapZonePreset.LEFT_HANDED -> mapOf(
                ReaderGesture.TAP_LEFT.name to ReaderAction.NEXT_PAGE.name,
                ReaderGesture.TAP_CENTER.name to ReaderAction.TOGGLE_CHROME.name,
                ReaderGesture.TAP_RIGHT.name to ReaderAction.PREV_PAGE.name,
                ReaderGesture.SWIPE_UP.name to ReaderAction.OPEN_TOC.name,
                ReaderGesture.SWIPE_DOWN.name to ReaderAction.OPEN_SEARCH.name,
                ReaderGesture.LONG_PRESS.name to ReaderAction.TOGGLE_CHROME.name,
                ReaderGesture.VOLUME_UP.name to ReaderAction.PREV_PAGE.name,
                ReaderGesture.VOLUME_DOWN.name to ReaderAction.NEXT_PAGE.name
            )
            TapZonePreset.SCROLL_ONLY -> mapOf(
                ReaderGesture.TAP_LEFT.name to ReaderAction.TOGGLE_CHROME.name,
                ReaderGesture.TAP_CENTER.name to ReaderAction.TOGGLE_CHROME.name,
                ReaderGesture.TAP_RIGHT.name to ReaderAction.TOGGLE_CHROME.name,
                ReaderGesture.SWIPE_UP.name to ReaderAction.NONE.name,
                ReaderGesture.SWIPE_DOWN.name to ReaderAction.NONE.name,
                ReaderGesture.LONG_PRESS.name to ReaderAction.TOGGLE_AUTO_SCROLL.name,
                ReaderGesture.VOLUME_UP.name to ReaderAction.PREV_PAGE.name,
                ReaderGesture.VOLUME_DOWN.name to ReaderAction.NEXT_PAGE.name
            )
        }
    }
}
