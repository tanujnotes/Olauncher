package app.olauncher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import kotlinx.android.synthetic.main.main_fragment.*

class MainFragment : Fragment(), View.OnClickListener {

    private lateinit var viewModel: MainViewModel
    private lateinit var prefs: Prefs

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        initUi()
        initObservers()
    }

    private fun initUi() {
        homeApp1.setOnClickListener(this)
        homeApp1.text = prefs.appName1
        homeApp2.text = prefs.appName2
        homeApp3.text = prefs.appName3
        homeApp4.text = prefs.appName4
    }

    private fun initObservers() {
        viewModel.selectedApp.observe(viewLifecycleOwner, Observer<AppModelPosition> {
            prefs.appName1 = it.appModel.appLabel
            initUi()
        })
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.homeApp1 -> view.findNavController().navigate(R.id.appListFragment)
        }
    }

}