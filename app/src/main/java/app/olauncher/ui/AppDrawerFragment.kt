package app.olauncher.ui

import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
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
import app.olauncher.data.Constants.CharacterIndicator
import app.olauncher.data.DrawerCharacterModel
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentAppDrawerBinding
import app.olauncher.helper.AppFilterHelper
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.openSearch
import app.olauncher.helper.openUrl
import app.olauncher.helper.searchOnPlayStore
import app.olauncher.helper.showKeyboard
import app.olauncher.helper.showToast
import app.olauncher.helper.uninstall
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class AppDrawerFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager
    private lateinit var drawerCharacterAdapter: DrawerCharacterAdapter

    private var flag = Constants.FLAG_LAUNCH_APP
    private var canRename = false

    private val viewModel: MainViewModel by activityViewModels()
    private var _binding: FragmentAppDrawerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
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
        }
        initViews()
        initSearch()
        initAdapter()
        initObservers()
        initClickListeners()
    }

    private fun setAppDrawerPortraitMargins(center: Boolean) {
        val centerValue = if (center) 5 else 48

        val rv = binding.recyclerView
        val params = rv.layoutParams as FrameLayout.LayoutParams
        val scale = resources.displayMetrics.density
        val marginTop = (180 * scale).toInt()
        val marginBottom = (24 * scale).toInt()
        val marginRight = (centerValue * scale).toInt()
        params.setMargins(0, marginTop, marginRight, marginBottom)
        rv.layoutParams = params
    }

    private fun setAppDrawerLandMargins() {
        val rv = binding.recyclerView
        val params = rv.layoutParams as FrameLayout.LayoutParams
        val scale = resources.displayMetrics.density
        val marginTop = (80 * scale).toInt()
        val marginBottom = 0
        val marginRight = (56 * scale).toInt()
        val marginLeft = (56 * scale).toInt()
        params.setMargins(marginLeft, marginTop, marginRight, marginBottom)
        rv.layoutParams = params
    }

    private fun setIndicatorMargins(x:Float,y:Float,isLast:Boolean,isFirst:Boolean) {
        val lastValue = if (isLast) 6 else 3
        val indicator = binding.characterIndicator
        val params = indicator.layoutParams as LinearLayout.LayoutParams
        val scale = resources.displayMetrics.density
        val marginTop =if (isFirst) (y + (1 * scale)).toInt() else (y - (lastValue * scale)).toInt()
        val marginRight = (8 * scale).toInt()
        val marginLeft = x.toInt()
        val marginBottom = 0
        params.setMargins(marginLeft, marginTop, marginRight, marginBottom)
        indicator.layoutParams = params
    }


    private fun setIndicatorLayoutLandMargins() {
        val indicatorLayout = binding.indicatorLayout
        val params = indicatorLayout.layoutParams as FrameLayout.LayoutParams
        val scale = resources.displayMetrics.density
        val marginTop = (6 * scale).toInt()
        val marginRight = 0
        val marginLeft = 0
        val marginBottom = (6 * scale).toInt()
        params.setMargins(marginLeft, marginTop, marginRight, marginBottom)
        indicatorLayout.layoutParams = params
    }


    private fun initViews() {
        binding.characterRecyclerView.isVisible = prefs.autoShowKeyboard.not()

        if (requireContext().resources.configuration.orientation == ORIENTATION_PORTRAIT) {
            setAppDrawerPortraitMargins(prefs.appLabelAlignment == Gravity.CENTER)
        }else{
            setAppDrawerLandMargins()
            setIndicatorLayoutLandMargins()
        }

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
                else if (adapter.itemCount == 0 && requireContext().searchOnPlayStore(query?.trim()).not())
                    requireContext().openSearch(query?.trim())
                else
                    adapter.launchFirstInList()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                try {
                    adapter.filter.filter(newText)
                    binding.appDrawerTip.visibility = View.GONE
                    binding.appRename.visibility = if (canRename && newText.isNotBlank()) View.VISIBLE else View.GONE
                    return true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return false
            }
        })
    }

    private fun initAdapter() {

        val appFilterHelper = object : AppFilterHelper {
            override fun onAppFiltered(items: List<AppModel>) {
                submitDrawerCharacters(items)
            }
        }
        drawerCharacterAdapter = DrawerCharacterAdapter()

        adapter = AppDrawerAdapter(
            flag,
            appFilterHelper,
            prefs.appLabelAlignment,
            appClickListener = {
                if (it.appPackage.isEmpty())
                    return@AppDrawerAdapter
                val navigationController = findNavController()
                val launch = {
                    viewModel.selectedApp(it, flag)
                    if (flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS)
                        navigationController.popBackStack(R.id.mainFragment, false)
                    else
                        navigationController.popBackStack()
                }
                val launchDelay = prefs.getAppLaunchDelay(it.appPackage)
                if ((flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS) && launchDelay > 0)
                    findNavController().navigate(
                        R.id.action_appListFragment_to_timerFragment,
                        bundleOf(
                            Constants.Key.LAUNCH to launch,
                            Constants.Key.LAUNCH_DELAY to launchDelay,
                            Constants.Key.APP_NAME to it.appLabel
                        )
                    )
                else
                    launch()
            },
            appInfoListener = {
                openAppInfo(
                    requireContext(),
                    it.user,
                    it.appPackage
                )
                findNavController().popBackStack(R.id.mainFragment, false)
            },
            appDeleteListener = {
                requireContext().apply {
                    if (isSystemApp(it.appPackage))
                        showToast(getString(R.string.system_app_cannot_delete))
                    else
                        uninstall(it.appPackage)
                }
            },
            appHideListener = { appModel, position ->
                adapter.appFilteredList.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.appsList.remove(appModel)

                val newSet = mutableSetOf<String>()
                newSet.addAll(prefs.hiddenApps)
                if (flag == Constants.FLAG_HIDDEN_APPS) {
                    newSet.remove(appModel.appPackage) // for backward compatibility
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
            },
            appRenameListener = { appModel, renameLabel ->
                prefs.setAppRenameLabel(appModel.appPackage, renameLabel)
                viewModel.getAppList()
            },
            appChangeLaunchDelayListener = { appModel, startDelay ->
                prefs.setAppLaunchDelay(appModel.appPackage, startDelay)
            }
        )

        linearLayoutManager = object : LinearLayoutManager(requireContext()) {
            override fun scrollVerticallyBy(
                dx: Int,
                recycler: Recycler,
                state: RecyclerView.State
            ): Int {
                val scrollRange = super.scrollVerticallyBy(dx, recycler, state)
                val overScroll = dx - scrollRange
                if (overScroll < -10 && binding.recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING)
                    checkMessageAndExit()
                return scrollRange
            }
        }
        binding.characterRecyclerView.addOnItemTouchListener(
            DrawerCharacterAdapter.CharacterTouchListener(drawerCharacterAdapter) { char,mode ,pos->

                if (mode != CharacterIndicator.HIDE) {
                    binding.characterIndicator.apply{
                        setIndicatorMargins(
                            pos.first,
                            pos.second,
                            char.equals("Z", true),
                            char.equals("A", true)
                        )
                        text = char
                        isVisible = true
                    }
                    viewModel.updateRangeDrawerCharacterList(char)
                    val matchIndex = if (char == "#") {
                        0
                    } else {
                        val match = adapter.currentList.find {
                            char.equals(it.appLabel.first().toString(), true)
                        }
                        adapter.currentList.indexOf(match)
                    }
                    linearLayoutManager.scrollToPositionWithOffset(matchIndex, 0)
                }

                if (mode == CharacterIndicator.HIDE) {
                    lifecycleScope.launch {
                        delay(1000L)
                        binding.characterIndicator.isVisible = false
                    }

                }

            })


        binding.recyclerView.layoutManager = linearLayoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addOnScrollListener(getRecyclerViewOnScrollListener())
        binding.recyclerView.itemAnimator = null
        if (requireContext().isEinkDisplay().not())
            binding.recyclerView.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_from_bottom)
        binding.characterRecyclerView.adapter = drawerCharacterAdapter
    }

    private fun initObservers() {
        viewModel.firstOpen.observe(viewLifecycleOwner) {
            if (it && flag == Constants.FLAG_LAUNCH_APP) {
                binding.appDrawerTip.visibility = View.VISIBLE
                binding.appDrawerTip.isSelected = true
            }
        }
        if (flag == Constants.FLAG_HIDDEN_APPS) {
            viewModel.hiddenApps.observe(viewLifecycleOwner) {
                it?.let {
                    adapter.setAppList(it.toMutableList())
                    submitDrawerCharacters(it)
                }
            }
        } else {
            viewModel.appList.observe(viewLifecycleOwner) {
                it?.let { appModels ->
                    adapter.setAppList(appModels.toMutableList())
                    adapter.filter.filter(binding.search.query)
                    submitDrawerCharacters(appModels)
                }
            }
        }

        viewModel.drawerCharacterList.observe(viewLifecycleOwner) { characters ->
            drawerCharacterAdapter.submitList(characters)
        }
    }

    private fun initClickListeners() {
        binding.appDrawerTip.setOnClickListener {
            binding.appDrawerTip.isSelected = false
            binding.appDrawerTip.isSelected = true
        }
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
                        if (onTop) binding.search.hideKeyboard()
                        // if (onTop && !recyclerView.canScrollVertically(1))
                        //     checkMessageAndExit()
                    }

                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (!recyclerView.canScrollVertically(1)) {
                            binding.search.hideKeyboard()
                        } else if (!recyclerView.canScrollVertically(-1)) {
                            if (!onTop) binding.search.showKeyboard(prefs.autoShowKeyboard)
                            // if (onTop) checkMessageAndExit()
                            // else binding.search.showKeyboard(prefs.autoShowKeyboard)
                        }
                    }
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val visiblePosition = linearLayoutManager.findFirstVisibleItemPosition()
                val position = if (visiblePosition >= 0) visiblePosition else 0
                val item = adapter.currentList[position]
                viewModel.updateRangeDrawerCharacterList(item.appLabel.first().toString())
            }
        }
    }

    private fun checkMessageAndExit() {
        findNavController().popBackStack()
        if (flag == Constants.FLAG_LAUNCH_APP)
            viewModel.checkForMessages.call()
    }

    private fun submitDrawerCharacters(drawerItems: List<AppModel>) {
        if (drawerItems.isEmpty()) {
            viewModel.updateDrawerCharacterList(emptyList())
            return
        }


        val charRegex = Regex("[0-9\\\\$&+,:;=?@#|/'<>.^*()%!-]")
        val emojiRegex = Regex("\\p{So}+")
        val emojiRegex2 = Regex("[\uD800-\uDBFF\uDC00-\uDFFF]+")

        val firstVisibleItemPosition =
            linearLayoutManager.findFirstCompletelyVisibleItemPosition()

        val position =
            if (firstVisibleItemPosition >= 0) firstVisibleItemPosition else 0
        val firstVisibleItem =
            drawerItems[position]


        val drawerCharacters =
            drawerItems.filter { it.appLabel.isNotEmpty() }.map { char ->
                val firstLetter = char.appLabel.first().toString()
                val regexMatch = charRegex.matches(firstLetter) || emojiRegex.matches(
                    char.appLabel.first().toString()
                ) || emojiRegex2.matches(char.appLabel.first().toString())

                if (regexMatch) "#" else firstLetter.uppercase()
            }.toSet()
                .map { str ->
                    DrawerCharacterModel(str, str.equals(firstVisibleItem.appLabel.first().toString(),true))
                }

        viewModel.updateDrawerCharacterList(drawerCharacters)

    }


    override fun onStart() {
        super.onStart()
        binding.search.showKeyboard(prefs.autoShowKeyboard)
    }

    override fun onStop() {
        binding.search.hideKeyboard()
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}