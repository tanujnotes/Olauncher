package app.olauncher.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentAppLimitsBinding

class AppLimitsFragment : Fragment() {

  private lateinit var prefs: Prefs
  private lateinit var adapter: AppLimitsAdapter

  private var _binding: FragmentAppLimitsBinding? = null
  private val binding
    get() = _binding!!

  override fun onCreateView(
          inflater: LayoutInflater,
          container: ViewGroup?,
          savedInstanceState: Bundle?
  ): View {
    _binding = FragmentAppLimitsBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    prefs = Prefs(requireContext())

    initAdapter()
    initClickListeners()
    loadAppLimits()
  }

  override fun onResume() {
    super.onResume()
    loadAppLimits()
  }

  private fun initAdapter() {
    adapter = AppLimitsAdapter { appLimit ->
      // Navigate to edit screen
      findNavController()
              .navigate(
                      R.id.action_appLimitsFragment_to_appLimitConfigFragment,
                      bundleOf(
                              Constants.Key.APP_PACKAGE to appLimit.appPackage,
                              Constants.Key.APP_NAME to appLimit.appName
                      )
              )
    }

    binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
    binding.recyclerView.adapter = adapter
  }

  private fun initClickListeners() {
    binding.btnAddLimit.setOnClickListener {
      // Navigate to app selection screen
      findNavController()
              .navigate(
                      R.id.action_appLimitsFragment_to_appListFragment,
                      bundleOf(Constants.Key.FLAG to Constants.FLAG_SET_APP_LIMIT)
              )
    }
  }

  private fun loadAppLimits() {
    val limits = prefs.getAppLimits()
    if (limits.isEmpty()) {
      binding.tvNoLimits.isVisible = true
      binding.recyclerView.isVisible = false
    } else {
      binding.tvNoLimits.isVisible = false
      binding.recyclerView.isVisible = true
      adapter.setAppLimits(limits)
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
