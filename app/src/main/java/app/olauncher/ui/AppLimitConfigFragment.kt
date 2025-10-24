package app.olauncher.ui

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.AppLimitModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentAppLimitConfigBinding
import app.olauncher.helper.showToast

class AppLimitConfigFragment : Fragment() {

  private lateinit var prefs: Prefs
  private var appPackage: String = ""
  private var appName: String = ""
  private var startHour: Int = 22
  private var startMinute: Int = 0
  private var endHour: Int = 6
  private var endMinute: Int = 0
  private var lockDuringLimit: Boolean = false
  private var existingLimit: AppLimitModel? = null

  private var _binding: FragmentAppLimitConfigBinding? = null
  private val binding
    get() = _binding!!

  override fun onCreateView(
          inflater: LayoutInflater,
          container: ViewGroup?,
          savedInstanceState: Bundle?
  ): View {
    _binding = FragmentAppLimitConfigBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    prefs = Prefs(requireContext())

    arguments?.let {
      appPackage = it.getString(Constants.Key.APP_PACKAGE, "")
      appName = it.getString(Constants.Key.APP_NAME, "")
    }

    loadExistingLimit()
    initViews()
    initClickListeners()
  }

  private fun loadExistingLimit() {
    existingLimit = prefs.getAppLimit(appPackage)
    existingLimit?.let { limit ->
      startHour = limit.startHour
      startMinute = limit.startMinute
      endHour = limit.endHour
      endMinute = limit.endMinute
      lockDuringLimit = limit.lockDuringLimit
    }
  }

  private fun initViews() {
    binding.tvAppName.text = appName
    updateTimeDisplays()
    updateLockDisplay()

    // Show delete button only if editing existing limit
    binding.btnDelete.isVisible = existingLimit != null
  }

  private fun initClickListeners() {
    binding.tvStartTime.setOnClickListener {
      if (canEdit()) {
        showTimePicker(startHour, startMinute) { hour, minute ->
          startHour = hour
          startMinute = minute
          updateTimeDisplays()
        }
      }
    }

    binding.tvEndTime.setOnClickListener {
      if (canEdit()) {
        showTimePicker(endHour, endMinute) { hour, minute ->
          endHour = hour
          endMinute = minute
          updateTimeDisplays()
        }
      }
    }

    binding.tvLockDuringLimit.setOnClickListener {
      if (canEdit()) {
        lockDuringLimit = !lockDuringLimit
        updateLockDisplay()
      }
    }

    binding.btnSave.setOnClickListener {
      if (canEdit()) {
        saveLimit()
      }
    }

    binding.btnDelete.setOnClickListener {
      if (canEdit()) {
        deleteLimit()
      }
    }
  }

  private fun canEdit(): Boolean {
    // Check if limit is currently active and locked
    existingLimit?.let { limit ->
      if (limit.isCurrentlyBlocked() && limit.lockDuringLimit) {
        requireContext().showToast(getString(R.string.limit_is_locked))
        return false
      }
    }
    return true
  }

  private fun showTimePicker(hour: Int, minute: Int, onTimeSet: (Int, Int) -> Unit) {
    TimePickerDialog(
                    requireContext(),
                    { _, selectedHour, selectedMinute -> onTimeSet(selectedHour, selectedMinute) },
                    hour,
                    minute,
                    true // 24-hour format
            )
            .show()
  }

  private fun updateTimeDisplays() {
    binding.tvStartTime.text = String.format("%02d:%02d", startHour, startMinute)
    binding.tvEndTime.text = String.format("%02d:%02d", endHour, endMinute)
  }

  private fun updateLockDisplay() {
    binding.tvLockDuringLimit.text =
            if (lockDuringLimit) {
              getString(R.string.lock_during_limit_on)
            } else {
              getString(R.string.lock_during_limit_off)
            }
  }

  private fun saveLimit() {
    val limit =
            AppLimitModel(
                    appPackage = appPackage,
                    appName = appName,
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute,
                    lockDuringLimit = lockDuringLimit
            )

    prefs.addAppLimit(limit)
    requireContext().showToast("App limit saved")
    findNavController().popBackStack()
  }

  private fun deleteLimit() {
    prefs.removeAppLimit(appPackage)
    requireContext().showToast("App limit removed")
    findNavController().popBackStack()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
