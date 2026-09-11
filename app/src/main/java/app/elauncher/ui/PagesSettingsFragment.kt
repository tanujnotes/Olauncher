package app.elauncher.ui

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import androidx.recyclerview.widget.LinearLayoutManager
import app.elauncher.R
import app.elauncher.data.Page
import app.elauncher.data.Prefs
import app.elauncher.data.newPageDefaultItems
import app.elauncher.databinding.FragmentPagesSettingsBinding
import app.elauncher.helper.FontManager
import app.elauncher.helper.themedBackgroundColor
import java.util.UUID

/**
 * Managed list of home-screen pages: add, rename, reorder (up/down) and remove. Every mutation
 * is written straight back to [Prefs.pages] - the home screen's ViewPager2/dot indicator
 * (Step 10's HomePagerAdapter + HomeFragment.refreshPager) already picks this up on resume, so
 * this fragment does not need to talk to HomeFragment directly.
 */
class PagesSettingsFragment : BaseFragment() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: PagesAdapter
    private var pages: MutableList<Page> = mutableListOf()

    private var _binding: FragmentPagesSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPagesSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        binding.root.setBackgroundColor(requireContext().themedBackgroundColor(prefs.backgroundOpacity))
        pages = prefs.pages.toMutableList()

        adapter = PagesAdapter(
            onRename = { position -> showRenameDialog(position) },
            onMoveUp = { position -> movePage(position, position - 1) },
            onMoveDown = { position -> movePage(position, position + 1) },
            onDelete = { position -> confirmDeletePage(position) },
        )
        binding.pagesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.pagesRecyclerView.adapter = adapter
        adapter.submitList(pages)

        binding.addPage.setOnClickListener { addPage() }
    }

    private fun persistAndRefresh() {
        prefs.pages = pages
        adapter.submitList(pages)
    }

    private fun addPage() {
        // Seeded rather than blank: every page is meant to carry a Date & Screen Time item (the
        // fixed header it replaces was on every page), and an empty-slot App List gives the user
        // somewhere to put apps without first having to add a widget. See Page.newPageDefaultItems.
        val newPage = Page(
            id = UUID.randomUUID().toString(),
            name = getString(R.string.page_name_default, pages.size + 1),
            items = newPageDefaultItems(prefs.defaultColumnCount()),
        )
        pages.add(newPage)
        persistAndRefresh()
        binding.pagesRecyclerView.scrollToPosition(pages.size - 1)
    }

    private fun movePage(from: Int, to: Int) {
        if (to < 0 || to >= pages.size) return
        val page = pages.removeAt(from)
        pages.add(to, page)
        persistAndRefresh()
    }

    private fun confirmDeletePage(position: Int) {
        val page = pages.getOrNull(position) ?: return
        if (pages.size <= 1) return // always keep at least one page

        if (page.items.isEmpty()) {
            removePage(position)
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.remove_page_confirm_title)
            .setMessage(R.string.remove_page_confirm_message)
            .setPositiveButton(R.string.remove_page) { dialog, _ ->
                removePage(position)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
            .applyCustomFont()
    }

    private fun removePage(position: Int) {
        if (position < 0 || position >= pages.size || pages.size <= 1) return
        pages.removeAt(position)
        persistAndRefresh()
        // Prefs.currentPageIndex self-clamps to a valid index on every read (see Prefs.kt), so
        // no explicit clamping is needed here beyond having written the shorter `pages` list.
    }

    private fun showRenameDialog(position: Int) {
        val page = pages.getOrNull(position) ?: return
        val input = AppCompatEditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setText(page.name)
            setSelection(text?.length ?: 0)
            maxLines = 1
        }
        val paddingHorizontal = (20 * resources.displayMetrics.density).toInt()
        val inputContainer = FrameLayout(requireContext()).apply {
            setPadding(paddingHorizontal, 0, paddingHorizontal, 0)
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.rename_page)
            .setView(inputContainer)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val newName = input.text?.toString()?.trim().orEmpty()
                if (newName.isNotEmpty()) {
                    pages[position] = pages[position].copy(name = newName)
                    persistAndRefresh()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
            .applyCustomFont()
    }

    /**
     * Dialog content lives in its own window, outside this fragment's view tree, so
     * BaseFragment's typeface walk never reaches it. No-op unless a custom font is set.
     */
    private fun AlertDialog.applyCustomFont() {
        window?.decorView?.let { FontManager.applyCustomTypeface(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
