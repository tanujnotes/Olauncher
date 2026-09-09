package app.elauncher.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.elauncher.data.Page
import app.elauncher.databinding.ItemPageBinding
import app.elauncher.helper.FontManager

/**
 * Backs the RecyclerView in [PagesSettingsFragment]. Kept as a plain [RecyclerView.Adapter]
 * over a directly-mutable list (rather than [androidx.recyclerview.widget.ListAdapter] +
 * DiffUtil) since the pages settings list is short, always fully in memory, and every mutation
 * already comes from this adapter's own callbacks - matching the "simplest to build correctly"
 * guidance for this step.
 */
class PagesAdapter(
    private val onRename: (Int) -> Unit,
    private val onMoveUp: (Int) -> Unit,
    private val onMoveDown: (Int) -> Unit,
    private val onDelete: (Int) -> Unit,
) : RecyclerView.Adapter<PagesAdapter.ViewHolder>() {

    private var pages: List<Page> = emptyList()

    fun submitList(newPages: List<Page>) {
        pages = newPages
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val page = pages.getOrNull(position) ?: return
        // Rows are inflated on demand, after the fragment-level typeface walk; no-op without a
        // custom font.
        FontManager.applyCustomTypeface(holder.itemView)
        holder.bind(
            page = page,
            isFirst = position == 0,
            isLast = position == pages.size - 1,
            canDelete = pages.size > 1,
            onRename = { onRename(holder.bindingAdapterPosition) },
            onMoveUp = { onMoveUp(holder.bindingAdapterPosition) },
            onMoveDown = { onMoveDown(holder.bindingAdapterPosition) },
            onDelete = { onDelete(holder.bindingAdapterPosition) },
        )
    }

    override fun getItemCount(): Int = pages.size

    class ViewHolder(private val binding: ItemPageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            page: Page,
            isFirst: Boolean,
            isLast: Boolean,
            canDelete: Boolean,
            onRename: () -> Unit,
            onMoveUp: () -> Unit,
            onMoveDown: () -> Unit,
            onDelete: () -> Unit,
        ) = with(binding) {
            pageName.text = page.name
            pageName.setOnClickListener { onRename() }

            pageMoveUp.isEnabled = !isFirst
            pageMoveUp.alpha = if (isFirst) 0.3f else 1f
            pageMoveUp.setOnClickListener { onMoveUp() }

            pageMoveDown.isEnabled = !isLast
            pageMoveDown.alpha = if (isLast) 0.3f else 1f
            pageMoveDown.setOnClickListener { onMoveDown() }

            pageDelete.isEnabled = canDelete
            pageDelete.alpha = if (canDelete) 1f else 0.3f
            pageDelete.setOnClickListener { if (canDelete) onDelete() }
        }
    }
}
