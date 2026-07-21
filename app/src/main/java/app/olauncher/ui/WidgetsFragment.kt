package app.olauncher.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentWidgetsBinding
import app.olauncher.helper.WidgetHostManager
import app.olauncher.helper.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetsFragment : Fragment() {
    private var _binding: FragmentWidgetsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var hostManager: WidgetHostManager
    private lateinit var listAdapter: WidgetListAdapter
    private var pickerMode = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentWidgetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        hostManager = WidgetHostManager.get(requireContext())
        listAdapter = WidgetListAdapter(::onItemClicked)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = listAdapter
        binding.addWidget.setOnClickListener { showPicker() }
        viewModel.widgetActivityResult.observe(viewLifecycleOwner) { (requestCode, resultCode) ->
            handleWidgetResult(requestCode, resultCode)
        }
        showManagedWidgets()
    }

    private fun showManagedWidgets() {
        pickerMode = false
        binding.title.setText(R.string.widgets)
        val ids = hostManager.pruneStaleWidgets(prefs)
        binding.addWidget.isVisible = ids.size < Constants.Widget.MAX_WIDGETS
        lifecycleScope.launch {
            listAdapter.submitList(loadItems(ids))
        }
    }

    private fun showPicker() {
        if (prefs.widgetIdList.size >= Constants.Widget.MAX_WIDGETS) {
            requireContext().showToast(R.string.widget_limit_reached)
            return
        }
        pickerMode = true
        binding.title.setText(R.string.choose_widget)
        binding.addWidget.visibility = View.GONE
        lifecycleScope.launch {
            val providers = withContext(Dispatchers.IO) {
                hostManager.appWidgetManager.installedProviders
                    .sortedWith(compareBy({ appName(it.provider.packageName) }, { it.loadLabel(requireContext().packageManager).toString() }))
                    .map { info -> createListItem(null, info) }
            }
            if (_binding != null && pickerMode) listAdapter.submitList(providers)
        }
    }

    private suspend fun loadItems(ids: List<Int>): List<WidgetListItem> = withContext(Dispatchers.IO) {
        ids.mapNotNull { id ->
            hostManager.appWidgetManager.getAppWidgetInfo(id)?.let { createListItem(id, it) }
        }
    }

    private fun createListItem(id: Int?, info: android.appwidget.AppWidgetProviderInfo): WidgetListItem {
        val packageManager = requireContext().packageManager
        val density = resources.displayMetrics.densityDpi
        val preview = runCatching { info.loadPreviewImage(requireContext(), density) }.getOrNull()
            ?: runCatching { info.loadIcon(requireContext(), density) }.getOrNull()
        return WidgetListItem(
            appWidgetId = id,
            providerInfo = info,
            label = info.loadLabel(packageManager).toString(),
            appLabel = appName(info.provider.packageName),
            preview = preview
        )
    }

    private fun appName(packageName: String): String = runCatching {
        val pm = requireContext().packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private fun onItemClicked(item: WidgetListItem) {
        if (pickerMode) beginAddWidget(item)
        else confirmRemove(item)
    }

    private fun beginAddWidget(item: WidgetListItem) {
        val id = hostManager.allocateId()
        viewModel.pendingWidgetId = id
        if (hostManager.bindIfAllowed(id, item.providerInfo.provider)) {
            configureOrFinish(id)
            return
        }
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, item.providerInfo.provider)
        }
        runCatching {
            requireActivity().startActivityForResult(intent, Constants.REQUEST_CODE_BIND_WIDGET)
        }.onFailure {
            cancelPendingWidget()
            requireContext().showToast(R.string.widget_pin_failed)
        }
    }

    private fun configureOrFinish(id: Int) {
        val info = hostManager.appWidgetManager.getAppWidgetInfo(id)
        if (info == null) {
            cancelPendingWidget()
            requireContext().showToast(R.string.widget_pin_failed)
            return
        }
        if (info.configure == null) {
            finishAdd(id)
            return
        }
        val configureIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = info.configure
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        }
        runCatching {
            requireActivity().startActivityForResult(
                configureIntent,
                Constants.REQUEST_CODE_CONFIGURE_WIDGET
            )
        }
            .onFailure {
                cancelPendingWidget()
                requireContext().showToast(R.string.widget_pin_failed)
            }
    }

    private fun handleWidgetResult(requestCode: Int, resultCode: Int) {
        val id = viewModel.pendingWidgetId
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        if (resultCode != Activity.RESULT_OK) {
            cancelPendingWidget()
            return
        }
        when (requestCode) {
            Constants.REQUEST_CODE_BIND_WIDGET -> configureOrFinish(id)
            Constants.REQUEST_CODE_CONFIGURE_WIDGET -> finishAdd(id)
        }
    }

    private fun finishAdd(id: Int) {
        prefs.widgetIdList = (prefs.widgetIdList + id).take(Constants.Widget.MAX_WIDGETS)
        prefs.widgetCurrentPage = prefs.widgetIdList.lastIndex
        viewModel.pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        viewModel.refreshWidgets()
        showManagedWidgets()
    }

    private fun cancelPendingWidget() {
        val id = viewModel.pendingWidgetId
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) hostManager.deleteId(id)
        viewModel.pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        showManagedWidgets()
    }

    private fun confirmRemove(item: WidgetListItem) {
        val id = item.appWidgetId ?: return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.remove_widget)
            .setMessage(item.label)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.remove) { _, _ ->
                hostManager.deleteId(id)
                prefs.widgetIdList = prefs.widgetIdList.filterNot { it == id }
                prefs.widgetCurrentPage =
                    prefs.widgetCurrentPage.coerceAtMost((prefs.widgetIdList.size - 1).coerceAtLeast(0))
                viewModel.refreshWidgets()
                showManagedWidgets()
            }
            .show()
    }

    override fun onDestroyView() {
        binding.recyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
