package app.olauncher.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.data.AppModel
import app.olauncher.helper.MainViewModel
import app.olauncher.R
import app.olauncher.helper.openAppInfo
import kotlinx.android.synthetic.main.fragment_app.*


class AppListFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_app, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val flag = arguments?.getInt("flag") ?: 0
        val viewModel = activity?.run {
            ViewModelProvider(this).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")
        val appAdapter = AppListAdapter(
            flag,
            appClickListener(viewModel, flag),
            appLongPressListener()
        )

        initViewModel(viewModel, appAdapter)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = appAdapter
        recyclerView.addOnScrollListener(getRecyclerViewOnScrollListener())

        search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                appAdapter.filter.filter(newText?.trim())
                return false
            }
        })
    }

    private fun initViewModel(viewModel: MainViewModel, appAdapter: AppListAdapter) {
        viewModel.appList.observe(viewLifecycleOwner, Observer<List<AppModel>> {
            val animation =
                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_from_bottom)
            recyclerView.layoutAnimation = animation
            appAdapter.setAppList(it)
            search.visibility = View.VISIBLE
        })
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

    private fun View.showKeyboard() {
        view?.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    private fun appClickListener(viewModel: MainViewModel, flag: Int):
                (appModel: AppModel) -> Unit =
        { appModel ->
            viewModel.selectedApp(appModel, flag)
            findNavController().popBackStack()
        }

    private fun appLongPressListener(): (appModel: AppModel) -> Unit =
        { appModel ->
            openAppInfo(
                requireContext(),
                appModel.appPackage
            )
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
                        if (!recyclerView.canScrollVertically(-1)) {
                            if (onTop) findNavController().popBackStack()
                            else search.showKeyboard()
                        }
                    }
                }
            }
        }
    }
}