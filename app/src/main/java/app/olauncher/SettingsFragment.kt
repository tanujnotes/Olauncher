package app.olauncher

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import kotlinx.android.synthetic.main.fragment_settings.*


class SettingsFragment : Fragment(), View.OnClickListener {

    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = activity?.run {
            ViewModelProvider(this).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")
        viewModel.isOlauncherDefault()

        deviceManager =
            context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(requireContext(), DeviceAdmin::class.java)

        setLockModeText()
        initClickListeners()
        initObservers()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.setLauncher -> viewModel.resetDefaultLauncherApp(requireContext())
            R.id.textColor -> viewModel.switchTheme()
            R.id.toggleOnOff -> toggleLockMode()
        }
    }

    private fun setLockModeText() {
        val active: Boolean = deviceManager.isAdminActive(componentName)
        Prefs(requireContext()).lockModeOn = active
        if (active) toggleOnOff.text = getString(R.string.on)
        else toggleOnOff.text = getString(R.string.off)
    }

    private fun initClickListeners() {
        setLauncher.setOnClickListener(this)
        textColor.setOnClickListener(this)
        toggleOnOff.setOnClickListener(this)
    }

    private fun initObservers() {
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner, Observer<Boolean> {
            if (it) setLauncher.text = getString(R.string.change_default_launcher)
        })
        viewModel.isDarkModeOn.observe(viewLifecycleOwner, Observer {
            if (it) textColor.text = getString(R.string.white)
            else textColor.text = getString(R.string.black)
        })
    }

    private fun toggleLockMode() {
        val active: Boolean = deviceManager.isAdminActive(componentName)
        if (active) {
            deviceManager.removeActiveAdmin(componentName)
            Prefs(requireContext()).lockModeOn = false
            setLockModeText()
            showToastShort(requireContext(), "Admin permission removed")
        } else {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                getString(R.string.admin_permission_message)
            )
            activity?.startActivityForResult(intent, Constants.REQUEST_CODE_ENABLE_ADMIN)
        }
    }
}