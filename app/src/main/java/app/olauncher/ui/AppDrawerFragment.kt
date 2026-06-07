package app.olauncher.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import eightbitlab.com.blurview.RenderScriptBlur
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
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
import app.olauncher.helper.applyFontFamily
import app.olauncher.helper.deletePinnedShortcut
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isPrivateSpaceProfile
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.openSearch
import app.olauncher.helper.openUrl
import app.olauncher.helper.showKeyboard
import app.olauncher.helper.showToast
import app.olauncher.helper.uninstall
import java.text.Normalizer

class AppDrawerFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager

    private var flag = Constants.FLAG_LAUNCH_APP
    private var canRename = false
    private var scrollToLetter = ""
    private var currentAppList: List<AppModel>? = null
    private var currentPrivateSpaceApps: List<AppModel>? = null
    private var currentPrivateSpaceLocked: Boolean = true
    private var currentPrivateSpaceAvailable: Boolean = false
    private val indexLabels: MutableList<String> = mutableListOf()
    private val indexPositions: MutableMap<String, Int> = mutableMapOf()
    private var isIndexDragging = false

    private val viewModel: MainViewModel by activityViewModels()
    private var _binding: FragmentAppDrawerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAppDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        arguments?.let {
            flag = it.getInt(Constants.Key.FLAG, Constants.FLAG_LAUNCH_APP)
            canRename = it.getBoolean(Constants.Key.RENAME, false)
            scrollToLetter = it.getString(Constants.Key.SCROLL_TO_LETTER, "") ?: ""
        }

        initViews()
        initSearch()
        initAdapter()
        initObservers()
        initClickListeners()
        initIndex()
        // 選択されたフォントを適用
        (binding.root as? ViewGroup)?.applyFontFamily(prefs.fontFamily)

        applyLiquidGlassEffect(true)
    }

    private fun applyLiquidGlassEffect(enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val window = requireActivity().window
            if (enable) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val attributes = window.attributes
                attributes.blurBehindRadius = 80
                window.attributes = attributes
                binding.blurView?.visibility = View.GONE
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val attributes = window.attributes
                attributes.blurBehindRadius = 0
                window.attributes = attributes
            }
        } else {
            if (enable) {
                binding.blurView?.visibility = View.VISIBLE
                try {
                    val decorView = requireActivity().window.decorView
                    val rootView = decorView.findViewById<ViewGroup>(android.R.id.content)
                    val windowBackground = decorView.background
                    binding.blurView?.setupWith(rootView, RenderScriptBlur(requireContext()))
                        ?.setFrameClearDrawable(windowBackground)
                        ?.setBlurRadius(20f)
                } catch (e: Exception) {
                    binding.blurView?.visibility = View.GONE
                }
            } else {
                binding.blurView?.visibility = View.GONE
            }
        }
    }

    private fun initViews() {
        if (flag == Constants.FLAG_HIDDEN_APPS)
            binding.search.queryHint = getString(R.string.hidden_apps)
        else if (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_CALENDAR_APP)
            binding.search.queryHint = "Please select an app"
        try {
            val searchTextView = binding.search.findViewById<TextView>(R.id.search_src_text)
            if (searchTextView != null) searchTextView.gravity = prefs.appLabelAlignment
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initSearch() {
        binding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query?.startsWith("!") == true)
                    requireContext().openUrl(Constants.URL_DUCK_SEARCH + query.replace(" ", "%20"))
                else if (adapter.itemCount == 0)
                    requireContext().openSearch(query?.trim())
                else
                    adapter.launchFirstInList()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                try {
                    adapter.filter.filter(newText)
                    binding.appRename.visibility =
                        if (canRename && newText.isNotBlank()) View.VISIBLE else View.GONE
                    updateIndexVisibility(newText)
                    return true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return false
            }
        })
    }

    private fun initAdapter() {
        adapter = AppDrawerAdapter(
            flag,
            prefs.appLabelAlignment,
            appClickListener = { appModel ->
                viewModel.selectedApp(appModel, flag)
                if (flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS)
                    findNavController().popBackStack(R.id.mainFragment, false)
                else
                    findNavController().popBackStack()
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
                        if (isPrivateSpaceProfile(requireContext(), appModel.user)) {
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
            appHideListener = { appModel, position ->
                if (appModel is AppModel.PinnedShortcut) {
                    requireContext().showToast("Hiding pinned shortcuts is not supported")
                    return@AppDrawerAdapter
                }
                adapter.appFilteredList.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.appsList.remove(appModel)

                val newSet = mutableSetOf<String>()
                newSet.addAll(prefs.hiddenApps)
                if (flag == Constants.FLAG_HIDDEN_APPS) {
                    newSet.remove(appModel.appPackage)
                    newSet.remove(appModel.appPackage + "|" + appModel.user.toString())
                } else
                    newSet.add(appModel.appPackage + "|" + appModel.user.toString())

                prefs.hiddenApps = newSet
                if (newSet.isEmpty())
                    findNavController().popBackStack()
                if (prefs.firstHide) {
                    binding.search.hideKeyboard()
                    prefs.firstHide = false
                    viewModel.showDialog.postValue(Constants.Dialog.HIDDEN)
                    findNavController().navigate(R.id.action_appListFragment_to_settingsFragment2)
                }
                viewModel.getAppList()
                viewModel.getHiddenApps()
            },
            appRenameListener = { appModel, renameLabel ->
                val identifier = when (appModel) {
                    is AppModel.PinnedShortcut -> appModel.shortcutId
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
                if (overScroll < -10 && binding.recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING)
                    checkMessageAndExit()
                return scrollRange
            }
        }

        binding.recyclerView.layoutManager = linearLayoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addOnScrollListener(getRecyclerViewOnScrollListener())
        binding.recyclerView.itemAnimator = null
        if (requireContext().isEinkDisplay().not())
            binding.recyclerView.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_cascade)
    }

    private fun initObservers() {
        viewModel.firstOpen.observe(viewLifecycleOwner) {
        }
        if (flag == Constants.FLAG_HIDDEN_APPS) {
            viewModel.hiddenApps.observe(viewLifecycleOwner) {
                it?.let {
                    adapter.setAppList(it.toMutableList())
                    updateAppIndex(it)
                }
            }
        } else {
            viewModel.appList.observe(viewLifecycleOwner) {
                currentAppList = it
                updateCombinedAppList()
            }
            if (flag == Constants.FLAG_LAUNCH_APP) {
                viewModel.privateSpaceAvailable.observe(viewLifecycleOwner) {
                    currentPrivateSpaceAvailable = it
                    updateCombinedAppList()
                }
                viewModel.privateSpaceLocked.observe(viewLifecycleOwner) {
                    currentPrivateSpaceLocked = it
                    updateCombinedAppList()
                }
                viewModel.privateSpaceApps.observe(viewLifecycleOwner) {
                    currentPrivateSpaceApps = it
                    updateCombinedAppList()
                }
            }
        }
    }

    private fun updateCombinedAppList() {
        val apps = currentAppList ?: return
        val combined = apps.toMutableList()

        if (flag == Constants.FLAG_LAUNCH_APP && currentPrivateSpaceAvailable) {
            combined.add(AppModel.PrivateSpaceHeader(isLocked = currentPrivateSpaceLocked))
            if (!currentPrivateSpaceLocked) {
                currentPrivateSpaceApps?.let { combined.addAll(it) }
            }
        }

        adapter.setAppList(combined)
        updateAppIndex(combined)
        adapter.filter.filter(binding.search.query)

        // Niagar風: scrollToLetterが指定されていたら該当の位置へスクロール
        if (scrollToLetter.isNotBlank()) {
            binding.recyclerView.post {
                scrollToPositionForLetter(scrollToLetter)
            }
        }
    }

    private fun initClickListeners() {
        binding.appRename.setOnClickListener {
            val name = binding.search.query.toString().trim()
            if (name.isEmpty()) {
                requireContext().showToast(getString(R.string.type_a_new_app_name_first))
                binding.search.showKeyboard()
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
            findNavController().popBackStack()
        }
    }

    private fun getRecyclerViewOnScrollListener(): RecyclerView.OnScrollListener {
        return object : RecyclerView.OnScrollListener() {

            var onTop = false

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                when (newState) {

                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        onTop = !recyclerView.canScrollVertically(-1)
                        if (onTop)
                            binding.search.hideKeyboard()
                    }

                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (!recyclerView.canScrollVertically(1))
                            binding.search.hideKeyboard()
                    }
                }
            }
        }
    }

    // ── Niagara風 アルファベットインデックス（波アニメーション） ──

    private fun initIndex() {
        val appIndex = binding.appIndex ?: return
        appIndex.setOnTouchListener { _, event ->
            handleIndexTouch(event)
        }
    }

    private fun handleIndexTouch(event: MotionEvent): Boolean {
        val appIndex = binding.appIndex ?: return false
        if (indexLabels.isEmpty()) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isIndexDragging = true
                showLetterPopup(getIndexLabelAt(event, appIndex))
                applyWaveEffect(event, appIndex)
                scrollToIndexLabel(event, appIndex)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isIndexDragging) return false
                showLetterPopup(getIndexLabelAt(event, appIndex))
                applyWaveEffect(event, appIndex)
                scrollToIndexLabel(event, appIndex)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isIndexDragging) return false
                isIndexDragging = false
                hideLetterPopup()
                resetWaveEffect(appIndex)
                return true
            }
        }
        return false
    }

    private fun getIndexLabelAt(event: MotionEvent, appIndex: LinearLayout): String {
        val viewHeight = (appIndex.height - appIndex.paddingTop - appIndex.paddingBottom).coerceAtLeast(1)
        val y = (event.y - appIndex.paddingTop).coerceIn(0f, viewHeight.toFloat())
        val index = ((y / viewHeight) * indexLabels.size).toInt().coerceIn(0, indexLabels.size - 1)
        return indexLabels[index]
    }

    /**
     * Niagara式 波アニメーション: 指の近くの文字を拡大
     */
    private fun applyWaveEffect(event: MotionEvent, appIndex: LinearLayout) {
        val viewHeight = (appIndex.height - appIndex.paddingTop - appIndex.paddingBottom).coerceAtLeast(1)
        val touchY = (event.y - appIndex.paddingTop).coerceIn(0f, viewHeight.toFloat())
        val touchProgress = touchY / viewHeight
        val centerIndex = (touchProgress * indexLabels.size).toInt().coerceIn(0, indexLabels.size - 1)

        for (i in 0 until appIndex.childCount) {
            val child = appIndex.getChildAt(i) as? TextView ?: continue
            val distance = Math.abs(i - centerIndex).toFloat()
            val scale = when {
                distance <= 1f -> 1.0f + (1f - distance / 1f) * 1.2f
                distance <= 3f -> 1.0f + (1f - (distance - 1f) / 2f) * 0.4f
                else -> 1.0f
            }.coerceIn(0.8f, 2.5f)
            val alpha = when {
                distance <= 1f -> 1.0f
                distance <= 3f -> 1.0f - (distance - 1f) * 0.2f
                else -> 0.4f
            }.coerceIn(0.3f, 1.0f)

            child.scaleX = scale
            child.scaleY = scale
            child.alpha = alpha
        }
    }

    private fun resetWaveEffect(appIndex: LinearLayout) {
        for (i in 0 until appIndex.childCount) {
            val child = appIndex.getChildAt(i) as? TextView ?: continue
            child.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(0.6f)
                .setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }

    private fun scrollToIndexLabel(event: MotionEvent, appIndex: LinearLayout) {
        val viewHeight = (appIndex.height - appIndex.paddingTop - appIndex.paddingBottom).coerceAtLeast(1)
        val y = (event.y - appIndex.paddingTop).coerceIn(0f, viewHeight.toFloat())
        val index = ((y / viewHeight) * indexLabels.size).toInt().coerceIn(0, indexLabels.size - 1)
        val label = indexLabels[index]
        indexPositions[label]?.let { position ->
            binding.recyclerView.stopScroll()
            linearLayoutManager.scrollToPositionWithOffset(position, 0)
        }
    }

    /**
     * scrollToLetterで指定された文字の位置へスクロール
     */
    private fun scrollToPositionForLetter(letter: String) {
        val target = letter.uppercase()
        // 完全一致を探す
        var bestPosition = -1
        for ((label, position) in indexPositions) {
            if (label.equals(target, ignoreCase = true)) {
                bestPosition = position
                break
            }
        }
        // 前方一致で探す
        if (bestPosition < 0) {
            for ((label, position) in indexPositions) {
                if (label.startsWith(target.firstOrNull()?.toString() ?: "", ignoreCase = true)) {
                    bestPosition = position
                    break
                }
            }
        }
        if (bestPosition >= 0) {
            linearLayoutManager.scrollToPositionWithOffset(bestPosition, 0)
        }
    }

    // ── レターポップアップ ──

    private fun showLetterPopup(letter: String) {
        val popup = binding.letterPopup ?: return
        popup.text = letter
        popup.visibility = View.VISIBLE
        popup.animate()
            .alpha(1f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun hideLetterPopup() {
        val popup = binding.letterPopup ?: return
        popup.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction { popup.visibility = View.GONE }
            .start()
    }

    // ── インデックス管理 ──

    private fun updateAppIndex(apps: List<AppModel>) {
        val labels = LinkedHashMap<String, Int>()
        apps.forEachIndexed { index, app ->
            if (app is AppModel.PrivateSpaceHeader) return@forEachIndexed
            val label = app.appLabel.trim()
            if (label.isBlank()) return@forEachIndexed
            val key = getIndexKey(label)
            if (!labels.containsKey(key)) labels[key] = index
        }
        indexLabels.clear()
        indexLabels.addAll(labels.keys)
        indexPositions.clear()
        indexPositions.putAll(labels)
        renderIndexLabels()
        updateIndexVisibility(binding.search.query)
    }

    private fun renderIndexLabels() {
        val appIndex = binding.appIndex ?: return
        appIndex.removeAllViews()
        if (indexLabels.isEmpty()) return
        val context = requireContext()
        indexLabels.forEach { label ->
            val textView = TextView(context).apply {
                text = label
                TextViewCompat.setTextAppearance(this, R.style.TextSmall)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9f)
                pivotX = 0f
                pivotY = 0f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                alpha = 0.6f
                tag = label
            }
            appIndex.addView(textView)
        }
    }

    private fun updateIndexVisibility(query: CharSequence?) {
        // Niagara: インデックスは常に表示（検索中も消さない）
        binding.appIndex?.isVisible = indexLabels.isNotEmpty()
    }

    private fun getIndexKey(label: String): String {
        val normalized = Normalizer.normalize(label, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .trim()
        val firstChar = normalized.firstOrNull()?.uppercaseChar() ?: return "#"
        return when {
            firstChar.isLetter() -> firstChar.toString()
            firstChar.isDigit() -> "0-9"
            else -> "#"
        }
    }

    private fun checkMessageAndExit() {
        findNavController().popBackStack()
        if (flag == Constants.FLAG_LAUNCH_APP)
            viewModel.checkForMessages.call()
    }

    override fun onStart() {
        super.onStart()
        // Niagara: キーボードは自動表示しない。検索はあくまでオプション
        // アルファベットインデックスがメインの操作手段
    }

    override fun onStop() {
        binding.search.hideKeyboard()
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        applyLiquidGlassEffect(false)
        _binding = null
    }
}
