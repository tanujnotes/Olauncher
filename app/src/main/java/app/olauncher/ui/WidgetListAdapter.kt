package app.olauncher.ui

import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import app.olauncher.databinding.AdapterWidgetItemBinding

data class WidgetListItem(
    val appWidgetId: Int? = null,
    val providerInfo: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    val preview: Drawable?,
)

class WidgetListAdapter(
    private val onClick: (WidgetListItem) -> Unit,
) : RecyclerView.Adapter<WidgetListAdapter.WidgetItemViewHolder>() {
    private var items: List<WidgetListItem> = emptyList()

    fun submitList(value: List<WidgetListItem>) {
        val oldItems = items
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldItems.size
            override fun getNewListSize() = value.size
            override fun areItemsTheSame(oldPosition: Int, newPosition: Int): Boolean {
                val old = oldItems[oldPosition]
                val new = value[newPosition]
                return old.appWidgetId?.let { it == new.appWidgetId }
                    ?: (new.appWidgetId == null && old.providerInfo.provider == new.providerInfo.provider)
            }

            override fun areContentsTheSame(oldPosition: Int, newPosition: Int): Boolean =
                oldItems[oldPosition].label == value[newPosition].label &&
                    oldItems[oldPosition].appLabel == value[newPosition].appLabel
        })
        items = value
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetItemViewHolder =
        WidgetItemViewHolder(
            AdapterWidgetItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: WidgetItemViewHolder, position: Int) {
        holder.bind(items[position], onClick)
    }

    class WidgetItemViewHolder(
        private val binding: AdapterWidgetItemBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: WidgetListItem, onClick: (WidgetListItem) -> Unit) {
            binding.label.text = item.label
            binding.appLabel.text = item.appLabel
            binding.preview.setImageDrawable(item.preview)
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}
