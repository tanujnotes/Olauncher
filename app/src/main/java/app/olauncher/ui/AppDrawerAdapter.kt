package app.olauncher.ui

import android.annotation.SuppressLint
import android.os.UserHandle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.databinding.AdapterAppDrawerBinding
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.showKeyboard
import java.text.Normalizer

@SuppressLint("NotifyDataSetChanged")
class AppDrawerAdapter(
    private var flag: Int,
    private val appLabelGravity: Int,
    private val appClickListener: (AppModel) -> Unit,
    private val appInfoListener: (AppModel) -> Unit,
    private val appDeleteListener: (AppModel) -> Unit,
    private val appHideListener: (AppModel, Int) -> Unit,
    private val appRenameListener: (AppModel, String) -> Unit,
) : RecyclerView.Adapter<AppDrawerAdapter.ViewHolder>(), Filterable {

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
            if (appFilteredList.size == 0) return
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

    override fun getItemCount(): Int = appFilteredList.size

    override fun getFilter(): Filter = this.appFilter

    private fun createAppFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSearch: CharSequence?): FilterResults {
                isBangSearch = charSearch?.startsWith("!") ?: false
                autoLaunch = charSearch?.startsWith(" ")?.not() ?: true

                val appFilteredList = (if (charSearch.isNullOrBlank()) appsList
                else appsList.filter { app -> appLabelMatches(app.appLabel, charSearch) } as MutableList<AppModel>)

                val filterResults = FilterResults()
                filterResults.values = appFilteredList
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                appFilteredList = results?.values as MutableList<AppModel>
                notifyDataSetChanged()
                autoLaunch()
            }
        }
    }

    private fun autoLaunch() {
        try {
            if (itemCount == 1
                && autoLaunch
                && isBangSearch.not()
                && flag == Constants.FLAG_LAUNCH_APP
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
        appsList.add(AppModel("", null, "", "", android.os.Process.myUserHandle()))
        this.appsList = appsList
        this.appFilteredList = appsList
        notifyItemRangeChanged(0, appFilteredList.size)
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
                appTitle.text = appModel.appLabel
                appTitle.gravity = appLabelGravity
                otherProfileIndicator.isVisible = appModel.user != myUserHandle

                appTitle.setOnClickListener { clickListener(appModel) }
                appTitle.setOnLongClickListener {
                    if (appModel.appPackage.isNotEmpty()) {
                        appDelete.alpha = if (root.context.isSystemApp(appModel.appPackage)) 0.5f else 1.0f
                        appHide.text = if (flag == Constants.FLAG_HIDDEN_APPS) "Show" else "Hide"
                        appHideLayout.visibility = View.VISIBLE
                    }
                    true
                }
                appRename.setOnClickListener {
                    if (appModel.appPackage.isNotEmpty()) {
                        etAppRename.hint = appModel.appLabel
                        renameLayout.visibility = View.VISIBLE
                        appHideLayout.visibility = View.GONE
                        etAppRename.showKeyboard()
                    }
                }
                tvSaveRename.setOnClickListener {
                    val renameLabel = etAppRename.text.toString().trim()
                    if (renameLabel.isNotBlank() && appModel.appPackage.isNotBlank())
                        appRenameListener(appModel, renameLabel)
                }
                appInfo.setOnClickListener { appInfoListener(appModel) }
                appDelete.setOnClickListener { appDeleteListener(appModel) }
                appHideLayout.setOnClickListener { appHideLayout.visibility = View.GONE }
                appHide.setOnClickListener { appHideListener(appModel, bindingAdapterPosition) }
            }
    }
}