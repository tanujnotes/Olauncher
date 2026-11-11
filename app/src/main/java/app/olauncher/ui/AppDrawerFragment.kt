package app.olauncher.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentAppDrawerBinding
import app.olauncher.helper.ContactSearchCommand
import app.olauncher.helper.ContactAction
import app.olauncher.helper.DrawerContact
import app.olauncher.helper.deletePinnedShortcut
import app.olauncher.helper.openDrawerContact
import app.olauncher.helper.DrawerContactsCache
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isSystemAnimationsDisabled
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.openSearch
import app.olauncher.helper.openUrl
import app.olauncher.helper.showToast
import app.olauncher.helper.uninstall
import app.olauncher.ui.AppDrawerAdapter.SearchCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class AppDrawerFragment : BaseFragment() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager
    private var displayedSearchQuery = ""
    private var displayedCategory = SearchCategory.ALL
    private var homeSearchAnimator: android.animation.ValueAnimator? = null
    private var animateCategoryChanges = false
    private var contactCommand: ContactSearchCommand? = null
    private var commandEntryMode = false
    private var commandHandled = false
    private var contactsLoaded = false
    private var contactsJob: Job? = null
    private val requestContactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (_binding != null) {
            if (granted) {
                selectSearchCategory(SearchCategory.CONTACTS)
                refreshContacts()
            } else {
                requireContext().showToast(getString(R.string.drawer_contacts_permission))
            }
        }
    }
    private var lightStatusBar = false
    private var lightNavigationBar = false

    private var flag = Constants.FLAG_LAUNCH_APP
    private var canRename = false
    private var currentAppList: List<AppModel>? = null
    private var currentPrivateSpaceApps: List<AppModel>? = null
    private var exitPosted = false

    private val viewModel: MainViewModel by activityViewModels()
    private var _binding: FragmentAppDrawerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        arguments?.let {
            flag = it.getInt(Constants.Key.FLAG, Constants.FLAG_LAUNCH_APP)
            canRename = it.getBoolean(Constants.Key.RENAME, false)
        }
        val drawerInflater = if (flag == Constants.FLAG_LAUNCH_APP) {
            inflater.cloneInContext(ContextThemeWrapper(inflater.context, R.style.DrawerDarkOverlay))
        } else {
            inflater
        }
        _binding = FragmentAppDrawerBinding.inflate(drawerInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        displayedSearchQuery = ""
        displayedCategory = SearchCategory.ALL
        contactCommand = null
        commandEntryMode = false
        commandHandled = false
        contactsLoaded = false
        currentAppList = null
        currentPrivateSpaceApps = null
        exitPosted = false
        if (flag == Constants.FLAG_LAUNCH_APP) configureAppSearchUi()

        initAdapter()
        initSearch()
        initObservers()
        initClickListeners()
        if (flag == Constants.FLAG_LAUNCH_APP) {
            setCommandEntryMode(arguments?.getBoolean(Constants.Key.COMMAND_MODE, false) == true)
        }

        if (savedInstanceState == null) animateHomeSearchEntry()

        val topPadding = binding.root.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { drawer, insets ->
            val safeArea = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            drawer.updatePadding(
                left = safeArea.left,
                top = maxOf(topPadding, safeArea.top),
                right = safeArea.right,
                bottom = maxOf(safeArea.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            )
            insets
        }
    }

    private fun animateHomeSearchEntry() {
        val sourceY = arguments?.getInt("homeSearchY", -1) ?: -1
        if (sourceY < 0 || flag != Constants.FLAG_LAUNCH_APP ||
            requireContext().isEinkDisplay() || requireContext().isSystemAnimationsDisabled()) return
        val bar = binding.searchBar
        var progress = 0f
        val location = IntArray(2)
        // Recalculate against the laid-out destination on every frame so IME insets
        // can move the destination without snapping the bar away from its path.
        val listener = android.view.ViewTreeObserver.OnPreDrawListener {
            bar.getLocationOnScreen(location)
            val layoutY = location[1] - bar.translationY
            bar.translationY = (sourceY - layoutY) * (1f - progress)
            true
        }
        bar.viewTreeObserver.addOnPreDrawListener(listener)
        homeSearchAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 350L
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                bar.invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (bar.viewTreeObserver.isAlive) bar.viewTreeObserver.removeOnPreDrawListener(listener)
                    bar.translationY = 0f
                }
            })
        }
        bar.doOnPreDraw { homeSearchAnimator?.start() }
    }

    private fun configureAppSearchUi() = with(binding) {
        root.setBackgroundColor(Color.rgb(11, 11, 11))
        appsHeading.isVisible = true
        categoryMenu.root.isVisible = true
        categoryMenu.categoryApps.isSelected = false
        animateCategoryChanges = !requireContext().isEinkDisplay() &&
            !requireContext().isSystemAnimationsDisabled()
        val sideMargin = resources.getDimensionPixelSize(R.dimen.app_drawer_search_margin)
        resultsViewport.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = sideMargin
            marginEnd = sideMargin
        }
        searchBar.setBackgroundResource(R.drawable.bg_home_search)
        search.setTextColor(Color.WHITE)
        search.setHintTextColor(Color.WHITE)
        searchActionBadge.setImageResource(R.drawable.ic_home_search_badge)
        searchLeadingIcon.isVisible = false
        searchLegacyRail.isVisible = false
        searchActionBadge.isVisible = true
        searchDivider.setBackgroundColor(Color.WHITE)
    }

    private fun initSearch() = with(binding.search) {
        onEmptyBackspace = {
            if (contactCommand != null) {
                clearContactCommand()
                true
            } else false
        }
        if (flag == Constants.FLAG_HIDDEN_APPS)
            hint = getString(R.string.hidden_apps)
        else if (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_CALENDAR_APP)
            hint = "Please select an app"

        doAfterTextChanged { query ->
            commandHandled = false
            val text = query?.toString().orEmpty()
            if (flag == Constants.FLAG_LAUNCH_APP && contactCommand == null) {
                val command = ContactSearchCommand.parsePrefix(text, true)
                if (command != null) {
                    setCommandEntryMode(false)
                    selectSearchCategory(if (command.action != null) SearchCategory.CONTACTS else SearchCategory.ALL)
                    contactCommand = command
                    updateContactCommandChip()
                    if (command.action == null) {
                        // External searches do not require contact access.
                    } else if (hasContactsPermission()) {
                        if (!contactsLoaded && contactsJob?.isActive != true) refreshContacts()
                    } else {
                        requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
                    }
                    // Remove only the prefix; pasted commands can include a contact query.
                    setText(ContactSearchCommand.queryAfterPrefix(text))
                    setSelection(this.text?.length ?: 0)
                    return@doAfterTextChanged
                }
            }
            adapter.updateSearch(text)
            binding.appRename.visibility =
                if (canRename && !query.isNullOrBlank()) View.VISIBLE else View.GONE
        }

        setOnEditorActionListener { _, actionId, event ->
            val query = text?.toString().orEmpty()
            val imeSubmit = event == null && actionId in setOf(
                EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_DONE,
            )
            val enterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER
            val completedPress = imeSubmit ||
                (enterKey && event?.action == KeyEvent.ACTION_UP && event?.isCanceled == false)
            when {
                query.isEmpty() -> true
                query.isBlank() -> false
                imeSubmit || enterKey -> {
                    if (completedPress) submitSearch(query)
                    true
                }
                else -> false
            }
        }
    }

    private fun submitSearch(query: String) {
        when (contactCommand) {
            ContactSearchCommand.WEB -> {
                requireContext().openUrl("https://www.google.com/search?q=" + android.net.Uri.encode(query.trim()))
                return
            }
            ContactSearchCommand.AI -> {
                openAiSearch(query.trim())
                return
            }
            else -> Unit
        }
        if (adapter.searchCategory != SearchCategory.CONTACTS && query.startsWith("!")) {
            adapter.cancelPendingSearchAction()
            requireContext().openUrl(Constants.URL_DUCK_SEARCH + query.replace(" ", "%20"))
            return
        }
        adapter.submitSearch { app ->
            if (app == null) requireContext().openSearch(query.trim())
            else selectApp(app)
        }
    }

    private fun aiSearchSettings() = requireContext().getSharedPreferences(
        "external_search", android.content.Context.MODE_PRIVATE
    )

    private fun selectedAiProvider(): Int {
        val selected = aiSearchSettings().getInt("ai_provider", 0)
        if (selected !in 0..1) {
            aiSearchSettings().edit().putInt("ai_provider", 0).apply()
            return 0
        }
        return selected
    }

    private fun chooseAiProvider() {
        val providers = resources.getStringArray(R.array.command_ai_providers)
        val selected = selectedAiProvider()
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.command_choose_ai)
            .setSingleChoiceItems(providers, selected) { dialog, index ->
                aiSearchSettings().edit().putInt("ai_provider", index).apply()
                updateContactCommandChip()
                dialog.dismiss()
                showSearchKeyboard()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openAiSearch(query: String) {
        val encoded = android.net.Uri.encode(query)
        when (selectedAiProvider()) {
            1 -> {
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT, query)
                    .setPackage("com.google.android.apps.bard")
                try {
                    startActivity(intent)
                } catch (_: android.content.ActivityNotFoundException) {
                    openGeminiWithCopiedPrompt(query)
                } catch (_: SecurityException) {
                    openGeminiWithCopiedPrompt(query)
                }
            }
            else -> requireContext().openUrl("https://www.google.com/search?udm=50&q=" + encoded)
        }
    }

    private fun openGeminiWithCopiedPrompt(query: String) {
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AI prompt", query))
        requireContext().openUrl("https://gemini.google.com/app")
        requireContext().showToast(getString(R.string.command_gemini_paste))
    }

    private fun selectApp(app: AppModel) {
        if (findNavController().currentDestination?.id != R.id.appListFragment) return
        viewModel.selectedApp(app, flag)
        if (flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS)
            findNavController().popBackStack(R.id.mainFragment, false)
        else
            closeDrawer()
    }

    private fun showSearchKeyboard() {
        val search = binding.search
        if (search.requestFocus()) {
            search.refreshTypingCursor()
            WindowCompat.getInsetsController(requireActivity().window, search)
                .show(WindowInsetsCompat.Type.ime())
        }
    }

    private fun initAdapter() {
        adapter = AppDrawerAdapter(
            flag,
            prefs.appLabelAlignment,
            viewLifecycleOwner.lifecycleScope,
            isCurrentSearch = { query ->
                _binding?.search?.text?.toString() == query &&
                    findNavController().currentDestination?.id == R.id.appListFragment
            },
            onSearchResultsApplied = { query ->
                if (flag == Constants.FLAG_LAUNCH_APP) {
                    showSearchResults(query)
                    maybeExecuteContactCommand(query)
                }
            },
            appClickListener = ::selectApp,
            contactClickListener = { contact, action ->
                executeContactAction(contact, action)
            },
            appInfoListener = {
                openAppInfo(
                    requireContext(),
                    it.user,
                    it.appPackage
                )
                findNavController().popBackStack(R.id.mainFragment, false)
            },
            appDeleteListener = { appModel ->
                when (appModel) {
                    is AppModel.PrivateSpaceHeader -> {}
                    is AppModel.PinnedShortcut ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                            requireContext().deletePinnedShortcut(
                                packageName = appModel.appPackage,
                                shortcutIdToDelete = appModel.shortcutId,
                                user = appModel.user,
                            )
                        }

                    is AppModel.App -> {
                        if (appModel.user != Process.myUserHandle()) {
                            openAppInfo(requireContext(), appModel.user, appModel.appPackage)
                        } else if (requireContext().isSystemApp(appModel.appPackage, appModel.user)) {
                            requireContext().showToast(getString(R.string.system_app_cannot_delete))
                            openAppInfo(requireContext(), appModel.user, appModel.appPackage)
                        } else {
                            requireContext().uninstall(appModel.appPackage)
                        }
                    }
                }
                viewModel.getAppList()
            },
            appHideListener = { appModel ->
                if (appModel is AppModel.PinnedShortcut) {
                    requireContext().showToast("Hiding pinned shortcuts is not supported")
                    return@AppDrawerAdapter
                }
                val newSet = mutableSetOf<String>()
                newSet.addAll(prefs.hiddenApps)
                if (flag == Constants.FLAG_HIDDEN_APPS)
                    newSet.remove(appModel.appPackage + "|" + appModel.user.toString())
                else
                    newSet.add(appModel.appPackage + "|" + appModel.user.toString())

                prefs.hiddenApps = newSet
                viewModel.getAppList()
                if (flag == Constants.FLAG_HIDDEN_APPS && newSet.isEmpty()) {
                    closeDrawer()
                    return@AppDrawerAdapter
                }
                if (prefs.firstHide) {
                    binding.search.hideKeyboard()
                    prefs.firstHide = false
                    viewModel.showDialog.postValue(Constants.Dialog.HIDDEN)
                    findNavController().navigate(R.id.action_appListFragment_to_settingsFragment2)
                }
            },
            appRenameListener = { appModel, renameLabel ->
                val identifier = when (appModel) {
                    is AppModel.PinnedShortcut -> appModel.identity
                    is AppModel.App -> appModel.appPackage
                    else -> return@AppDrawerAdapter
                }
                prefs.setAppRenameLabel(identifier, renameLabel)
                viewModel.getAppList()
            },
            privateSpaceToggleListener = {
                viewModel.togglePrivateSpaceLock()
            },
            privateSpaceSettingsListener = {
                viewModel.openPrivateSpaceSettings()
                findNavController().popBackStack(R.id.mainFragment, false)
            }
        )

        linearLayoutManager = object : LinearLayoutManager(requireContext()) {
            override fun scrollVerticallyBy(
                dx: Int,
                recycler: Recycler,
                state: RecyclerView.State,
            ): Int {
                val scrollRange = super.scrollVerticallyBy(dx, recycler, state)
                val overScroll = dx - scrollRange
                val list = _binding?.recyclerView
                if (!exitPosted && !reverseLayout && overScroll < -10 &&
                    list?.scrollState == RecyclerView.SCROLL_STATE_DRAGGING
                ) {
                    exitPosted = true
                    // RecyclerView must finish its scroll before navigation changes its view.
                    list.post {
                        exitPosted = false
                        if (_binding?.recyclerView === list &&
                            findNavController().currentDestination?.id == R.id.appListFragment
                        ) checkMessageAndExit()
                    }
                }
                return scrollRange
            }
        }

        binding.recyclerView.layoutManager = linearLayoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    _binding?.search?.hideKeyboard()
                }
            }
        })
        binding.recyclerView.itemAnimator = null
        if (requireContext().isEinkDisplay())
            binding.recyclerView.overScrollMode = View.OVER_SCROLL_NEVER
        else if (flag != Constants.FLAG_LAUNCH_APP && !requireContext().isSystemAnimationsDisabled())
            binding.recyclerView.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_from_bottom)
    }

    private fun initObservers() {
        viewModel.installedApps.observe(viewLifecycleOwner) { apps ->
            currentAppList = when (flag) {
                Constants.FLAG_LAUNCH_APP -> apps.visible
                Constants.FLAG_HIDDEN_APPS -> apps.hidden
                Constants.FLAG_SET_SWIPE_LEFT_APP, Constants.FLAG_SET_SWIPE_RIGHT_APP -> apps.all
                else -> apps.visible
            }
            updateCombinedAppList()
        }
        if (flag == Constants.FLAG_LAUNCH_APP) {
            viewModel.privateSpaceApps.observe(viewLifecycleOwner) {
                currentPrivateSpaceApps = it
                updateCombinedAppList()
            }
        }
    }

    private fun updateCombinedAppList() {
        val apps = currentAppList ?: return
        val privateApps = currentPrivateSpaceApps
        adapter.setAppList(if (privateApps.isNullOrEmpty()) apps else apps + privateApps)
    }

    private fun initClickListeners() {
        if (flag == Constants.FLAG_LAUNCH_APP) {
            binding.searchActionBadge.setOnClickListener {
                if (!commandEntryMode) clearContactCommand()
                setCommandEntryMode(!commandEntryMode)
                showSearchKeyboard()
            }
            binding.categoryMenu.categoryContacts.setOnClickListener {
                if (adapter.searchCategory == SearchCategory.CONTACTS) {
                    selectSearchCategory(SearchCategory.ALL)
                } else if (hasContactsPermission()) {
                    selectSearchCategory(SearchCategory.CONTACTS)
                    if (!contactsLoaded && contactsJob?.isActive != true) refreshContacts()
                } else {
                    requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
                }
            }
            binding.categoryMenu.categoryApps.setOnClickListener {
                selectSearchCategory(
                    if (adapter.searchCategory == SearchCategory.APPS) SearchCategory.ALL
                    else SearchCategory.APPS
                )
            }
        }
        binding.appRename.setOnClickListener {
            val name = binding.search.text.toString().trim()
            if (name.isEmpty()) {
                requireContext().showToast(getString(R.string.type_a_new_app_name_first))
                showSearchKeyboard()
                return@setOnClickListener
            }

            when (flag) {
                Constants.FLAG_SET_HOME_APP_1 -> prefs.appName1 = name
                Constants.FLAG_SET_HOME_APP_2 -> prefs.appName2 = name
                Constants.FLAG_SET_HOME_APP_3 -> prefs.appName3 = name
                Constants.FLAG_SET_HOME_APP_4 -> prefs.appName4 = name
                Constants.FLAG_SET_HOME_APP_5 -> prefs.appName5 = name
                Constants.FLAG_SET_HOME_APP_6 -> prefs.appName6 = name
                Constants.FLAG_SET_HOME_APP_7 -> prefs.appName7 = name
                Constants.FLAG_SET_HOME_APP_8 -> prefs.appName8 = name
            }
            closeDrawer()
        }
    }

    private fun setCommandEntryMode(enabled: Boolean) = with(binding) {
        commandEntryMode = enabled
        val highlighted = enabled || contactCommand != null
        searchDivider.setBackgroundColor(if (highlighted) Color.BLACK else Color.WHITE)
        searchBar.setBackgroundResource(
            if (highlighted) R.drawable.bg_drawer_search_command else R.drawable.bg_home_search
        )
        searchActionBadge.setImageResource(
            if (highlighted) R.drawable.ic_drawer_action_badge_command else R.drawable.ic_home_search_badge
        )
        searchActionBadge.isSelected = highlighted
        searchActionBadge.contentDescription = getString(
            if (enabled) R.string.command_mode_disable else R.string.command_mode_enable
        )
        search.refreshTypingCursor()
        search.setTextColor(if (highlighted) Color.BLACK else Color.WHITE)
        search.setHintTextColor(if (highlighted) Color.BLACK else Color.WHITE)
    }

    private fun updateContactCommandChip(): Unit = with(binding.contactCommandChip) {
        val command = contactCommand
        adapter.contactCommand = command?.action
        if (flag == Constants.FLAG_LAUNCH_APP) {
            binding.search.setHint(command?.hintRes ?: R.string.search_anything)
        }
        isVisible = command != null
        setCommandEntryMode(commandEntryMode)
        if (command == null) return@with
        setImageResource(when (command) {
            ContactSearchCommand.PHONE -> R.drawable.ic_drawer_contact_phone
            ContactSearchCommand.MESSAGE -> R.drawable.ic_drawer_contact_text
            ContactSearchCommand.WHATSAPP -> R.drawable.ic_drawer_contact_whatsapp
            ContactSearchCommand.WEB -> R.drawable.ic_command_web
            ContactSearchCommand.AI -> if (selectedAiProvider() == 1)
                R.drawable.ic_command_gemini else R.drawable.ic_command_ai
        })
        contentDescription = getString(when (command) {
            ContactSearchCommand.PHONE -> R.string.contact_command_phone
            ContactSearchCommand.MESSAGE -> R.string.contact_command_message
            ContactSearchCommand.WHATSAPP -> R.string.contact_command_whatsapp
            ContactSearchCommand.WEB -> R.string.command_web
            ContactSearchCommand.AI -> R.string.command_ai
        })
        setOnLongClickListener {
            if (command == ContactSearchCommand.AI) {
                chooseAiProvider()
                true
            } else false
        }
        setOnClickListener {
            clearContactCommand()
            showSearchKeyboard()
        }
    }

    private fun clearContactCommand() {
        contactCommand = null
        commandHandled = false
        updateContactCommandChip()
    }

    private fun maybeExecuteContactCommand(query: String) {
        val command = contactCommand ?: return
        val action = command.action ?: return
        if (commandHandled || query.isBlank() || !contactsLoaded ||
            adapter.searchCategory != SearchCategory.CONTACTS ||
            _binding?.search?.text?.toString() != query ||
            !viewLifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        ) return
        val contact = adapter.currentList.filterIsInstance<AppDrawerAdapter.Row.Contact>()
            .singleOrNull()?.model ?: return
        if (contact.numbers.isEmpty()) return
        executeContactAction(contact, action)
    }

    private fun executeContactAction(contact: DrawerContact, action: ContactAction) {
        // A refresh or return from another app must not repeat the automatic action.
        commandHandled = true
        adapter.cancelPendingSearchAction()
        openDrawerContact(requireContext(), contact, action)
    }

    private fun hasContactsPermission() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    private fun selectSearchCategory(category: SearchCategory) {
        if (category != SearchCategory.CONTACTS) {
            contactCommand = null
            commandHandled = false
            updateContactCommandChip()
        }
        binding.categoryMenu.categoryContacts.isSelected = category == SearchCategory.CONTACTS
        binding.categoryMenu.categoryApps.isSelected = category == SearchCategory.APPS
        adapter.setSearchCategory(category)
    }

    private fun refreshContacts() {
        contactsJob?.cancel()
        if (!hasContactsPermission()) {
            DrawerContactsCache.invalidate()
            contactsLoaded = false
            if (adapter.searchCategory == SearchCategory.CONTACTS) {
                selectSearchCategory(SearchCategory.APPS)
            }
            adapter.setContacts(emptyList())
            return
        }
        val context = requireContext().applicationContext
        if (!contactsLoaded) adapter.setContacts(null)
        contactsJob = viewLifecycleOwner.lifecycleScope.launch {
            var first = true
            DrawerContactsCache.changes.collectLatest {
                // Coalesce provider notification bursts; reopening uses the cache immediately.
                if (!first) delay(150)
                first = false
                if (!hasContactsPermission()) {
                    contactsLoaded = false
                    adapter.setContacts(emptyList())
                    return@collectLatest
                }
                val contacts = withContext(Dispatchers.IO) { DrawerContactsCache.get(context) }
                contactsLoaded = true
                adapter.setContacts(contacts)
            }
        }
    }

    private fun showSearchResults(query: String): Unit = with(binding) {
        val category = adapter.searchCategory
        val contactsOnly = category == SearchCategory.CONTACTS
        val categoryChanged = displayedCategory != category
        if (categoryChanged || query != displayedSearchQuery) {
            displayedCategory = category
            displayedSearchQuery = query
            linearLayoutManager.reverseLayout = query.isNotBlank()
            linearLayoutManager.scrollToPositionWithOffset(0, 0)
        }
        appsHeading.isVisible = contactsOnly || query.isBlank()
        appsHeading.setText(if (contactsOnly) R.string.drawer_contacts else R.string.drawer_apps)
        appsHeading.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (contactsOnly) R.drawable.ic_drawer_contacts_badge else R.drawable.ic_drawer_apps_badge,
            0, 0, 0
        )
        val empty = contactsOnly && contactsLoaded && adapter.itemCount == 0
        contactsEmpty.isVisible = empty
        resultsViewport.isVisible = !empty
        recyclerView.isVisible = !empty
        if (categoryChanged && animateCategoryChanges) {
            for (view in listOf(appsHeading, contactsEmpty, recyclerView)) {
                view.animate().cancel()
                view.alpha = 1f
                if (view.isVisible) {
                    view.alpha = 0.35f
                    view.animate().alpha(1f).setDuration(160L).start()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (flag == Constants.FLAG_LAUNCH_APP) refreshContacts()
    }

    private fun checkMessageAndExit() {
        closeDrawer()
    }

    // Pops only the drawer. A plain popBackStack() from a late overscroll frame could pop the
    // home screen too, leaving a drawer that auto-launch and back can't close (#766)
    private fun closeDrawer() = findNavController().popBackStack(R.id.appListFragment, true)

    override fun onStart() {
        super.onStart()
        // The wallpaper layout flag prevents the drawer from resizing for the IME.
        val window = requireActivity().window
        window.clearFlags(FLAG_LAYOUT_NO_LIMITS)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (flag == Constants.FLAG_LAUNCH_APP) {
            val controller = WindowCompat.getInsetsController(window, binding.root)
            lightStatusBar = controller.isAppearanceLightStatusBars
            lightNavigationBar = controller.isAppearanceLightNavigationBars
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
        ViewCompat.requestApplyInsets(binding.root)
        if (arguments?.containsKey("homeSearchY") == true || prefs.autoShowKeyboard || (flag == Constants.FLAG_LAUNCH_APP &&
            arguments?.getBoolean(Constants.Key.COMMAND_MODE, false) == true)) {
            showSearchKeyboard()
        }
    }

    override fun onStop() {
        binding.search.hideKeyboard()
        val window = requireActivity().window
        if (flag == Constants.FLAG_LAUNCH_APP) {
            val controller = WindowCompat.getInsetsController(window, binding.root)
            controller.isAppearanceLightStatusBars = lightStatusBar
            controller.isAppearanceLightNavigationBars = lightNavigationBar
        }
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.addFlags(FLAG_LAYOUT_NO_LIMITS)
        super.onStop()
    }

    override fun onDestroyView() {
        homeSearchAnimator?.cancel()
        homeSearchAnimator = null
        contactsJob?.cancel()
        contactsJob = null
        binding.recyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
