package app.olauncher.ui

import android.util.SparseArray
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.util.forEach
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import app.olauncher.helper.WidgetHostManager

class WidgetPagerAdapter(
    private val widgetHostManager: WidgetHostManager,
    private val onHostViewReady: (android.appwidget.AppWidgetHostView) -> Unit,
) : RecyclerView.Adapter<WidgetPagerAdapter.WidgetViewHolder>() {
    private var widgetIds: List<Int> = emptyList()
    private val hostViews = SparseArray<android.appwidget.AppWidgetHostView>()

    init {
        setHasStableIds(true)
    }

    fun submitList(ids: List<Int>) {
        val oldIds = widgetIds
        val removedIds = oldIds.filterNot(ids::contains)
        removedIds.forEach { id ->
            hostViews[id]?.let { (it.parent as? ViewGroup)?.removeView(it) }
            hostViews.remove(id)
        }
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldIds.size
            override fun getNewListSize() = ids.size
            override fun areItemsTheSame(oldPosition: Int, newPosition: Int) =
                oldIds[oldPosition] == ids[newPosition]

            override fun areContentsTheSame(oldPosition: Int, newPosition: Int) =
                areItemsTheSame(oldPosition, newPosition)
        })
        widgetIds = ids
        diff.dispatchUpdatesTo(this)
    }

    fun updateAllSizes(widthPx: Int, heightPx: Int) {
        hostViews.forEach { _, view -> widgetHostManager.updateSize(view, widthPx, heightPx) }
    }

    fun clear() {
        hostViews.forEach { _, view -> (view.parent as? ViewGroup)?.removeView(view) }
        hostViews.clear()
    }

    override fun getItemCount(): Int = widgetIds.size

    override fun getItemId(position: Int): Long = widgetIds[position].toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetViewHolder {
        val container = FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        return WidgetViewHolder(container)
    }

    override fun onBindViewHolder(holder: WidgetViewHolder, position: Int) {
        val id = widgetIds[position]
        val hostView = hostViews[id] ?: widgetHostManager.createView(holder.container.context, id)
            ?.also { hostViews.put(id, it) }
            ?: return
        (hostView.parent as? ViewGroup)?.removeView(hostView)
        holder.container.removeAllViews()
        holder.container.addView(
            hostView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        hostView.post { onHostViewReady(hostView) }
    }

    class WidgetViewHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container)
}
