package app.elauncher.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.core.view.setPadding
import app.elauncher.R
import app.elauncher.data.AppSlot
import app.elauncher.data.Constants
import app.elauncher.data.GridItem
import app.elauncher.data.GridItemType
import app.elauncher.helper.FontManager
import app.elauncher.helper.WidgetHostManager
import app.elauncher.helper.dpToPx
import app.elauncher.helper.getColorFromAttr
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A rendering/layout component for a single [app.elauncher.data.Page]'s grid items.
 *
 * This view does not touch Prefs or navigation, and it does not detect the *normal* (non-editing)
 * gestures itself: today's codebase resolves tap/long-press/swipe together per-view via a single
 * GestureDetector-based View.OnTouchListener (see HomeFragment.getViewSwipeTouchListener), and there
 * is no touch-event-propagation-to-parent mechanism for swipes to inherit here. So every cell -
 * occupied or empty - gets its entire touch handling delegated to a listener supplied by the caller
 * via [setItems].
 *
 * The one exception is *edit mode* (Step 19): once [enterEditMode] is called for an item, this view
 * takes over touch handling for the whole grid until edit mode ends, because move/resize are
 * continuous drags that a fling/tap gesture detector cannot express. Edit mode is pure geometry -
 * the committed result is handed back to the caller through the `onItemsChanged`/`onItemDeleted`
 * callbacks of [setItems], which own persistence.
 */
class HomeGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val cellSizePx: Int = (Constants.Grid.CELL_SIZE_DP * resources.displayMetrics.density).toInt()
    private val touchSlopPx: Int = ViewConfiguration.get(context).scaledTouchSlop

    /**
     * How long a press on a widget has to last before it selects the widget instead of reaching the
     * widget's own content. Matches what an app cell needs: ViewSwipeTouchListener fires its
     * onLongClick a further LONG_PRESS_DELAY_MS after the framework reports a long press.
     */
    private val widgetLongPressTimeoutMs: Long =
        ViewConfiguration.getLongPressTimeout() + Constants.LONG_PRESS_DELAY_MS

    private var columnCount: Int = 0
    private var rowCount: Int = 0

    /**
     * Where cell (0, 0) starts inside this view. A view's size is almost never an exact multiple of
     * the cell size, and the whole-cell count discards that remainder - left at 0 the leftover
     * would all pile up against the right/bottom edge as unreachable dead space, so it is split
     * evenly between the two edges of each axis instead. At most half a cell per side.
     */
    private var gridOffsetX: Int = 0
    private var gridOffsetY: Int = 0

    private var items: List<GridItem> = emptyList()
    private var touchListenerFor: ((col: Int, row: Int, item: GridItem?) -> View.OnTouchListener)? = null
    private var slotTouchListenerFor: ((item: GridItem, slotIndex: Int) -> View.OnTouchListener)? = null
    private var isAppSlotUnavailable: ((AppSlot) -> Boolean)? = null
    private var onItemsChanged: ((List<GridItem>) -> Unit)? = null
    private var onItemDeleted: ((GridItem) -> Unit)? = null
    private var onOpenSettings: ((GridItem) -> Unit)? = null

    // DATE_TIME rendering/wiring - all caller-owned for the same reason touchListenerFor etc. are
    // (this view touches no Prefs/Context helpers): dateTextProvider/screenTimeTextProvider supply
    // the already-formatted text (HomeFragment's preserved formatDateText()/currentScreenTimeText(),
    // Step 7), and the on*Click/on*LongClick callbacks are the preserved openClockApp()/
    // openCalendarApp()/reassignment flows, re-wired onto the views createDateTimeView builds
    // instead of the old fixed pageBinding.clock/date (Step 10).
    private var dateTextProvider: (() -> String)? = null
    private var screenTimeTextProvider: (() -> String?)? = null
    private var onClockClick: (() -> Unit)? = null
    private var onClockLongClick: (() -> Unit)? = null
    private var onDateClick: (() -> Unit)? = null
    private var onDateLongClick: (() -> Unit)? = null

    /** The item currently being edited, identified by reference into [items]. Null when not editing. */
    private var editingItem: GridItem? = null
    private var editingItemView: View? = null

    /**
     * The widget ids that still had a live provider at the last [rebuildChildren] - i.e. the cells
     * currently showing a real widget rather than the "unavailable" placeholder. Compared against
     * the current state in [onProvidersChanged] so an unrelated app install/update doesn't tear
     * down and re-create every hosted view on this page for nothing.
     */
    private var liveWidgetIds: Set<Int> = emptySet()

    /**
     * Kept as one instance so attach/detach add and remove the same object: [WidgetHostManager]
     * outlives every view, so a page view recycled by ViewPager2 that never unregistered would be
     * leaked by it (and would keep rebuilding itself off-screen).
     */
    private val providersChangedListener: () -> Unit = { onProvidersChanged() }

    // Edit-mode chrome, all direct children of this view, added on top of the cells in this order.
    private var scrimView: View? = null
    private var gridLinesView: View? = null
    private var previewView: View? = null
    private var overlayView: View? = null

    /**
     * Clears and re-adds one child view per occupied cell plus one invisible/transparent view
     * per unoccupied cell (so empty cells are addressable too). [touchListenerFor] is called
     * for every cell, occupied or empty, and its result is set via [View.setOnTouchListener] -
     * this view has no click/long-click/swipe logic of its own.
     *
     * An App List item's rows are addressable individually rather than as one cell, so
     * [slotTouchListenerFor] supplies the same kind of caller-owned gesture listener per slot -
     * which, being on a child view filling the cell, is what a touch on such a cell reaches instead
     * of [touchListenerFor]'s listener (see [createAppListView]). [isAppSlotUnavailable] answers
     * "is this filled slot's app still resolvable", the one question about a slot this view can't
     * answer itself (no package-manager access, same reasoning as Prefs/navigation).
     *
     * [onItemsChanged] is invoked with the (mutated in place) item list after an edit-mode move or
     * resize commits, and [onItemDeleted] when the edit-mode delete badge is tapped; both exist so
     * that persistence stays with the caller, exactly like the gesture callbacks above.
     * [onOpenSettings] is the same shape again, for the edit-mode settings (gear) badge shown on the
     * item types that have per-item settings ([hasSettings]): this view knows nothing about what
     * those settings are or how they are edited, it only reports the tap.
     */
    fun setItems(
        items: List<GridItem>,
        touchListenerFor: (col: Int, row: Int, item: GridItem?) -> View.OnTouchListener,
        slotTouchListenerFor: ((item: GridItem, slotIndex: Int) -> View.OnTouchListener)? = null,
        isAppSlotUnavailable: ((AppSlot) -> Boolean)? = null,
        onItemsChanged: ((List<GridItem>) -> Unit)? = null,
        onItemDeleted: ((GridItem) -> Unit)? = null,
        onOpenSettings: ((GridItem) -> Unit)? = null,
        dateTextProvider: (() -> String)? = null,
        screenTimeTextProvider: (() -> String?)? = null,
        onClockClick: (() -> Unit)? = null,
        onClockLongClick: (() -> Unit)? = null,
        onDateClick: (() -> Unit)? = null,
        onDateLongClick: (() -> Unit)? = null,
    ) {
        this.items = items
        this.touchListenerFor = touchListenerFor
        this.slotTouchListenerFor = slotTouchListenerFor
        this.isAppSlotUnavailable = isAppSlotUnavailable
        this.onItemsChanged = onItemsChanged
        this.onItemDeleted = onItemDeleted
        this.onOpenSettings = onOpenSettings
        this.dateTextProvider = dateTextProvider
        this.screenTimeTextProvider = screenTimeTextProvider
        this.onClockClick = onClockClick
        this.onClockLongClick = onClockLongClick
        this.onDateClick = onDateClick
        this.onDateLongClick = onDateLongClick
        // A fresh list means fresh GridItem instances (they're re-parsed from Prefs' JSON on every
        // read), so an item being edited can't survive a rebind - drop edit mode rather than
        // silently editing a detached copy.
        editingItem = editingItem?.let { editing -> items.firstOrNull { it === editing } }
        rebuildChildren()
    }

    fun columnCount(): Int = columnCount

    fun rowCount(): Int = rowCount

    /**
     * Selects [item] for editing: it gets a highlighted outline, resize handles (subject to the
     * provider's declared constraints for widgets) and a delete badge, and a full-grid scrim goes
     * up behind it so that any touch that isn't on the item exits edit mode instead of reaching a
     * cell's normal tap/swipe listener.
     */
    fun enterEditMode(item: GridItem) {
        if (items.none { it === item }) return
        if (editingItem === item) return
        removeEditChrome()
        editingItem = item
        if (clampToGrid(item)) {
            // The item didn't fit the grid before the user ever touched it, so every move/resize
            // target would be rejected as out of bounds and edit mode would appear broken. Repair it
            // first, then let the rebuild lay it out at its corrected size and re-add the chrome.
            // isAttachedToWindow guard: see onSizeChanged's kdoc for why a deferred rebuildChildren()
            // needs this (it calls back into HomeFragment via touchListenerFor, which throws if the
            // owning fragment was detached before this runnable fires).
            onItemsChanged?.invoke(items)
            post { if (isAttachedToWindow) rebuildChildren() }
            return
        }
        editingItemView = viewForItem(item)
        addEditChrome()
    }

    /**
     * Pulls [item] inside the grid, returning true if anything had to change.
     *
     * Items reach this view from storage, where nothing guarantees they still fit: they may have
     * been sized against a different screen (rotation, a restored backup) or restated by the
     * one-time cell-size rescale in Prefs. Anything out of bounds would fail every subsequent
     * move/resize as "doesn't fit", so it is repaired on selection instead.
     */
    private fun clampToGrid(item: GridItem): Boolean {
        if (columnCount <= 0 || rowCount <= 0) return false
        val spanX = item.spanX.coerceIn(1, columnCount)
        val spanY = item.spanY.coerceIn(1, rowCount)
        val col = item.col.coerceIn(0, columnCount - spanX)
        val row = item.row.coerceIn(0, rowCount - spanY)
        if (spanX == item.spanX && spanY == item.spanY && col == item.col && row == item.row) return false
        item.spanX = spanX
        item.spanY = spanY
        item.col = col
        item.row = row
        return true
    }

    fun exitEditMode() {
        if (editingItem == null) return
        editingItem = null
        editingItemView?.let {
            it.translationX = 0f
            it.translationY = 0f
        }
        editingItemView = null
        // Called from inside the scrim's/overlay's own touch handler, so defer the detach to right
        // after the current dispatch finishes (same reasoning as onSizeChanged below).
        post { removeEditChrome() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        WidgetHostManager.addProvidersChangedListener(providersChangedListener)
    }

    override fun onDetachedFromWindow() {
        WidgetHostManager.removeProvidersChangedListener(providersChangedListener)
        super.onDetachedFromWindow()
    }

    /**
     * A widget provider was installed, updated, disabled or uninstalled somewhere on the device.
     *
     * Without this, a widget whose app is uninstalled while its page is on screen keeps showing the
     * last frame the provider ever drew until something else forces a rebuild (the next resume, in
     * practice) - so the user can be looking at a widget that no longer exists. Rebuilding
     * re-resolves every widget id and swaps the dead ones for the "unavailable" placeholder.
     * Guarded so this is a no-op for the common case (a background app update that has nothing to
     * do with this page's widgets), because a rebuild re-creates hosted views.
     */
    private fun onProvidersChanged() {
        if (items.none { it.type == GridItemType.WIDGET }) return
        if (currentLiveWidgetIds() == liveWidgetIds) return
        rebuildChildren()
    }

    private fun currentLiveWidgetIds(): Set<Int> = items.asSequence()
        .filter { it.type == GridItemType.WIDGET }
        .mapNotNull { it.appWidgetId }
        .filter { WidgetHostManager.providerInfoFor(context, it) != null }
        .toSet()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeGeometry(w, h)
        // Adding/removing children synchronously here would mutate the hierarchy while this
        // view's parent is still mid-layout-pass, leaving the newly added cells with a stale
        // (zero) measured size until some later, unrelated layout pass happens to fix them up -
        // post() defers the rebuild to right after the current traversal finishes instead.
        //
        // isAttachedToWindow guards against a real crash: rebuildChildren() calls back into
        // HomeFragment (touchListenerFor -> cellTouchListenerFor -> requireContext()) to build
        // each cell's listener. If this view - and the fragment that owns it - were detached by
        // the time this deferred block runs (e.g. a system dialog like the "set as default
        // launcher" role request causing MainActivity/HomeFragment to be recreated while this
        // Runnable was still queued from the old instance), requireContext() throws
        // IllegalStateException. A detached view has nothing useful to rebuild anyway.
        post { if (isAttachedToWindow) rebuildChildren() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        recomputeGeometry(measuredWidth, measuredHeight)
    }

    private fun recomputeGeometry(width: Int, height: Int) {
        if (cellSizePx <= 0) return
        columnCount = floor(width.toFloat() / cellSizePx).toInt()
        rowCount = floor(height.toFloat() / cellSizePx).toInt()
        gridOffsetX = ((width - columnCount * cellSizePx) / 2).coerceAtLeast(0)
        gridOffsetY = ((height - rowCount * cellSizePx) / 2).coerceAtLeast(0)
    }

    private fun rebuildChildren() {
        val touchListenerFor = this.touchListenerFor ?: return
        if (columnCount <= 0 || rowCount <= 0) return

        // Every cell view - hosted widget views included - is created fresh below, so nothing is
        // ever carried over from a previous bind of this (possibly recycled) page view.
        removeAllViews()
        scrimView = null
        gridLinesView = null
        previewView = null
        overlayView = null
        editingItemView = null
        liveWidgetIds = currentLiveWidgetIds()

        val itemsByCell = mutableMapOf<Pair<Int, Int>, GridItem>()
        items.forEach { item -> itemsByCell[item.col to item.row] = item }

        // Cell labels are built in code, never inflated from XML, and are rebuilt on every
        // setItems()/resize - so the fragment-level typeface walk can't reach them and each cell
        // applies the custom font itself. Resolved once per rebuild rather than per cell.
        val customTypeface = FontManager.activeCustomTypeface(context)

        val occupiedByOtherCell = mutableSetOf<Pair<Int, Int>>()
        items.forEach { item ->
            for (dx in 0 until item.spanX) {
                for (dy in 0 until item.spanY) {
                    if (dx == 0 && dy == 0) continue
                    occupiedByOtherCell.add((item.col + dx) to (item.row + dy))
                }
            }
        }

        for (row in 0 until rowCount) {
            for (col in 0 until columnCount) {
                val cell = col to row
                if (cell in occupiedByOtherCell) continue // covered by another item's span

                val item = itemsByCell[cell]
                val cellView = createCellView(item, customTypeface)
                cellView.setOnTouchListener(touchListenerFor(col, row, item))
                if (item != null && item === editingItem) editingItemView = cellView

                val spanX = item?.spanX ?: 1
                val spanY = item?.spanY ?: 1
                addView(cellView, cellParams(col, row, spanX, spanY))
            }
        }

        if (editingItem != null) addEditChrome()
    }

    private fun createCellView(item: GridItem?, customTypeface: Typeface?): View {
        return when (item?.type) {
            GridItemType.WIDGET -> createWidgetView(item, customTypeface)
            GridItemType.APP_LIST -> createAppListView(item, customTypeface)
            GridItemType.DATE_TIME -> createDateTimeView(item, customTypeface)
            GridItemType.CLOCK -> createClockView(item, customTypeface)
            // An empty cell, and - since Step 9 - a legacy GridItemType.APP item too: Step 6's
            // migration converted every stored APP item into a one-slot APP_LIST one and Step 9
            // removed the last path that could create a new one, so nothing renders, launches or
            // creates one any more. The enum value survives purely so a straggler unmigrated item
            // still deserializes instead of being dropped.
            GridItemType.APP, null -> View(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        }
    }

    /**
     * Inflates the hosted view for an already-bound widget, via the app-wide [WidgetHostManager]
     * (called directly here for the same reason FontManager is above: it is a process-lifetime
     * object, so no constructor/setter threading is needed). A fresh host view is built on every
     * rebuild, so no [android.appwidget.AppWidgetHostView] is ever shared between two pages'
     * HomeGridViews (ViewPager2 recycles page views - see Step 20).
     *
     * Falls back to the "unavailable" placeholder whenever the widget can't be hosted - most
     * commonly because the provider's app was uninstalled, which leaves the id bound to nothing.
     */
    private fun createWidgetView(item: GridItem, customTypeface: Typeface?): View {
        val appWidgetId = item.appWidgetId ?: return widgetPlaceholderView(customTypeface)
        val info = WidgetHostManager.providerInfoFor(context, appWidgetId)
            ?: return widgetPlaceholderView(customTypeface)
        val hostView = runCatching { WidgetHostManager.createHostView(context, appWidgetId, info) }
            .getOrNull() ?: return widgetPlaceholderView(customTypeface)
        return WidgetCellContainer(context, item).apply {
            addView(hostView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }

    /**
     * A vertical stack of one row per [GridItem.appSlots] entry - `item.appSlots.size` is expected
     * to equal `item.spanY` (an invariant the settings dialog and picking flow are responsible for
     * maintaining, not something enforced defensively here). Each row is its own [TextView], sized
     * with `layout_weight = 1` so the stack divides the cell view's height evenly no matter how many
     * rows there are, which composes with the outer cell's own `spanY * cellSizePx` sizing without
     * this view needing to know [cellSizePx] itself.
     *
     * Row text, in order of preference: [AppSlot.customLabel], else [AppSlot.appName], else the
     * "App" placeholder for an empty slot ([AppSlot.appPackage] == null).
     *
     * Each row carries its own gesture listener from `slotTouchListenerFor` (Step 9), so a tap or
     * long press lands on the *slot*, not the cell. That listener is on a child filling the whole
     * cell, so the outer per-cell listener never sees a touch on an App List item at all - which is
     * why the slot long-press dialog is the only way into edit mode for one (plan.md, "Editing
     * gesture model"). The rows' listeners resolve swipes exactly like the cell listener does, so
     * page switching and the swipe-up drawer keep working over an App List.
     *
     * A filled slot whose app can no longer be resolved (uninstalled or disabled while pinned) keeps
     * its label but is dimmed to [UNAVAILABLE_SLOT_ALPHA], so it reads as neither live nor empty;
     * tapping it clears the slot (HomeFragment.onAppSlotClick).
     */
    private fun createAppListView(item: GridItem, customTypeface: Typeface?): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            item.appSlots.forEachIndexed { slotIndex, slot ->
                addView(
                    TextView(context).apply {
                        setTextAppearance(R.style.TextLarge)
                        text = slot.customLabel ?: slot.appName ?: context.getString(R.string.app)
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        isSingleLine = true
                        gravity = item.alignment
                        if (isAppSlotUnavailable?.invoke(slot) == true) alpha = UNAVAILABLE_SLOT_ALPHA
                        if (customTypeface != null) setTypeface(customTypeface, typeface?.style ?: Typeface.NORMAL)
                        slotTouchListenerFor?.let { setOnTouchListener(it(item, slotIndex)) }
                    },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                )
            }
        }

    /**
     * A standalone clock item: one real, self-updating [TextClock] line. Split out of what used to
     * be the combined DATE_TIME block's clock line (Step 7 split the stored data, this step splits
     * the rendering to match) - construction, gravity, typeface and click wiring are unchanged from
     * that block, just hosted on its own [GridItemType.CLOCK] item instead of a shared one.
     *
     * Wrapped in a single-child [LinearLayout] (rather than returning the [TextClock] directly) so
     * this has the same "always a real container" shape [createDateTimeView]/[createAppListView]
     * return, which is what edit mode attaches its chrome to.
     */
    private fun createClockView(item: GridItem, customTypeface: Typeface?): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextClock(context).apply {
                    setTextAppearance(R.style.TextDefault)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.time_size))
                    // Both formats set explicitly (the old header only set format12Hour) so the
                    // clock reads correctly regardless of the device's 12h/24h setting.
                    format12Hour = "h:mm"
                    format24Hour = "H:mm"
                    gravity = item.alignment
                    if (customTypeface != null) setTypeface(customTypeface, typeface?.style ?: Typeface.NORMAL)
                    onClockClick?.let { onClick -> setOnClickListener { onClick() } }
                    onClockLongClick?.let { onLongClick -> setOnLongClickListener { onLongClick(); true } }
                },
                dateTimeLineParams(),
            )
        }

    /**
     * A vertical block of up to two lines - date, screen time - for a DATE_TIME item.
     * Structurally the same "build in code" shape [createAppListView] uses: each visible line is
     * its own view with `layout_weight = 1` so the stack divides the cell's height evenly, and
     * [GridItem.alignment] is set as every line's own gravity, which is what makes the whole block
     * shift together.
     *
     * Line visibility: the date line is unconditional - presence of a DATE_TIME item on a page is
     * itself the on/off switch now (plan.md, "Date widget visibility"); [GridItem.dateTimeVisibility]
     * is dead from here on (Step 7 only ever reads it once, at migration time). [GridItem.showScreenTime]
     * gates the second line independently, and only when [screenTimeTextProvider] actually has
     * something to show (Q+, usage-access permission granted, a measurement completed - see
     * HomeFragment.currentScreenTimeText()). When nothing is visible (no screen time) this still
     * returns a real, non-empty container (the date line always renders) rather than null/nothing,
     * so edit mode - reached the same way an App List's slots reach it, via a touch on this view -
     * has chrome to attach to.
     *
     * The date line is a plain [TextView] whose text comes from [dateTextProvider] (HomeFragment's
     * preserved formatDateText(), which also folds in the battery percentage when the status bar is
     * hidden) - re-resolved on every rebuild, the same cadence the old fixed header's date text
     * refreshed on (every bind, not a timer).
     *
     * Tap/long-press for the date line is a plain click listener (not the swipe-gesture touch
     * listeners [touchListenerFor]/[slotTouchListenerFor] give cells/slots): the pre-Step-7 header
     * never supported swiping over the clock/date either, only tap-to-launch and
     * long-press-to-reassign, so this reproduces that exactly rather than inventing new gesture
     * support for it.
     */
    private fun createDateTimeView(item: GridItem, customTypeface: Typeface?): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL

            val screenTimeLineText = if (item.showScreenTime) screenTimeTextProvider?.invoke() else null

            addView(
                TextView(context).apply {
                    setTextAppearance(R.style.TextDefault)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.date_size))
                    text = dateTextProvider?.invoke().orEmpty()
                    gravity = item.alignment
                    isSingleLine = true
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    if (customTypeface != null) setTypeface(customTypeface, typeface?.style ?: Typeface.NORMAL)
                    onDateClick?.let { onClick -> setOnClickListener { onClick() } }
                    onDateLongClick?.let { onLongClick -> setOnLongClickListener { onLongClick(); true } }
                },
                dateTimeLineParams(),
            )

            if (screenTimeLineText != null) addView(
                TextView(context).apply {
                    setTextAppearance(R.style.TextSmall)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.date_size))
                    text = screenTimeLineText
                    gravity = item.alignment
                    isSingleLine = true
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    if (customTypeface != null) setTypeface(customTypeface, typeface?.style ?: Typeface.NORMAL)
                    // Deliberately non-interactive - see Step 10 deviation notes (plan.md left the
                    // screen-time line's own tap target, openScreenTimeDigitalWellbeing(), optional).
                },
                dateTimeLineParams(),
            )
        }

    /**
     * Shared layout params for a DATE_TIME line: MATCH_PARENT width (so [GridItem.alignment]'s
     * gravity has room to place text against either edge or center) but - unlike
     * [createAppListView]'s equal-weight rows - WRAP_CONTENT height. Confirmed on-device
     * (Pixel 9 Pro) that dividing the cell height evenly between lines clips the large clock text
     * style whenever the actual glyph line is taller than its even share of [GridItem.spanY]'s
     * allotted rows - `time_size` alone (50-66sp depending on density bucket) needs close to two
     * full 48dp rows by itself. WRAP_CONTENT means each line only ever takes what it actually
     * needs, so a spanY estimate that runs slightly generous just leaves blank space below the
     * block instead of squeezing text - the failure mode this deliberately avoids is clipping, not
     * a few dp of unused space.
     */
    private fun dateTimeLineParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    /**
     * Wraps a hosted widget so a long press on it can still select it for editing.
     *
     * A widget's own RemoteViews content is normally clickable, so it consumes the whole touch
     * stream before the cell's OnTouchListener is ever consulted - confirmed on-device, where
     * long-pressing the battery widget opened Settings instead of entering edit mode. This container
     * therefore times the press itself and, once it qualifies, intercepts the gesture (which
     * cancels the widget's own pending click) and enters edit mode. Same shape as Launcher3's
     * CheckLongPressHelper. Non-long presses are untouched, so a widget's own buttons keep working.
     */
    private inner class WidgetCellContainer(context: Context, private val item: GridItem) :
        FrameLayout(context) {

        private var downX = 0f
        private var downY = 0f
        private var pending = false
        private var triggered = false

        private val longPressCheck = Runnable {
            pending = false
            triggered = true
            enterEditMode(item)
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x
                    downY = ev.y
                    triggered = false
                    pending = true
                    postDelayed(longPressCheck, widgetLongPressTimeoutMs)
                }

                MotionEvent.ACTION_MOVE ->
                    if (hypot(ev.x - downX, ev.y - downY) > touchSlopPx) cancelPendingLongPress()

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelPendingLongPress()
            }
            return triggered
        }

        override fun onTouchEvent(event: MotionEvent): Boolean = triggered || super.onTouchEvent(event)

        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            // A scrollable widget claiming the gesture for itself also gives up the long press.
            if (disallowIntercept) cancelPendingLongPress()
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }

        override fun onDetachedFromWindow() {
            cancelPendingLongPress()
            super.onDetachedFromWindow()
        }

        private fun cancelPendingLongPress() {
            if (!pending) return
            pending = false
            removeCallbacks(longPressCheck)
        }
    }

    /**
     * Stands in for a widget that can't be hosted - almost always because the provider's app was
     * uninstalled or disabled while the widget was pinned, which leaves a [GridItem] pointing at an
     * id nothing answers for any more.
     *
     * Deliberately a plain (non-clickable) TextView rather than something with its own click
     * listener: [rebuildChildren] puts the caller-supplied per-cell touch listener on this very
     * view, and a click listener here would never see the gesture anyway. The tap-to-remove
     * behavior the label promises is therefore resolved by that listener (see
     * HomeFragment.cellTouchListenerFor), which keeps every other cell gesture - swipe to change
     * page, long-press to enter edit mode - working on this cell exactly like any other.
     */
    private fun widgetPlaceholderView(customTypeface: Typeface?): View =
        neutralPlaceholderView(R.string.widget_unavailable, customTypeface)

    /**
     * Generic neutral-grey box used for any [GridItem] type that doesn't have real rendering yet
     * (or, for WIDGET, can't currently be hosted) - same visual language for all of them so an
     * unfinished/unavailable cell always reads the same way rather than each type inventing its
     * own look.
     */
    private fun neutralPlaceholderView(@androidx.annotation.StringRes textRes: Int, customTypeface: Typeface?): View =
        TextView(context).apply {
            setTextAppearance(R.style.TextSmall)
            textSize = PLACEHOLDER_TEXT_SIZE_SP
            text = context.getString(textRes)
            gravity = Gravity.CENTER
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(PLACEHOLDER_PADDING_DP.dpToPx())
            if (customTypeface != null) setTypeface(customTypeface, typeface?.style ?: Typeface.NORMAL)
            background = GradientDrawable().apply {
                setColor(WIDGET_PLACEHOLDER_COLOR)
                cornerRadius = CORNER_RADIUS_DP.dpToPx().toFloat()
            }
        }

    // region edit mode

    private fun viewForItem(item: GridItem): View? {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val params = child.layoutParams as? LayoutParams ?: continue
            if (params.leftMargin == gridOffsetX + item.col * cellSizePx &&
                params.topMargin == gridOffsetY + item.row * cellSizePx
            ) {
                return child
            }
        }
        return null
    }

    private fun removeEditChrome() {
        scrimView?.let { removeView(it) }
        gridLinesView?.let { removeView(it) }
        previewView?.let { removeView(it) }
        overlayView?.let { removeView(it) }
        scrimView = null
        gridLinesView = null
        previewView = null
        overlayView = null
    }

    private fun addEditChrome() {
        val item = editingItem ?: return
        if (editingItemView == null) editingItemView = viewForItem(item)

        // Full-grid scrim: swallows every touch that doesn't land on the item's overlay, so a stray
        // touch during editing can never reach a cell's swipe/tap listener (and therefore can't
        // switch pages or launch an app). A tap on it is the "done" affordance.
        val scrim = View(context).apply {
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) exitEditMode()
                true
            }
        }
        addView(scrim, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        scrimView = scrim

        // Full-grid cell boundaries: purely visual reference for the whole page's cell layout
        // while editing, not just the occupied cells. Sits above the scrim but below the snap
        // preview/overlay (added next), and never gets a touch listener, so it can't affect the
        // scrim's exit-on-tap behavior or the overlay's move/resize handling above it.
        val gridLines = GridLinesView(context)
        addView(gridLines, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        gridLinesView = gridLines

        // Snap preview: shown only while a drag is in progress, at the cell range the item would
        // land on / grow to, tinted to say whether that target is accepted or rejected.
        val preview = View(context).apply { visibility = View.GONE }
        addView(preview, LayoutParams(cellSizePx, cellSizePx))
        previewView = preview

        val overlay = buildOverlay(item)
        addView(overlay, cellParams(item.col, item.row, item.spanX, item.spanY))
        overlayView = overlay
    }

    private fun cellParams(col: Int, row: Int, spanX: Int, spanY: Int): LayoutParams =
        LayoutParams(spanX * cellSizePx, spanY * cellSizePx).apply {
            leftMargin = gridOffsetX + col * cellSizePx
            topMargin = gridOffsetY + row * cellSizePx
        }

    /**
     * Draws the boundary of every cell across the whole grid (not just occupied ones) for the
     * duration of edit mode, so a drag/resize target can be judged against the whole page. Reads
     * [columnCount]/[rowCount]/[cellSizePx]/[gridOffsetX]/[gridOffsetY] straight from the outer
     * view on every draw, so it always reflects the current geometry with no invalidation of its
     * own to manage - [addEditChrome] rebuilds this view fresh whenever geometry could have
     * changed. Never given a touch listener (matches [previewView]'s non-interactive chrome), so
     * it cannot affect touch dispatch to the scrim or overlay around it.
     */
    private inner class GridLinesView(context: Context) : View(context) {
        private val paint = Paint().apply {
            color = withAlpha(chromeColor(), GRID_LINE_ALPHA)
            style = Paint.Style.STROKE
            strokeWidth = GRID_LINE_WIDTH_DP.dpToPx().toFloat()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (columnCount <= 0 || rowCount <= 0) return
            val left = gridOffsetX.toFloat()
            val top = gridOffsetY.toFloat()
            val right = (gridOffsetX + columnCount * cellSizePx).toFloat()
            val bottom = (gridOffsetY + rowCount * cellSizePx).toFloat()
            for (col in 0..columnCount) {
                val x = (gridOffsetX + col * cellSizePx).toFloat()
                canvas.drawLine(x, top, x, bottom, paint)
            }
            for (row in 0..rowCount) {
                val y = (gridOffsetY + row * cellSizePx).toFloat()
                canvas.drawLine(left, y, right, y, paint)
            }
        }
    }

    private fun buildOverlay(item: GridItem): FrameLayout {
        val chrome = chromeColor()
        val overlay = FrameLayout(context)
        overlay.background = GradientDrawable().apply {
            setColor(withAlpha(chrome, EDIT_FILL_ALPHA))
            setStroke(STROKE_WIDTH_DP.dpToPx(), chrome)
            cornerRadius = CORNER_RADIUS_DP.dpToPx().toFloat()
        }
        overlay.setOnTouchListener(moveTouchListener(item))

        // Delete badge, top-start: the corner deliberately left free by the two resize handles
        // (which sit on the end and bottom edges). Reuses the caller's existing remove path.
        val delete = TextView(context).apply {
            text = DELETE_GLYPH
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(inverseChromeColor())
            textSize = BADGE_TEXT_SIZE_SP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(chrome)
            }
            setOnClickListener {
                val target = editingItem
                exitEditMode()
                if (target != null) onItemDeleted?.invoke(target)
            }
        }
        overlay.addView(
            delete,
            LayoutParams(BADGE_SIZE_DP.dpToPx(), BADGE_SIZE_DP.dpToPx(), Gravity.START or Gravity.TOP)
        )

        // Settings badge, bottom-start: the last corner still free once the delete badge (top-start)
        // and the resize handles (end edge / bottom edge) have their places. Only the item types
        // that actually have per-item settings get one - see [hasSettings]. Same construction as the
        // delete badge above, a different glyph apart: two identical-looking badges a corner apart
        // would be easy to hit by mistake, and one of them deletes the item.
        if (hasSettings(item)) {
            val settings = TextView(context).apply {
                text = SETTINGS_GLYPH
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(inverseChromeColor())
                textSize = BADGE_TEXT_SIZE_SP
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(chrome)
                }
                setOnClickListener {
                    val target = editingItem
                    // Edit mode ends first, exactly like the delete badge: applying the settings
                    // re-reads and re-renders the page, which drops edit mode anyway (a rebind
                    // hands this view fresh GridItem instances - see setItems).
                    exitEditMode()
                    if (target != null) onOpenSettings?.invoke(target)
                }
            }
            overlay.addView(
                settings,
                LayoutParams(BADGE_SIZE_DP.dpToPx(), BADGE_SIZE_DP.dpToPx(), Gravity.START or Gravity.BOTTOM)
            )
        }

        val constraints = resizeConstraints(item)
        if (constraints.canResizeHorizontally) {
            overlay.addView(
                handleView(item = item, horizontal = true),
                LayoutParams(
                    HANDLE_SIZE_DP.dpToPx(),
                    HANDLE_SIZE_DP.dpToPx(),
                    Gravity.END or Gravity.CENTER_VERTICAL
                )
            )
        }
        if (constraints.canResizeVertically) {
            overlay.addView(
                handleView(item = item, horizontal = false),
                LayoutParams(
                    HANDLE_SIZE_DP.dpToPx(),
                    HANDLE_SIZE_DP.dpToPx(),
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                )
            )
        }
        return overlay
    }

    /**
     * Whether [item]'s type has per-item settings worth a gear badge in edit mode.
     *
     * True for the three types this launcher renders itself and therefore owns the appearance of:
     * APP_LIST (alignment, how many slots), DATE_TIME (alignment) and CLOCK (alignment). False for
     * WIDGET - a hosted widget's settings belong to its provider, not to us - and for the legacy
     * APP type, which no longer renders or gets created at all (Step 6/9); the branch is kept so
     * this stays total over the enum rather than relying on an else.
     */
    private fun hasSettings(item: GridItem): Boolean = when (item.type) {
        GridItemType.APP_LIST, GridItemType.DATE_TIME -> true
        // CLOCK has settings too - alignment, and only alignment
        // (HomeFragment.showClockSettings).
        GridItemType.CLOCK -> true
        GridItemType.APP, GridItemType.WIDGET -> false
    }

    /**
     * A resize grip: a generous (touch-sized) transparent view with a small dot drawn inside it, so
     * the handle is easy to hit without visually dominating a single cell.
     */
    private fun handleView(item: GridItem, horizontal: Boolean): View =
        View(context).apply {
            background = InsetDrawable(
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(chromeColor())
                },
                ((HANDLE_SIZE_DP - HANDLE_DOT_SIZE_DP) / 2).dpToPx()
            )
            setOnTouchListener(resizeTouchListener(item, horizontal = horizontal))
        }

    /**
     * Drag anywhere on the item to reposition it: the item and its overlay follow the finger, while
     * the snap preview shows the whole-cell target the release would commit to. Nothing is written
     * until ACTION_UP, and a target that leaves the grid or overlaps another item is rejected, so
     * the item simply snaps back to where it started (no push/displace behavior - see Step 19).
     */
    private fun moveTouchListener(item: GridItem): OnTouchListener {
        var downX = 0f
        var downY = 0f
        var dragging = false
        var targetCol = item.col
        var targetRow = item.row
        return OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    dragging = false
                    targetCol = item.col
                    targetRow = item.row
                    parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && hypot(dx, dy) > touchSlopPx) dragging = true
                    if (dragging) {
                        view.translationX = dx
                        view.translationY = dy
                        editingItemView?.let {
                            it.translationX = dx
                            it.translationY = dy
                        }
                        targetCol = snapSpan(item.col * cellSizePx + dx, columnCount - item.spanX)
                        targetRow = snapSpan(item.row * cellSizePx + dy, rowCount - item.spanY)
                        showPreview(
                            targetCol, targetRow, item.spanX, item.spanY,
                            fits(item, targetCol, targetRow, item.spanX, item.spanY)
                        )
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.translationX = 0f
                    view.translationY = 0f
                    editingItemView?.let {
                        it.translationX = 0f
                        it.translationY = 0f
                    }
                    hidePreview()
                    if (dragging && event.actionMasked == MotionEvent.ACTION_UP) {
                        commitGeometry(item, targetCol, targetRow, item.spanX, item.spanY)
                    }
                    dragging = false
                    true
                }

                else -> false
            }
        }
    }

    /**
     * Drag the end/bottom grip to change [GridItem.spanX]/[GridItem.spanY] in whole-cell steps.
     * The item itself stays put while dragging; only the snap preview grows/shrinks, which keeps a
     * hosted widget from being remeasured on every touch move. Same commit-on-release and
     * reject-on-overlap rules as a move.
     */
    private fun resizeTouchListener(item: GridItem, horizontal: Boolean): OnTouchListener {
        var down = 0f
        var dragging = false
        var targetSpanX = item.spanX
        var targetSpanY = item.spanY
        return OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    down = if (horizontal) event.rawX else event.rawY
                    dragging = false
                    targetSpanX = item.spanX
                    targetSpanY = item.spanY
                    parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val delta = (if (horizontal) event.rawX else event.rawY) - down
                    if (!dragging && abs(delta) > touchSlopPx) dragging = true
                    if (dragging) {
                        val constraints = resizeConstraints(item)
                        if (horizontal) {
                            targetSpanX = clampSpan(
                                (item.spanX * cellSizePx + delta) / cellSizePx,
                                constraints.minSpanX,
                                minOf(constraints.maxSpanX, columnCount - item.col)
                            )
                        } else {
                            targetSpanY = clampSpan(
                                (item.spanY * cellSizePx + delta) / cellSizePx,
                                constraints.minSpanY,
                                minOf(constraints.maxSpanY, rowCount - item.row)
                            )
                        }
                        showPreview(
                            item.col, item.row, targetSpanX, targetSpanY,
                            fits(item, item.col, item.row, targetSpanX, targetSpanY)
                        )
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    hidePreview()
                    if (dragging && event.actionMasked == MotionEvent.ACTION_UP) {
                        commitGeometry(item, item.col, item.row, targetSpanX, targetSpanY)
                    }
                    dragging = false
                    true
                }

                else -> false
            }
        }
    }

    /** Nearest whole-cell index for a pixel offset, clamped to the grid. */
    private fun snapSpan(offsetPx: Float, maxIndex: Int): Int =
        (offsetPx / cellSizePx).roundToInt().coerceIn(0, maxIndex.coerceAtLeast(0))

    private fun clampSpan(rawSpan: Float, min: Int, max: Int): Int =
        rawSpan.roundToInt().coerceIn(min, maxOf(min, max))

    /** True if [item] at the given position/span stays on the grid and touches no other item. */
    private fun fits(item: GridItem, col: Int, row: Int, spanX: Int, spanY: Int): Boolean {
        if (col < 0 || row < 0 || spanX < 1 || spanY < 1) return false
        if (col + spanX > columnCount || row + spanY > rowCount) return false
        return items.none { other ->
            other !== item &&
                col < other.col + other.spanX && other.col < col + spanX &&
                row < other.row + other.spanY && other.row < row + spanY
        }
    }

    /**
     * Applies a move/resize if it is legal, and does nothing at all if it isn't - the caller has
     * already reset the dragged views' translation, so a rejected drag reads as "snapped back".
     */
    private fun commitGeometry(item: GridItem, col: Int, row: Int, spanX: Int, spanY: Int) {
        if (col == item.col && row == item.row && spanX == item.spanX && spanY == item.spanY) return
        if (!fits(item, col, row, spanX, spanY)) return

        item.col = col
        item.row = row
        item.spanX = spanX
        item.spanY = spanY
        onItemsChanged?.invoke(items)
        if (item.type == GridItemType.WIDGET) notifyWidgetResized(item)

        // Move the already-attached views straight away so the commit is visible on this frame,
        // then rebuild for real: cell coverage (and therefore which empty cells exist, and the
        // child z-order the touch dispatch depends on) changed, and only a rebuild fixes that up.
        // isAttachedToWindow guard: see onSizeChanged's kdoc.
        editingItemView?.layoutParams = cellParams(col, row, spanX, spanY)
        overlayView?.layoutParams = cellParams(col, row, spanX, spanY)
        post { if (isAttachedToWindow) rebuildChildren() }
    }

    /**
     * Tells the provider its widget's new size. Without this the hosted view resizes but the
     * widget's own content never reflows (e.g. a calendar widget keeps showing three rows in a
     * cell now tall enough for six).
     */
    private fun notifyWidgetResized(item: GridItem) {
        val appWidgetId = item.appWidgetId ?: return
        val widthDp = item.spanX * Constants.Grid.CELL_SIZE_DP
        val heightDp = item.spanY * Constants.Grid.CELL_SIZE_DP
        WidgetHostManager.updateWidgetSize(context, appWidgetId, widthDp, heightDp, widthDp, heightDp)
    }

    private fun showPreview(col: Int, row: Int, spanX: Int, spanY: Int, valid: Boolean) {
        val preview = previewView ?: return
        val stroke = if (valid) chromeColor() else INVALID_TARGET_COLOR
        preview.background = GradientDrawable().apply {
            setColor(withAlpha(stroke, PREVIEW_FILL_ALPHA))
            setStroke(STROKE_WIDTH_DP.dpToPx(), stroke)
            cornerRadius = CORNER_RADIUS_DP.dpToPx().toFloat()
        }
        preview.layoutParams = cellParams(col, row, spanX, spanY)
        preview.visibility = View.VISIBLE
    }

    private fun hidePreview() {
        previewView?.visibility = View.GONE
    }

    private data class ResizeConstraints(
        val canResizeHorizontally: Boolean,
        val canResizeVertically: Boolean,
        val minSpanX: Int,
        val minSpanY: Int,
        val maxSpanX: Int,
        val maxSpanY: Int,
    )

    /**
     * What the item is allowed to be resized to, in whole cells.
     *
     * App shortcuts have no provider to ask, so anything from 1x1 up to the whole page is fine.
     *
     * APP_LIST, DATE_TIME and CLOCK resize horizontally only. Their height is content-driven - one
     * row per app slot, or the date/screen-time (DATE_TIME) or clock (CLOCK) block's own line count
     * - so it follows from their settings dialog (HomeFragment.showAppListSettings/showDateTimeSettings),
     * and letting a vertical drag contradict that would just produce squeezed or half-empty items
     * that the next settings change silently undoes. `canResizeVertically = false` also means [buildOverlay] never
     * adds a bottom handle for them, the same way it skips an axis a widget's provider disallows.
     *
     * Widgets are clamped to what their provider declared: [AppWidgetProviderInfo.resizeMode] gates
     * each axis entirely (a handle for a disallowed axis is never even added), minResizeWidth/Height
     * give the lower bound - rounded *up*, so the widget is never handed less room than it asked
     * for - falling back to minWidth/minHeight when unset, and maxResizeWidth/Height (API 31+) give
     * the upper bound, rounded *down*, defaulting to the page's own grid size.
     */
    private fun resizeConstraints(item: GridItem): ResizeConstraints {
        val unconstrained = ResizeConstraints(
            canResizeHorizontally = true,
            canResizeVertically = true,
            minSpanX = 1,
            minSpanY = 1,
            maxSpanX = columnCount.coerceAtLeast(1),
            maxSpanY = rowCount.coerceAtLeast(1),
        )
        if (item.type == GridItemType.APP_LIST || item.type == GridItemType.DATE_TIME || item.type == GridItemType.CLOCK) {
            val maxSpanX = columnCount.coerceAtLeast(1)
            return ResizeConstraints(
                canResizeHorizontally = true,
                canResizeVertically = false,
                minSpanX = MIN_TEXT_ITEM_SPAN_X.coerceAtMost(maxSpanX),
                // Height is fixed at whatever the item currently is: no vertical handle is drawn,
                // so these only exist to keep a stray span calculation from moving it.
                minSpanY = item.spanY,
                maxSpanX = maxSpanX,
                maxSpanY = item.spanY,
            )
        }
        if (item.type != GridItemType.WIDGET) return unconstrained
        val appWidgetId = item.appWidgetId ?: return unconstrained
        val info = WidgetHostManager.providerInfoFor(context, appWidgetId) ?: return unconstrained

        val minSpanX = spansForPx(
            if (info.minResizeWidth > 0) info.minResizeWidth else info.minWidth,
            roundUp = true
        ).coerceIn(1, columnCount.coerceAtLeast(1))
        val minSpanY = spansForPx(
            if (info.minResizeHeight > 0) info.minResizeHeight else info.minHeight,
            roundUp = true
        ).coerceIn(1, rowCount.coerceAtLeast(1))

        var maxSpanX = columnCount.coerceAtLeast(1)
        var maxSpanY = rowCount.coerceAtLeast(1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (info.maxResizeWidth > 0) {
                maxSpanX = spansForPx(info.maxResizeWidth, roundUp = false).coerceIn(1, maxSpanX)
            }
            if (info.maxResizeHeight > 0) {
                maxSpanY = spansForPx(info.maxResizeHeight, roundUp = false).coerceIn(1, maxSpanY)
            }
        }

        return ResizeConstraints(
            canResizeHorizontally = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
            canResizeVertically = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
            minSpanX = minSpanX,
            minSpanY = minSpanY,
            maxSpanX = maxOf(minSpanX, maxSpanX),
            maxSpanY = maxOf(minSpanY, maxSpanY),
        )
    }

    /** AppWidgetProviderInfo's dimensions are px; the grid counts whole cells. */
    private fun spansForPx(dimensionPx: Int, roundUp: Boolean): Int {
        if (dimensionPx <= 0) return 1
        val cells = dimensionPx.toFloat() / cellSizePx
        return (if (roundUp) ceil(cells) else floor(cells)).toInt().coerceAtLeast(1)
    }

    private fun chromeColor(): Int = context.getColorFromAttr(R.attr.primaryColor)

    private fun inverseChromeColor(): Int = context.getColorFromAttr(R.attr.primaryInverseColor)

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    // endregion

    companion object {
        private const val WIDGET_PLACEHOLDER_COLOR = 0x33808080 // neutral, semi-transparent grey

        // Dimming for an App List slot whose app is gone - same "inactive" strength the page
        // indicator's unselected dots use, so nothing new is invented for it.
        private const val UNAVAILABLE_SLOT_ALPHA = 0.4f
        private const val PLACEHOLDER_TEXT_SIZE_SP = 11f // has to read inside one cell
        private const val PLACEHOLDER_PADDING_DP = 4

        private const val STROKE_WIDTH_DP = 2
        private const val CORNER_RADIUS_DP = 4
        private const val BADGE_SIZE_DP = 28
        private const val BADGE_TEXT_SIZE_SP = 16f
        private const val HANDLE_SIZE_DP = 36 // touch target
        private const val HANDLE_DOT_SIZE_DP = 14 // what's actually drawn
        private const val DELETE_GLYPH = "×"
        private const val SETTINGS_GLYPH = "⚙"

        /**
         * Narrowest an APP_LIST/DATE_TIME item may be dragged to, in cells. At 48dp cells one cell
         * is not enough for a single line of an app name or a date to read as anything but
         * truncated, so two is the floor - the item's own alignment/content settings are what its
         * width is for, not fitting text into one cell.
         */
        private const val MIN_TEXT_ITEM_SPAN_X = 2
        private const val EDIT_FILL_ALPHA = 0x1A
        private const val PREVIEW_FILL_ALPHA = 0x33
        private const val INVALID_TARGET_COLOR = 0xFFE53935.toInt() // only used to say "won't fit"
        private const val GRID_LINE_WIDTH_DP = 1
        private const val GRID_LINE_ALPHA = 0x33 // subtle - same tint strength as the preview fill
    }
}
