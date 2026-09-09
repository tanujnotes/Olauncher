package app.elauncher.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.setPadding
import app.elauncher.R
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

    private val cellSizePx: Int = (CELL_SIZE_DP * resources.displayMetrics.density).toInt()
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

    private var items: List<GridItem> = emptyList()
    private var touchListenerFor: ((col: Int, row: Int, item: GridItem?) -> View.OnTouchListener)? = null
    private var onItemsChanged: ((List<GridItem>) -> Unit)? = null
    private var onItemDeleted: ((GridItem) -> Unit)? = null

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
    private var previewView: View? = null
    private var overlayView: View? = null

    /**
     * Clears and re-adds one child view per occupied cell plus one invisible/transparent view
     * per unoccupied cell (so empty cells are addressable too). [touchListenerFor] is called
     * for every cell, occupied or empty, and its result is set via [View.setOnTouchListener] -
     * this view has no click/long-click/swipe logic of its own.
     *
     * [onItemsChanged] is invoked with the (mutated in place) item list after an edit-mode move or
     * resize commits, and [onItemDeleted] when the edit-mode delete badge is tapped; both exist so
     * that persistence stays with the caller, exactly like the gesture callbacks above.
     */
    fun setItems(
        items: List<GridItem>,
        touchListenerFor: (col: Int, row: Int, item: GridItem?) -> View.OnTouchListener,
        onItemsChanged: ((List<GridItem>) -> Unit)? = null,
        onItemDeleted: ((GridItem) -> Unit)? = null,
    ) {
        this.items = items
        this.touchListenerFor = touchListenerFor
        this.onItemsChanged = onItemsChanged
        this.onItemDeleted = onItemDeleted
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
            onItemsChanged?.invoke(items)
            post { rebuildChildren() }
            return
        }
        editingItemView = viewForItem(item)
        addEditChrome()
    }

    /**
     * Pulls [item] inside the grid, returning true if anything had to change.
     *
     * App items are created spanning `Prefs.defaultColumnCount()` cells, which is derived from the
     * raw screen width and so can be one cell wider than this grid actually is once
     * item_home_page.xml's side margins are taken off - a pre-existing mismatch, harmless until an
     * item has to satisfy the bounds check below.
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
        post { rebuildChildren() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        recomputeGeometry(measuredWidth, measuredHeight)
    }

    private fun recomputeGeometry(width: Int, height: Int) {
        if (cellSizePx <= 0) return
        columnCount = floor(width.toFloat() / cellSizePx).toInt()
        rowCount = floor(height.toFloat() / cellSizePx).toInt()
    }

    private fun rebuildChildren() {
        val touchListenerFor = this.touchListenerFor ?: return
        if (columnCount <= 0 || rowCount <= 0) return

        // Every cell view - hosted widget views included - is created fresh below, so nothing is
        // ever carried over from a previous bind of this (possibly recycled) page view.
        removeAllViews()
        scrimView = null
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
                val params = LayoutParams(spanX * cellSizePx, spanY * cellSizePx)
                params.leftMargin = col * cellSizePx
                params.topMargin = row * cellSizePx
                addView(cellView, params)
            }
        }

        if (editingItem != null) addEditChrome()
    }

    private fun createCellView(item: GridItem?, customTypeface: Typeface?): View {
        return when (item?.type) {
            GridItemType.APP -> TextView(context).apply {
                setTextAppearance(R.style.TextLarge)
                text = item.appName
                ellipsize = android.text.TextUtils.TruncateAt.END
                isSingleLine = true
                if (customTypeface != null) setTypeface(customTypeface, typeface?.style ?: Typeface.NORMAL)
            }
            GridItemType.WIDGET -> createWidgetView(item, customTypeface)
            null -> View(context).apply {
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
    private fun widgetPlaceholderView(customTypeface: Typeface?): View = TextView(context).apply {
        setTextAppearance(R.style.TextSmall)
        textSize = PLACEHOLDER_TEXT_SIZE_SP
        text = context.getString(R.string.widget_unavailable)
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
            if (params.leftMargin == item.col * cellSizePx && params.topMargin == item.row * cellSizePx) {
                return child
            }
        }
        return null
    }

    private fun removeEditChrome() {
        scrimView?.let { removeView(it) }
        previewView?.let { removeView(it) }
        overlayView?.let { removeView(it) }
        scrimView = null
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
            leftMargin = col * cellSizePx
            topMargin = row * cellSizePx
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
     * A resize grip: a generous (touch-sized) transparent view with a small dot drawn inside it, so
     * the handle is easy to hit without visually dominating an 80dp cell.
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
        editingItemView?.layoutParams = cellParams(col, row, spanX, spanY)
        overlayView?.layoutParams = cellParams(col, row, spanX, spanY)
        post { rebuildChildren() }
    }

    /**
     * Tells the provider its widget's new size. Without this the hosted view resizes but the
     * widget's own content never reflows (e.g. a calendar widget keeps showing three rows in a
     * cell now tall enough for six).
     */
    private fun notifyWidgetResized(item: GridItem) {
        val appWidgetId = item.appWidgetId ?: return
        val widthDp = item.spanX * CELL_SIZE_DP
        val heightDp = item.spanY * CELL_SIZE_DP
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

    /** AppWidgetProviderInfo's dimensions are px; the grid counts 80dp cells. */
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
        private const val CELL_SIZE_DP = 80
        private const val WIDGET_PLACEHOLDER_COLOR = 0x33808080 // neutral, semi-transparent grey
        private const val PLACEHOLDER_TEXT_SIZE_SP = 11f // has to read inside one 80dp cell
        private const val PLACEHOLDER_PADDING_DP = 4

        private const val STROKE_WIDTH_DP = 2
        private const val CORNER_RADIUS_DP = 4
        private const val BADGE_SIZE_DP = 28
        private const val BADGE_TEXT_SIZE_SP = 16f
        private const val HANDLE_SIZE_DP = 36 // touch target
        private const val HANDLE_DOT_SIZE_DP = 14 // what's actually drawn
        private const val DELETE_GLYPH = "×"
        private const val EDIT_FILL_ALPHA = 0x1A
        private const val PREVIEW_FILL_ALPHA = 0x33
        private const val INVALID_TARGET_COLOR = 0xFFE53935.toInt() // only used to say "won't fit"
    }
}
