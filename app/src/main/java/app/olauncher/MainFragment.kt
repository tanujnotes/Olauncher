package app.olauncher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.main_fragment.*

class MainFragment : Fragment(), View.OnClickListener, View.OnLongClickListener {

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
        initClickListeners()
        initObservers()

        mainLayout.setOnTouchListener(getSwipeGestureListener(requireContext()))
    }

    private fun initUi() {
        homeApp1.text = prefs.appName1
        homeApp2.text = prefs.appName2
        homeApp3.text = prefs.appName3
        homeApp4.text = prefs.appName4
    }

    private fun initClickListeners() {
        homeApp1.setOnClickListener(this)
        homeApp2.setOnClickListener(this)
        homeApp3.setOnClickListener(this)
        homeApp4.setOnClickListener(this)
        homeApp1.setOnLongClickListener(this)
        homeApp2.setOnLongClickListener(this)
        homeApp3.setOnLongClickListener(this)
        homeApp4.setOnLongClickListener(this)
    }

    private fun initObservers() {
        viewModel.selectedApp.observe(viewLifecycleOwner, Observer<AppModelWithFlag> {
            when (it.flag) {
                Constants.FLAG_SET_HOME_APP_1 -> {
                    prefs.appName1 = it.appModel.appLabel
                    prefs.appPackage1 = it.appModel.appPackage
                }
                Constants.FLAG_SET_HOME_APP_2 -> {
                    prefs.appName2 = it.appModel.appLabel
                    prefs.appPackage2 = it.appModel.appPackage
                }
                Constants.FLAG_SET_HOME_APP_3 -> {
                    prefs.appName3 = it.appModel.appLabel
                    prefs.appPackage3 = it.appModel.appPackage
                }
                Constants.FLAG_SET_HOME_APP_4 -> {
                    prefs.appName4 = it.appModel.appLabel
                    prefs.appPackage4 = it.appModel.appPackage
                }
            }
            initUi()
        })
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.homeApp1 -> if (prefs.appPackage1.isEmpty()) onLongClick(view) else launchApp(prefs.appPackage1)
            R.id.homeApp2 -> if (prefs.appPackage2.isEmpty()) onLongClick(view) else launchApp(prefs.appPackage2)
            R.id.homeApp3 -> if (prefs.appPackage3.isEmpty()) onLongClick(view) else launchApp(prefs.appPackage3)
            R.id.homeApp4 -> if (prefs.appPackage4.isEmpty()) onLongClick(view) else launchApp(prefs.appPackage4)
        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.homeApp1 -> showAppList(Constants.FLAG_SET_HOME_APP_1)
            R.id.homeApp2 -> showAppList(Constants.FLAG_SET_HOME_APP_2)
            R.id.homeApp3 -> showAppList(Constants.FLAG_SET_HOME_APP_3)
            R.id.homeApp4 -> showAppList(Constants.FLAG_SET_HOME_APP_4)
        }
        return true
    }

    private fun showAppList(flag: Int) {
        findNavController().navigate(
            R.id.action_mainFragment_to_appListFragment,
            bundleOf("flag" to flag)
        )
    }

    private fun launchApp(packageName: String) {
        val pm = context?.packageManager
        val intent: Intent? = pm?.getLaunchIntentForPackage(packageName)
        intent?.addCategory(Intent.CATEGORY_LAUNCHER)
        startActivity(intent)
    }

    private fun getSwipeGestureListener(context: Context): View.OnTouchListener {
        return object : OnSwipeTouchListener(context) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                Toast.makeText(context, "Swipe Left gesture", Toast.LENGTH_SHORT).show()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                Toast.makeText(context, "Swipe Right gesture", Toast.LENGTH_SHORT).show()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                Toast.makeText(context, "Swipe up gesture", Toast.LENGTH_SHORT).show()
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                Toast.makeText(context, "Swipe down gesture", Toast.LENGTH_SHORT).show()
            }
        }
    }
}