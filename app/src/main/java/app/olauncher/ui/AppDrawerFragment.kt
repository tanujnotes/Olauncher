package app.olauncher.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
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
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.openUrl
import app.olauncher.helper.showToastShort
import kotlinx.android.synthetic.main.fragment_app_drawer.*

class AppDrawerFragment : Fragment() {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        return inflater.inflate(R.layout.fragment_app_drawer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        val flag = arguments?.getInt("flag", Constants.FLAG_LAUNCH_APP) ?: Constants.FLAG_LAUNCH_APP
        val rename = arguments?.getBoolean("rename", false) ?: false
        if (rename) {
            appRename.setOnClickListener { renameListener(flag) }
            if (prefs.renameTipShown.not()) {
                appDrawerTip.text = getString(R.string.tip_start_typing_for_rename)
                appDrawerTip.visibility = View.VISIBLE
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

        val searchTextView = search.findViewById<TextView>(R.id.search_src_text)
        if (searchTextView != null) searchTextView.gravity = prefs.appLabelAlignment

        initViewModel(flag, viewModel, appAdapter)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = appAdapter
        recyclerView.addOnScrollListener(getRecyclerViewOnScrollListener())

        if (flag == Constants.FLAG_HIDDEN_APPS) search.queryHint = "Hidden apps"
        search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query?.startsWith("!") == true)
                    requireContext().openUrl(Constants.URL_DUCK_SEARCH + query.replace(" ", "%20"))
                else
                    appAdapter.launchFirstInList()
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let {
                    appAdapter.filter.filter(it.trim())
                    appRename.isVisible = rename && it.trim().isNotEmpty()
                    appDrawerTip.visibility = View.GONE
                }
                return false
            }
        })
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
            if (it.isNullOrEmpty()) {
                findNavController().popBackStack()
                return@Observer
            }
            if (it == appAdapter.appsList) return@Observer
            populateAppList(it, appAdapter)
        })

        viewModel.firstOpen.observe(viewLifecycleOwner) {
            if (it) appDrawerTip.visibility = View.VISIBLE
        }
    }

    override fun onStart() {
        super.onStart()
        search.showKeyboard()
    }

    override fun onStop() {
        search.hideKeyboard()
        super.onStop()
    }

    private fun View.hideKeyboard() {
        view?.clearFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun View.showKeyboard(forceShowKeyboard: Boolean = false) {
        if (!prefs.autoShowKeyboard && !forceShowKeyboard) return
        view?.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    private fun populateAppList(apps: List<AppModel>, appAdapter: AppDrawerAdapter) {
        val animation = AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_from_bottom)
        recyclerView.layoutAnimation = animation
        appAdapter.setAppList(apps.toMutableList())
    }

    private fun appClickListener(viewModel: MainViewModel, flag: Int): (appModel: AppModel) -> Unit =
        { appModel ->
            search.hideKeyboard()
            viewModel.selectedApp(appModel, flag)
            if (flag != Constants.FLAG_LAUNCH_APP && flag != Constants.FLAG_HIDDEN_APPS)
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
        val name = search.query.toString().trim()
        if (name.isEmpty()) {
            showToastShort(requireContext(), "Type a new app name first")
            search.showKeyboard(true)
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
                        if (onTop) search.hideKeyboard()
                    }

                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (!recyclerView.canScrollVertically(1)) {
                            search.hideKeyboard()
                        } else if (!recyclerView.canScrollVertically(-1)) {
                            if (onTop) findNavController().popBackStack()
                            else search.showKeyboard()
                        }
                    }
                }
            }
        }
    }
}