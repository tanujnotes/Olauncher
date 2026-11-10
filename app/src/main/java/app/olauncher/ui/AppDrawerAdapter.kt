package app.olauncher.ui

import android.content.Context
import android.content.pm.LauncherApps
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
import app.olauncher.databinding.AdapterPrivateSpaceHeaderBinding
import app.olauncher.helper.appLabelMatcher
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
    private val privateSpaceToggleListener: () -> Unit = {},
    private val privateSpaceSettingsListener: () -> Unit = {},
) : ListAdapter<AppModel, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        const val VIEW_TYPE_APP = 0
        const val VIEW_TYPE_PRIVATE_HEADER = 1

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppModel>() {
            override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean = when {
                oldItem is AppModel.App && newItem is AppModel.App ->
                    oldItem.appPackage == newItem.appPackage && oldItem.user == newItem.user &&
                        oldItem.activityClassName == newItem.activityClassName

                oldItem is AppModel.PinnedShortcut && newItem is AppModel.PinnedShortcut ->
                    oldItem.identity == newItem.identity

                oldItem is AppModel.PrivateSpaceHeader && newItem is AppModel.PrivateSpaceHeader -> true

                else -> false
            }

            override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean =
                oldItem == newItem
        }
    }

    private val appIcons = LruCache<AppModel, Drawable.ConstantState>(80)
    private val myUserHandle = android.os.Process.myUserHandle()
    private var apps: List<AppModel>? = null
    private var query = ""
    private var pendingAutoLaunch = false
    private var pendingSubmit: ((AppModel?) -> Unit)? = null
    private var filterJob: Job? = null
    private var filterGeneration = 0
    private var appliedGeneration = 0

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is AppModel.PrivateSpaceHeader -> VIEW_TYPE_PRIVATE_HEADER
            else -> VIEW_TYPE_APP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
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
        super.onViewRecycled(holder)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        try {
            val appModel = getItem(position)
            when (holder) {
                is PrivateSpaceHeaderViewHolder -> {
                    holder.bind(
                        appLabelGravity,
                        privateSpaceToggleListener,
                        privateSpaceSettingsListener,
                    )
                }

                is ViewHolder -> {
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

    fun updateSearch(query: String, allowAutoLaunch: Boolean) {
        this.query = query
        pendingAutoLaunch = allowAutoLaunch
        pendingSubmit = null
        filterApps()
    }

    fun setAppList(apps: List<AppModel>) {
        if (this.apps == apps) return
        this.apps = apps
        filterApps()
    }

    fun cancelPendingSearchAction() {
        pendingAutoLaunch = false
        pendingSubmit = null
    }

    fun submitSearch(onResult: (AppModel?) -> Unit) {
        pendingAutoLaunch = false
        if (apps != null && appliedGeneration == filterGeneration) {
            onResult(currentList.firstOrNull())
        } else {
            pendingSubmit = onResult
        }
    }

    private fun filterApps() {
        val source = apps ?: return
        val query = this.query
        val generation = ++filterGeneration
        filterJob?.cancel()
        if (query.isBlank()) {
            val items = if (flag == Constants.FLAG_LAUNCH_APP) source else source + AppModel.App(
                appLabel = "",
                key = null,
                appPackage = "",
                activityClassName = "",
                user = myUserHandle,
            )
            applyResults(query, generation, items)
            return
        }
        filterJob = iconScope.launch {
            val items = withContext(Dispatchers.Default) {
                val matches = appLabelMatcher(query)
                source.filter {
                    ensureActive()
                    it !is AppModel.PrivateSpaceHeader && matches(it.appLabel)
                }
            }
            applyResults(query, generation, items)
        }
    }

    private fun applyResults(query: String, generation: Int, items: List<AppModel>) {
        submitList(items) {
            if (generation != filterGeneration || !isCurrentSearch(query)) return@submitList
            appliedGeneration = generation
            onSearchResultsApplied(query)
            pendingSubmit?.let { submit ->
                pendingSubmit = null
                submit(items.firstOrNull())
                return@submitList
            }
            val launch = pendingAutoLaunch
            pendingAutoLaunch = false
            if (!launch || flag != Constants.FLAG_LAUNCH_APP ||
                query.isBlank() || query.startsWith(" ") || query.startsWith("!")
            ) return@submitList
            items.singleOrNull()?.let(appClickListener)
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
