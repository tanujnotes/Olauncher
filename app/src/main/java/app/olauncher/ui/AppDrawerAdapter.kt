package app.olauncher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.databinding.AdapterAppDrawerBinding
import app.olauncher.databinding.AdapterDrawerSectionBinding
import app.olauncher.helper.DrawerContact
import app.olauncher.helper.DrawerContactPhoto
import app.olauncher.helper.ContactAction
import app.olauncher.databinding.AdapterPrivateSpaceHeaderBinding
import app.olauncher.helper.appLabelMatcher
import app.olauncher.helper.appLabelRanking
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.showKeyboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppDrawerAdapter(
    private val flag: Int,
    private val appLabelGravity: Int,
    private val iconScope: CoroutineScope,
    private val isCurrentSearch: (String) -> Boolean,
    private val onSearchResultsApplied: (String) -> Unit,
    private val appClickListener: (AppModel) -> Unit,
    private val appInfoListener: (AppModel) -> Unit,
    private val appDeleteListener: (AppModel) -> Unit,
    private val appHideListener: (AppModel) -> Unit,
    private val appRenameListener: (AppModel, String) -> Unit,
    private val contactClickListener: (DrawerContact, ContactAction) -> Unit,
    private val privateSpaceToggleListener: () -> Unit = {},
    private val privateSpaceSettingsListener: () -> Unit = {},
) : ListAdapter<AppDrawerAdapter.Row, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    enum class SearchCategory { ALL, APPS, CONTACTS }

    sealed interface Row {
        data class App(val model: AppModel) : Row
        data class Contact(val model: DrawerContact) : Row
        enum class Section : Row { APPS, CONTACTS }
    }

    companion object {
        const val VIEW_TYPE_APP = 0
        const val VIEW_TYPE_PRIVATE_HEADER = 1

        private const val VIEW_TYPE_CONTACT = 2
        private const val VIEW_TYPE_SECTION = 3

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(oldItem: Row, newItem: Row): Boolean = when {
                oldItem is Row.App && newItem is Row.App -> sameApp(oldItem.model, newItem.model)
                oldItem is Row.Contact && newItem is Row.Contact -> oldItem.model.id == newItem.model.id
                else -> oldItem == newItem
            }

            private fun sameApp(oldItem: AppModel, newItem: AppModel): Boolean = when {
                oldItem is AppModel.App && newItem is AppModel.App ->
                    oldItem.appPackage == newItem.appPackage && oldItem.user == newItem.user &&
                        oldItem.activityClassName == newItem.activityClassName

                oldItem is AppModel.PinnedShortcut && newItem is AppModel.PinnedShortcut ->
                    oldItem.identity == newItem.identity

                oldItem is AppModel.PrivateSpaceHeader && newItem is AppModel.PrivateSpaceHeader -> true

                else -> false
            }

            override fun areContentsTheSame(oldItem: Row, newItem: Row): Boolean =
                oldItem == newItem
        }
    }

    private val appIcons = LruCache<AppModel, Drawable.ConstantState>(80)
    private val contactPhotos = LruCache<DrawerContactPhoto, Bitmap>(80)
    private val myUserHandle = android.os.Process.myUserHandle()
    private var apps: List<AppModel>? = null
    private var contacts: List<DrawerContact>? = emptyList()
    var searchCategory = SearchCategory.ALL
        private set
    var contactCommand: ContactAction? = null
    private var query = ""
    private var pendingSubmit: ((AppModel?) -> Unit)? = null
    private var filterJob: Job? = null
    private var filterGeneration = 0
    private var appliedGeneration = 0

    override fun getItemViewType(position: Int): Int {
        return when (val row = getItem(position)) {
            is Row.App -> if (row.model is AppModel.PrivateSpaceHeader) VIEW_TYPE_PRIVATE_HEADER else VIEW_TYPE_APP
            is Row.Contact -> VIEW_TYPE_CONTACT
            is Row.Section -> VIEW_TYPE_SECTION
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_CONTACT -> ContactViewHolder.create(parent)
            VIEW_TYPE_SECTION -> SectionViewHolder(
                AdapterDrawerSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            VIEW_TYPE_PRIVATE_HEADER -> PrivateSpaceHeaderViewHolder(
                AdapterPrivateSpaceHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            else -> ViewHolder(
                AdapterAppDrawerBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                showTiles = flag == Constants.FLAG_LAUNCH_APP,
            )
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is ViewHolder) holder.cancelIconLoad()
        if (holder is ContactViewHolder) holder.clearPhoto()
        super.onViewRecycled(holder)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        try {
            val row = getItem(position)
            when (holder) {
                is PrivateSpaceHeaderViewHolder -> {
                    holder.bind(
                        appLabelGravity,
                        privateSpaceToggleListener,
                        privateSpaceSettingsListener,
                    )
                }

                is SectionViewHolder -> holder.bind(row as Row.Section)
                is ContactViewHolder -> holder.bind((row as Row.Contact).model, iconScope, contactPhotos) { contact, action ->
                    if (appliedGeneration == filterGeneration && isCurrentSearch(query)) {
                        contactClickListener(contact, if (action == ContactAction.CALL) contactCommand ?: action else action)
                    }
                }

                is ViewHolder -> {
                    val appModel = (row as Row.App).model
                    holder.bind(
                        flag,
                        appLabelGravity,
                        myUserHandle,
                        appModel,
                        { app ->
                            if (appliedGeneration == filterGeneration && isCurrentSearch(query)) {
                                appClickListener(app)
                            }
                        },
                        appDeleteListener,
                        appInfoListener,
                        appHideListener,
                        appRenameListener,
                    )
                    if (flag == Constants.FLAG_LAUNCH_APP) holder.bindIcon(iconScope, appIcons, appModel)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateSearch(query: String) {
        this.query = query
        pendingSubmit = null
        filterApps()
    }

    fun setAppList(apps: List<AppModel>) {
        if (this.apps == apps) return
        this.apps = apps
        filterApps()
    }

    fun setContacts(contacts: List<DrawerContact>?) {
        if (this.contacts == contacts) return
        this.contacts = contacts
        filterApps()
    }

    fun setSearchCategory(category: SearchCategory) {
        if (searchCategory == category) return
        searchCategory = category
        cancelPendingSearchAction()
        filterApps()
    }

    fun cancelPendingSearchAction() {
        pendingSubmit = null
    }

    fun submitSearch(onResult: (AppModel?) -> Unit) {
        if (isSearchDataLoaded() && appliedGeneration == filterGeneration) {
            dispatchSearch(currentList, onResult)
        } else {
            pendingSubmit = onResult
        }
    }

    private fun isSearchDataLoaded() =
        (apps != null || searchCategory == SearchCategory.CONTACTS) &&
            (contacts != null || searchCategory == SearchCategory.APPS)

    private fun dispatchSearch(rows: List<Row>, onResult: (AppModel?) -> Unit) {
        val contactsOnly = searchCategory == SearchCategory.CONTACTS
        val app = rows.filterIsInstance<Row.App>().firstOrNull()?.model
        val contacts = rows.filterIsInstance<Row.Contact>()
        val contact = contacts.firstOrNull()?.model
        when {
            app != null -> onResult(app)
            contact != null -> contactClickListener(contact, contactCommand ?: ContactAction.OPEN)
            !contactsOnly -> onResult(null)
        }
    }

    private fun filterApps() {
        val category = searchCategory
        val contactsOnly = category == SearchCategory.CONTACTS
        val source = apps ?: if (category == SearchCategory.ALL) return else emptyList()
        val query = this.query
        val contacts = if (category == SearchCategory.APPS) emptyList() else this.contacts.orEmpty()
        val generation = ++filterGeneration
        filterJob?.cancel()
        if (query.isBlank() && !contactsOnly) {
            val items = if (flag == Constants.FLAG_LAUNCH_APP) source else source + AppModel.App(
                appLabel = "",
                key = null,
                appPackage = "",
                activityClassName = "",
                user = myUserHandle,
            )
            applyResults(query, generation, items.map { Row.App(it) })
            return
        }
        filterJob = iconScope.launch {
            val items = withContext(Dispatchers.Default) {
                val matches = appLabelMatcher(query)
                val ranking = appLabelRanking(query)
                val apps = if (contactsOnly) emptyList() else source.filter {
                    ensureActive()
                    it !is AppModel.PrivateSpaceHeader && matches(it.appLabel)
                }.sortedWith { first, second -> ranking.compare(first.appLabel, second.appLabel) }
                val matchingContacts = contacts.filter {
                    ensureActive()
                    query.isBlank() || matches(it.name)
                }.let { matches ->
                    if (query.isBlank()) matches else matches.sortedWith { first, second ->
                        ranking.compare(first.name, second.name)
                    }
                }
                buildList<Row> {
                    apps.forEach { add(Row.App(it)) }
                    if (flag == Constants.FLAG_LAUNCH_APP && apps.isNotEmpty()) add(Row.Section.APPS)
                    if (!contactsOnly && matchingContacts.isNotEmpty()) {
                        // Position zero within each section is closest to the bottom.
                        matchingContacts.forEach { add(Row.Contact(it)) }
                        add(Row.Section.CONTACTS)
                    } else if (contactsOnly && matchingContacts.isNotEmpty()) {
                        // Contacts has a fixed heading outside the scrolling viewport.
                        matchingContacts.forEach { add(Row.Contact(it)) }
                    }
                }
            }
            applyResults(query, generation, items)
        }
    }

    private fun applyResults(query: String, generation: Int, items: List<Row>) {
        submitList(items) {
            if (generation != filterGeneration || !isCurrentSearch(query)) return@submitList
            appliedGeneration = generation
            onSearchResultsApplied(query)
            if (!isSearchDataLoaded()) return@submitList
            pendingSubmit?.let { submit ->
                pendingSubmit = null
                dispatchSearch(items, submit)
                return@submitList
            }
        }
    }

    private class SectionViewHolder(private val binding: AdapterDrawerSectionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(section: Row.Section) {
            val isApps = section == Row.Section.APPS
            binding.sectionLabel.setText(if (isApps) R.string.drawer_apps else R.string.drawer_contacts)
            binding.sectionLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(
                if (isApps) R.drawable.ic_drawer_apps_badge else R.drawable.ic_drawer_contacts_badge,
                0, 0, 0,
            )
        }
    }

    class PrivateSpaceHeaderViewHolder(private val binding: AdapterPrivateSpaceHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            appLabelGravity: Int,
            toggleListener: () -> Unit,
            settingsListener: () -> Unit,
        ) = with(binding) {
            privateSpaceTitle.gravity = appLabelGravity
            privateSpaceTitle.setOnClickListener { toggleListener() }
            privateSpaceTitle.setOnLongClickListener {
                settingsListener()
                true
            }
        }
    }

    class ViewHolder(
        private val binding: AdapterAppDrawerBinding,
        private val showTiles: Boolean,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var iconJob: Job? = null

        init {
            if (showTiles) with(binding) {
                root.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = dp(4) }
                appTitle.setBackgroundResource(R.drawable.bg_drawer_app_tile)
                appTitle.minimumHeight = dp(48)
                appTitle.updatePaddingRelative(start = dp(18), top = dp(6), end = dp(14), bottom = dp(6))
                appTitle.compoundDrawablePadding = dp(12)
                appTitle.textSize = 17f
                appTitle.ellipsize = TextUtils.TruncateAt.END
            }
        }

        private fun dp(value: Int): Int =
            (value * itemView.resources.displayMetrics.density + 0.5f).toInt()

        fun bindIcon(
            scope: CoroutineScope,
            cache: LruCache<AppModel, Drawable.ConstantState>,
            app: AppModel,
        ) {
            cancelIconLoad()
            val context = itemView.context
            val cached = cache.get(app)?.newDrawable(context.resources)?.mutate()
            val chevron = AppCompatResources.getDrawable(context, R.drawable.ic_drawer_chevron)
            chevron?.setBounds(0, 0, dp(16), dp(16))
            val iconSize = dp(32)
            val initialIcon = cached ?: context.packageManager.defaultActivityIcon
            initialIcon.setBounds(0, 0, iconSize, iconSize)
            binding.appTitle.setCompoundDrawablesRelative(initialIcon, null, chevron, null)
            if (cached != null) return

            iconJob = scope.launch {
                val icon = withContext(Dispatchers.IO) { loadIcon(context, app) } ?: return@launch
                icon.constantState?.let { cache.put(app, it) }
                icon.setBounds(0, 0, iconSize, iconSize)
                binding.appTitle.setCompoundDrawablesRelative(icon, null, chevron, null)
            }
        }

        fun cancelIconLoad() {
            iconJob?.cancel()
            iconJob = null
        }

        private fun loadIcon(context: Context, app: AppModel): Drawable? {
            val launcher = context.getSystemService(LauncherApps::class.java) ?: return null
            val density = context.resources.displayMetrics.densityDpi
            if (app is AppModel.PinnedShortcut) {
                val shortcutIcon = runCatching {
                    val query = LauncherApps.ShortcutQuery()
                        .setPackage(app.appPackage)
                        .setShortcutIds(listOf(app.shortcutId))
                        .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
                    launcher.getShortcuts(query, app.user)?.firstOrNull()?.let {
                        launcher.getShortcutBadgedIconDrawable(it, density)
                    }
                }.getOrNull()
                if (shortcutIcon != null) return shortcutIcon
            }
            return runCatching {
                val activities = launcher.getActivityList(app.appPackage, app.user)
                val activity = if (app is AppModel.App) {
                    activities.firstOrNull { it.componentName.className == app.activityClassName }
                } else null
                (activity ?: activities.firstOrNull())?.getBadgedIcon(density)
            }.getOrNull()
        }

        fun bind(
            flag: Int,
            appLabelGravity: Int,
            myUserHandle: UserHandle,
            appModel: AppModel,
            clickListener: (AppModel) -> Unit,
            appDeleteListener: (AppModel) -> Unit,
            appInfoListener: (AppModel) -> Unit,
            appHideListener: (AppModel) -> Unit,
            appRenameListener: (AppModel, String) -> Unit,
        ) = with(binding) {
            appHideLayout.visibility = View.GONE
            renameLayout.visibility = View.GONE
            appTitle.visibility = View.VISIBLE

            // Show indicators in title based on app type and state
            appTitle.text = buildString {
                append(appModel.appLabel)
                if (appModel.isNew) append(" ✦")
            }
            appTitle.gravity = if (showTiles) appLabelGravity or Gravity.CENTER_VERTICAL else appLabelGravity
            otherProfileIndicator.isVisible = appModel.user != myUserHandle

            appTitle.setOnClickListener {
                if (appModel.appPackage.isNotEmpty()) clickListener(appModel)
            }

            appTitle.setOnLongClickListener {
                if (appModel.appPackage.isNotEmpty()) {
                    appDelete.alpha = when (
                        appModel is AppModel.PinnedShortcut || !root.context.isSystemApp(appModel.appPackage, appModel.user)
                    ) {
                        true -> 1.0f
                        false -> 0.5f
                    }
                    appDelete.text = if (appModel is AppModel.PinnedShortcut)
                        root.context.getString(R.string.remove)
                    else
                        root.context.getString(R.string.delete)
                    appHide.text = if (flag == Constants.FLAG_HIDDEN_APPS)
                        root.context.getString(R.string.adapter_show)
                    else
                        root.context.getString(R.string.adapter_hide)
                    appTitle.visibility = View.INVISIBLE
                    appHide.alpha = when (appModel is AppModel.PinnedShortcut) {
                        true -> 0.5f
                        false -> 1.0f
                    }
                    appHideLayout.visibility = View.VISIBLE
                    // Only allow renaming non hidden apps
                    appRename.isVisible = flag != Constants.FLAG_HIDDEN_APPS
                }
                true
            }

            // Configure rename behavior
            appRename.setOnClickListener {
                if (appModel.appPackage.isNotEmpty()) {
                    etAppRename.hint = (appModel as? AppModel.App)?.key?.sourceString
                        ?: getAppName(etAppRename.context, appModel.appPackage, appModel.user)
                    etAppRename.setText(appModel.appLabel)
                    etAppRename.setSelectAllOnFocus(true)
                    renameLayout.visibility = View.VISIBLE
                    appHideLayout.visibility = View.GONE
                    etAppRename.imeOptions = EditorInfo.IME_ACTION_DONE
                    etAppRename.showKeyboard()
                }
            }
            etAppRename.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                appTitle.visibility = if (hasFocus) View.INVISIBLE else View.VISIBLE
            }
            etAppRename.setOnEditorActionListener { _, actionCode, _ ->
                if (actionCode != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
                val renameLabel = etAppRename.text.toString().trim()
                if (renameLabel.isNotBlank() && appModel.appPackage.isNotBlank()) {
                    appRenameListener(appModel, renameLabel)
                    etAppRename.hideKeyboard()
                    renameLayout.visibility = View.GONE
                    appTitle.visibility = View.VISIBLE
                }
                true
            }
            tvSaveRename.setOnClickListener {
                etAppRename.hideKeyboard()
                val renameLabel = etAppRename.text.toString().trim()
                    .ifBlank { etAppRename.hint?.toString().orEmpty() }
                appRenameListener(appModel, renameLabel)
                renameLayout.visibility = View.GONE
                appTitle.visibility = View.VISIBLE
            }
            appInfo.setOnClickListener { appInfoListener(appModel) }
            appDelete.setOnClickListener { appDeleteListener(appModel) }
            appMenuClose.setOnClickListener {
                appHideLayout.visibility = View.GONE
                appTitle.visibility = View.VISIBLE
            }
            appRenameClose.setOnClickListener {
                renameLayout.visibility = View.GONE
                appTitle.visibility = View.VISIBLE
            }
            appHide.setOnClickListener { appHideListener(appModel) }
        }

        private fun getAppName(context: Context, appPackage: String, user: UserHandle): String {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            return try {
                val activityList = launcherApps.getActivityList(appPackage, user)
                if (activityList.isNotEmpty()) {
                    activityList.first().label.toString()
                } else {
                    val packageManager = context.packageManager
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(appPackage, 0)
                    ).toString()
                }
            } catch (_: Exception) {
                "" // As a fallback, display an empty string.
            }
        }
    }
}
