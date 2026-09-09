package app.elauncher.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.elauncher.databinding.ItemHomePageBinding
import app.elauncher.helper.FontManager

/**
 * Backs the home screen's ViewPager2 - one [ItemHomePageBinding] (clock/date/screen-time/grid)
 * per [app.elauncher.data.Page]. ViewPager2 is itself backed by a RecyclerView.Adapter, not a
 * dedicated ViewPager2.Adapter type. [pageCount]/[bindPage] are read fresh on every call so this
 * adapter always reflects the current [app.elauncher.data.Prefs.pages] without needing to be
 * rebuilt when pages are added/removed/reordered elsewhere (e.g. Settings) - callers just need to
 * call [RecyclerView.Adapter.notifyDataSetChanged] after such a change.
 */
class HomePagerAdapter(
    private val pageCount: () -> Int,
    private val bindPage: (binding: ItemHomePageBinding, pageIndex: Int) -> Unit
) : RecyclerView.Adapter<HomePagerAdapter.PageViewHolder>() {

    class PageViewHolder(val binding: ItemHomePageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemHomePageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        // Page layouts (clock/date/screen-time) are inflated on demand by this adapter, after
        // HomeFragment's own typeface walk already ran, so apply it per page here. The grid cells
        // inside are code-built and handle the custom font themselves (see HomeGridView).
        FontManager.applyCustomTypeface(holder.itemView)
        bindPage(holder.binding, position)
    }

    override fun getItemCount(): Int = pageCount()
}
