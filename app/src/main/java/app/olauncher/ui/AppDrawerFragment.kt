package app.olauncher.ui

import android.Manifest
import android.content.Context
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
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
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
import app.olauncher.helper.deletePinnedShortcut
import app.olauncher.helper.openDrawerContact
import app.olauncher.helper.readDrawerContacts
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isSystemAnimationsDisabled
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.openSearch
import app.olauncher.helper.openUrl
import app.olauncher.helper.showToast
import app.olauncher.helper.uninstall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppDrawerFragment : BaseFragment() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager
    private var displayedSearchQuery = ""
    private var contactsOnly = false
    private var contactsLoaded = false
    private var contactsJob: Job? = null
    private val requestContactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (_binding != null) {
            if (granted) {
                browseContacts()
                refreshContacts()
            } else {
                requireContext().showToast(getString(R.string.drawer_contacts_permission))
            }
        }
    }
    private var cachedIsCjkKeyboard: Boolean? = null
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
        contactsOnly = false
        contactsLoaded = false
        currentAppList = null
        currentPrivateSpaceApps = null
        exitPosted = false
        if (flag == Constants.FLAG_LAUNCH_APP) configureAppSearchUi()

        initAdapter()
        initSearch()
        initObservers()
        initClickListeners()

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

    private fun configureAppSearchUi() = with(binding) {
        root.setBackgroundColor(Color.rgb(11, 11, 11))
        appsHeading.isVisible = true
        categoryMenu.root.isVisible = true
        val sideMargin = resources.getDimensionPixelSize(R.dimen.app_drawer_search_margin)
        recyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = sideMargin
            marginEnd = sideMargin
        }
        searchBar.setBackgroundResource(R.drawable.bg_drawer_search_white)
        searchLeadingIcon.isVisible = false
        searchLegacyRail.isVisible = false
        searchActionBadge.isVisible = true
        searchDivider.setBackgroundColor(Color.rgb(16, 16, 16))
    }

    private fun initSearch() = with(binding.search) {
        if (flag == Constants.FLAG_HIDDEN_APPS)
            hint = getString(R.string.hidden_apps)
        else if (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_CALENDAR_APP)
            hint = "Please select an app"

        doAfterTextChanged { query ->
            val userInput = viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            adapter.updateSearch(
                query?.toString().orEmpty(),
                allowAutoLaunch = userInput && !isSearchComposing(),
            )
            binding.appRename.visibility =
                if (canRename && !query.isNullOrBlank()) View.VISIBLE else View.GONE
        }

        setOnEditorActionListener { _, actionId, event ->
            val query = text?.toString().orEmpty()
            when {
                query.isBlank() -> false
                event == null && actionId == EditorInfo.IME_ACTION_SEARCH -> {
                    submitSearch(query)
                    true
                }
                event?.keyCode == KeyEvent.KEYCODE_ENTER -> {
                    if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) submitSearch(query)
                    true
                }
                else -> false
            }
        }
    }

    private fun submitSearch(query: String) {
        if (!contactsOnly && query.startsWith("!")) {
            adapter.cancelPendingSearchAction()
            requireContext().openUrl(Constants.URL_DUCK_SEARCH + query.replace(" ", "%20"))
            return
        }
        adapter.submitSearch { app ->
            if (app == null) requireContext().openSearch(query.trim())
            else selectApp(app)
        }
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
            WindowCompat.getInsetsController(requireActivity().window, search)
                .show(WindowInsetsCompat.Type.ime())
        }
    }

    private fun isSearchComposing(): Boolean {
        val text = binding.search.text ?: return false
        val start = BaseInputConnection.getComposingSpanStart(text)
        val end = BaseInputConnection.getComposingSpanEnd(text)
        if (start !in 0 until end) return false
        return isCjkKeyboard()
    }

    private fun isCjkKeyboard(): Boolean {
        cachedIsCjkKeyboard?.let { return it }
        val result = try {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val subtype = imm.currentInputMethodSubtype
            val language = when {
                subtype == null -> ""
                subtype.languageTag.isNotEmpty() -> subtype.languageTag // e.g. "zh-CN", "ja-JP", "en-US"
                else -> subtype.locale // deprecated fallback, e.g. "zh_CN"
            }
            language.startsWith("zh") || language.startsWith("ja") || language.startsWith("ko")
        } catch (e: Exception) {
            false
        }
        cachedIsCjkKeyboard = result
        return result
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
                    binding.appsHeading.isVisible = !contactsOnly && query.isBlank()
                    updateContactsEmptyState()
                    if (query != displayedSearchQuery) {
                        displayedSearchQuery = query
                        linearLayoutManager.reverseLayout = !contactsOnly && query.isNotBlank()
                        linearLayoutManager.scrollToPositionWithOffset(0, 0)
                    }
                }
            },
            appClickListener = ::selectApp,
            contactClickListener = { contact, action ->
                openDrawerContact(requireContext(), contact, action)
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
            binding.categoryMenu.categoryContacts.setOnClickListener {
                if (hasContactsPermission()) {
                    browseContacts()
                    if (!contactsLoaded && contactsJob?.isActive != true) refreshContacts()
                } else {
                    requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
                }
            }
            binding.categoryMenu.categoryApps.setOnClickListener {
                setContactsMode(false)
                binding.search.text?.clear()
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

    private fun hasContactsPermission() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    private fun browseContacts() {
        setContactsMode(true)
        binding.search.text?.clear()
    }

    private fun setContactsMode(enabled: Boolean) {
        if (contactsOnly == enabled) return
        contactsOnly = enabled
        binding.categoryMenu.categoryContacts.isSelected = enabled
        binding.categoryMenu.categoryApps.isSelected = !enabled
        binding.appsHeading.isVisible = !enabled && binding.search.text.isNullOrBlank()
        linearLayoutManager.reverseLayout = !enabled && !binding.search.text.isNullOrBlank()
        linearLayoutManager.scrollToPositionWithOffset(0, 0)
        adapter.setContactsOnly(enabled)
        updateContactsEmptyState()
    }

    private fun refreshContacts() {
        contactsJob?.cancel()
        if (!hasContactsPermission()) {
            contactsLoaded = false
            setContactsMode(false)
            adapter.setContacts(emptyList())
            return
        }
        val context = requireContext().applicationContext
        if (!contactsLoaded) adapter.setContacts(null)
        contactsJob = viewLifecycleOwner.lifecycleScope.launch {
            val contacts = withContext(Dispatchers.IO) { readDrawerContacts(context) }
            contactsLoaded = true
            adapter.setContacts(contacts)
            updateContactsEmptyState()
        }
    }

    private fun updateContactsEmptyState() {
        val empty = contactsOnly && contactsLoaded && adapter.itemCount == 0
        binding.contactsEmpty.isVisible = empty
        binding.recyclerView.isVisible = !empty
    }

    override fun onResume() {
        super.onResume()
        if (flag == Constants.FLAG_LAUNCH_APP) refreshContacts()
    }

    private fun checkMessageAndExit() {
        if (!closeDrawer()) return
        if (flag == Constants.FLAG_LAUNCH_APP)
            viewModel.checkForMessages.call()
    }

    // Pops only the drawer. A plain popBackStack() from a late overscroll frame could pop the
    // home screen too, leaving a drawer that auto-launch and back can't close (#766)
    private fun closeDrawer() = findNavController().popBackStack(R.id.appListFragment, true)

    override fun onStart() {
        super.onStart()
        cachedIsCjkKeyboard = null
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
        if (prefs.autoShowKeyboard) showSearchKeyboard()
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
        contactsJob?.cancel()
        contactsJob = null
        binding.recyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
