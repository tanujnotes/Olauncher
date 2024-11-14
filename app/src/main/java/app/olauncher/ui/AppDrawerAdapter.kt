package app.olauncher.ui

import android.content.Context
import android.os.UserHandle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Filter
import android.widget.Filterable
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.databinding.AdapterAppDrawerBinding
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.showKeyboard
import java.text.Normalizer

class AppDrawerAdapter(
    private var flag: Int,
    private val appLabelGravity: Int,
    private val appClickListener: (AppModel) -> Unit,
    private val appInfoListener: (AppModel) -> Unit,
    private val appDeleteListener: (AppModel) -> Unit,
    private val appHideListener: (AppModel, Int) -> Unit,
    private val appRenameListener: (AppModel, String) -> Unit,
) : ListAdapter<AppModel, AppDrawerAdapter.ViewHolder>(DIFF_CALLBACK), Filterable {

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppModel>() {
            override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean =
                oldItem.appPackage == newItem.appPackage && oldItem.user == newItem.user

            override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean =
                oldItem == newItem
        }
    }

    private var autoLaunch = true
    private var isBangSearch = false
    private val appFilter = createAppFilter()
    private val myUserHandle = android.os.Process.myUserHandle()

    var appsList: MutableList<AppModel> = mutableListOf()
    var appFilteredList: MutableList<AppModel> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(AdapterAppDrawerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        try {
            if (appFilteredList.size == 0 || position == RecyclerView.NO_POSITION) return
            val appModel = appFilteredList[holder.bindingAdapterPosition]
            holder.bind(
                flag,
                appLabelGravity,
                myUserHandle,
                appModel,
                appClickListener,
                appDeleteListener,
                appInfoListener,
                appHideListener,
                appRenameListener
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getFilter(): Filter = this.appFilter

    private fun createAppFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSearch: CharSequence?): FilterResults {
                isBangSearch = charSearch?.startsWith("!") ?: false
                autoLaunch = charSearch?.startsWith(" ")?.not() ?: true

                val appFilteredList = (if (charSearch.isNullOrBlank()) appsList
                else appsList.filter { app ->
                    appLabelMatches(app.appLabel, charSearch)
//                }.sortedByDescending {
//                    charSearch.contentEquals(it.appLabel, true)
                } as MutableList<AppModel>)

                val filterResults = FilterResults()
                filterResults.values = appFilteredList
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                results?.values?.let {
                    val items = it as MutableList<AppModel>
                    appFilteredList = items
                    submitList(appFilteredList) {
                        autoLaunch()
                    }
                }
            }
        }
    }

    private fun autoLaunch() {
        try {
            if (itemCount == 1
                && autoLaunch
                && isBangSearch.not()
                && flag == Constants.FLAG_LAUNCH_APP
                && appFilteredList.size > 0
            ) appClickListener(appFilteredList[0])
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun appLabelMatches(appLabel: String, charSearch: CharSequence): Boolean {
        return (appLabel.contains(charSearch.trim(), true) or
                Normalizer.normalize(appLabel, Normalizer.Form.NFD)
                    .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                    .replace(Regex("[-_+,. ]"), "")
                    .contains(charSearch, true))
    }

    fun setAppList(appsList: MutableList<AppModel>) {
        // Add empty app for bottom padding in recyclerview
        appsList.add(AppModel("", null, "", "", false, android.os.Process.myUserHandle()))
        this.appsList = appsList
        this.appFilteredList = appsList
        submitList(appsList)
    }

    fun launchFirstInList() {
        if (appFilteredList.size > 0)
            appClickListener(appFilteredList[0])
    }

    class ViewHolder(private val binding: AdapterAppDrawerBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            flag: Int,
            appLabelGravity: Int,
            myUserHandle: UserHandle,
            appModel: AppModel,
            clickListener: (AppModel) -> Unit,
            appDeleteListener: (AppModel) -> Unit,
            appInfoListener: (AppModel) -> Unit,
            appHideListener: (AppModel, Int) -> Unit,
            appRenameListener: (AppModel, String) -> Unit,
        ) =
            with(binding) {
                appHideLayout.visibility = View.GONE
                renameLayout.visibility = View.GONE
                appTitle.visibility = View.VISIBLE
                appTitle.text = appModel.appLabel + if (appModel.isNew == true) " ✦" else ""
                appTitle.gravity = appLabelGravity
                otherProfileIndicator.isVisible = appModel.user != myUserHandle

                appTitle.setOnClickListener { clickListener(appModel) }
                appTitle.setOnLongClickListener {
                    if (appModel.appPackage.isNotEmpty()) {
                        appDelete.alpha = if (root.context.isSystemApp(appModel.appPackage)) 0.5f else 1.0f
                        appHide.text = if (flag == Constants.FLAG_HIDDEN_APPS)
                            root.context.getString(R.string.adapter_show)
                        else
                            root.context.getString(R.string.adapter_hide)
                        appTitle.visibility = View.INVISIBLE
                        appHideLayout.visibility = View.VISIBLE
                        appRename.isVisible = flag != Constants.FLAG_HIDDEN_APPS
                    }
                    true
                }
                appRename.setOnClickListener {
                    if (appModel.appPackage.isNotEmpty()) {
                        etAppRename.hint = getAppName(etAppRename.context, appModel.appPackage)
                        etAppRename.setText(appModel.appLabel)
                        etAppRename.setSelectAllOnFocus(true)
                        renameLayout.visibility = View.VISIBLE
                        appHideLayout.visibility = View.GONE
                        etAppRename.showKeyboard()
                        etAppRename.imeOptions = EditorInfo.IME_ACTION_DONE;
                    }
                }
                etAppRename.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (hasFocus)
                        appTitle.visibility = View.INVISIBLE
                    else
                        appTitle.visibility = View.VISIBLE
                }
                etAppRename.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        etAppRename.hint = getAppName(etAppRename.context, appModel.appPackage)
                    }

                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int,
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int,
                    ) {
                        etAppRename.hint = ""
                    }
                })
                etAppRename.setOnEditorActionListener { _, actionCode, _ ->
                    if (actionCode == EditorInfo.IME_ACTION_DONE) {
                        val renameLabel = etAppRename.text.toString().trim()
                        if (renameLabel.isNotBlank() && appModel.appPackage.isNotBlank()) {
                            appRenameListener(appModel, renameLabel)
                            renameLayout.visibility = View.GONE
                        }
                        true
                    }
                    false
                }
                tvSaveRename.setOnClickListener {
                    etAppRename.hideKeyboard()
                    val renameLabel = etAppRename.text.toString().trim()
                    if (renameLabel.isNotBlank() && appModel.appPackage.isNotBlank()) {
                        appRenameListener(appModel, renameLabel)
                        renameLayout.visibility = View.GONE
                    } else {
                        val packageManager = etAppRename.context.packageManager
                        appRenameListener(
                            appModel,
                            packageManager.getApplicationLabel(
                                packageManager.getApplicationInfo(appModel.appPackage, 0)
                            ).toString()
                        )
                        renameLayout.visibility = View.GONE
                    }
                }
                appInfo.setOnClickListener { appInfoListener(appModel) }
                appDelete.setOnClickListener { appDeleteListener(appModel) }
                appMenuClose.setOnClickListener {
                    appHideLayout.visibility = View.GONE
                    appTitle.visibility = View.VISIBLE
                }
                appRenameClose.setOnClickListener {
                    renameLayout.visibility = View.GONE
                    appTitle.visibility = View.VISIBLE
                }
                appHide.setOnClickListener { appHideListener(appModel, bindingAdapterPosition) }
            }

        private fun getAppName(context: Context, appPackage: String): String {
            val packageManager = context.packageManager
            return packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(appPackage, 0)
            ).toString()
        }
    }
}
