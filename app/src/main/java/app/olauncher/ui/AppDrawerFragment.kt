package app.olauncher.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentAppDrawerBinding
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.openUrl
import app.olauncher.helper.showToastShort

class AppDrawerFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel

    private var _binding: FragmentAppDrawerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        val flag = arguments?.getInt(Constants.Key.FLAG, Constants.FLAG_LAUNCH_APP) ?: Constants.FLAG_LAUNCH_APP
        val rename = arguments?.getBoolean(Constants.Key.RENAME, false) ?: false
        if (rename) {
            binding.appRename.setOnClickListener { renameListener(flag) }
            if (prefs.renameTipShown.not()) {
                binding.appDrawerTip.text = getString(R.string.tip_start_typing_for_rename)
                binding.appDrawerTip.visibility = View.VISIBLE
                prefs.renameTipShown = true
            }
        }

        val appAdapter = AppDrawerAdapter(
            flag,
            prefs.appLabelAlignment,
            appClickListener(viewModel, flag),
            appInfoListener(),
            appShowHideListener()
        )

        val searchTextView = binding.search.findViewById<TextView>(R.id.search_src_text)
        if (searchTextView != null) searchTextView.gravity = prefs.appLabelAlignment

        initViewModel(flag, viewModel, appAdapter)

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = appAdapter
        binding.recyclerView.addOnScrollListener(getRecyclerViewOnScrollListener())

        if (flag == Constants.FLAG_HIDDEN_APPS) binding.search.queryHint = "Hidden apps"
        binding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query?.startsWith("!") == true)
                    requireContext().openUrl(Constants.URL_DUCK_SEARCH + query.replace(" ", "%20"))
                else
                    appAdapter.launchFirstInList()
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let {
                    binding.appRename.isVisible = rename && it.trim().isNotEmpty()
                    binding.appDrawerTip.visibility = View.GONE
                    appAdapter.filter.filter(it.trim())
                }
                return false
            }
        })
        binding.appDrawerTip.setOnClickListener {
            binding.appDrawerTip.isSelected = false
            binding.appDrawerTip.isSelected = true
        }
    }

    private fun initViewModel(flag: Int, viewModel: MainViewModel, appAdapter: AppDrawerAdapter) {
        viewModel.hiddenApps.observe(viewLifecycleOwner, Observer {
            if (flag != Constants.FLAG_HIDDEN_APPS) return@Observer
            if (it.isNullOrEmpty()) {
                findNavController().popBackStack()
                return@Observer
            }
            populateAppList(it, appAdapter)
        })

        viewModel.appList.observe(viewLifecycleOwner, Observer {
            if (flag == Constants.FLAG_HIDDEN_APPS) return@Observer
            if (it == appAdapter.appsList) return@Observer
            populateAppList(it, appAdapter)
        })

        viewModel.firstOpen.observe(viewLifecycleOwner) {
            if (it) binding.appDrawerTip.visibility = View.VISIBLE
            binding.appDrawerTip.isSelected = true
        }
    }

    override fun onStart() {
        super.onStart()
        binding.search.showKeyboard()
    }

    override fun onStop() {
        binding.search.hideKeyboard()
        super.onStop()
    }

    private fun View.hideKeyboard() {
        view?.clearFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun View.showKeyboard(forceShowKeyboard: Boolean = false) {
        if (!prefs.autoShowKeyboard && !forceShowKeyboard) return
        binding.search.postDelayed({
            binding.search.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
        }, 100)
    }

    private fun populateAppList(apps: List<AppModel>, appAdapter: AppDrawerAdapter) {
//        val animation = AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_from_bottom)
//        binding.recyclerView.layoutAnimation = animation
        appAdapter.setAppList(apps.toMutableList())
    }

    private fun appClickListener(viewModel: MainViewModel, flag: Int): (appModel: AppModel) -> Unit =
        { appModel ->
            viewModel.selectedApp(appModel, flag)
            if (flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS)
                findNavController().popBackStack(R.id.mainFragment, false)
            else
                findNavController().popBackStack()
        }

    private fun appInfoListener(): (appModel: AppModel) -> Unit =
        { appModel ->
            openAppInfo(
                requireContext(),
                appModel.user,
                appModel.appPackage
            )
            findNavController().popBackStack(R.id.mainFragment, false)
        }

    private fun appShowHideListener(): (flag: Int, appModel: AppModel) -> Unit =
        { flag, appModel ->
            val newSet = mutableSetOf<String>()
            newSet.addAll(prefs.hiddenApps)

            if (flag == Constants.FLAG_HIDDEN_APPS) {
                newSet.remove(appModel.appPackage) // for backward compatibility
                newSet.remove(appModel.appPackage + "|" + appModel.user.toString())
            } else newSet.add(appModel.appPackage + "|" + appModel.user.toString())

            prefs.hiddenApps = newSet

            if (newSet.isEmpty()) findNavController().popBackStack()
            if (prefs.firstHide) {
                prefs.firstHide = false
                viewModel.showMessageDialog(getString(R.string.hidden_apps_message))
                findNavController().navigate(R.id.action_appListFragment_to_settingsFragment2)
            }
        }

    private fun renameListener(flag: Int) {
        val name = binding.search.query.toString().trim()
        if (name.isEmpty()) {
            showToastShort(requireContext(), "Type a new app name first")
            binding.search.showKeyboard(true)
            return
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

    private fun getRecyclerViewOnScrollListener(): RecyclerView.OnScrollListener {
        return object : RecyclerView.OnScrollListener() {

            var onTop = false

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                when (newState) {

                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        onTop = !recyclerView.canScrollVertically(-1)
                        if (onTop) binding.search.hideKeyboard()
                        if (onTop && !recyclerView.canScrollVertically(1))
                            findNavController().popBackStack()
                    }

                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (!recyclerView.canScrollVertically(1)) {
                            binding.search.hideKeyboard()
                        } else if (!recyclerView.canScrollVertically(-1)) {
                            if (onTop) findNavController().popBackStack()
                            else binding.search.showKeyboard()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}