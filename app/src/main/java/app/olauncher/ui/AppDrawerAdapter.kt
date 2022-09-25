package app.olauncher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.databinding.AdapterAppDrawerBinding
import java.text.Normalizer

class AppDrawerAdapter(
    private var flag: Int,
    private val appLabelGravity: Int,
    private val clickListener: (AppModel) -> Unit,
    private val appInfoListener: (AppModel) -> Unit,
    private val appHideListener: (Int, AppModel) -> Unit,
) : ListAdapter<AppModel, AppDrawerAdapter.ViewHolder>(DIFF_CALLBACK), Filterable {

    private var appFilter = createAppFilter()
    private var isBangSearch = false
    var appsList: MutableList<AppModel> = mutableListOf()
    var appFilteredList: MutableList<AppModel> = mutableListOf()


    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppModel>() {
            override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean {
                return oldItem.appPackage == newItem.appPackage
            }

            override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(AdapterAppDrawerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (appFilteredList.size == 0) return
        val appModel = appFilteredList[holder.adapterPosition]
        holder.bind(flag, appLabelGravity, appModel, clickListener, appInfoListener)

        holder.appHideButton.setOnClickListener {
            appFilteredList.removeAt(holder.adapterPosition)
            appsList.remove(appModel)
            notifyItemRemoved(holder.adapterPosition)
            appHideListener(flag, appModel)
        }
        try { // Automatically open the app when there's only one search result
            if (itemCount == 1
                && isBangSearch.not()
                && flag == Constants.FLAG_LAUNCH_APP
            ) clickListener(appFilteredList[position])
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getItemCount(): Int = appFilteredList.size

    override fun getFilter(): Filter = this.appFilter

    private fun createAppFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val searchChars = constraint.toString()
                isBangSearch = searchChars.startsWith("!")
                val appFilteredList = (if (searchChars.isEmpty()) appsList
                else appsList.filter { app -> appLabelMatches(app.appLabel, searchChars) } as MutableList<AppModel>)

                val filterResults = FilterResults()
                filterResults.values = appFilteredList
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                appFilteredList = results?.values as MutableList<AppModel>
                notifyDataSetChanged()
            }
        }
    }

    private fun appLabelMatches(appLabel: String, searchChars: String): Boolean {
        return (appLabel.contains(searchChars, true) or
                Normalizer.normalize(appLabel, Normalizer.Form.NFD)
                    .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                    .replace(Regex("[-_+,. ]"), "")
                    .contains(searchChars, true))
    }

    fun setAppList(appsList: MutableList<AppModel>) {
        this.appsList = appsList
        this.appFilteredList = appsList
        notifyDataSetChanged()
    }

    fun launchFirstInList() {
        if (appFilteredList.size > 0)
            clickListener(appFilteredList[0])
    }

    class ViewHolder(private val binding: AdapterAppDrawerBinding) : RecyclerView.ViewHolder(binding.root) {
        val appHideButton: TextView = binding.appHide

        fun bind(
            flag: Int,
            appLabelGravity: Int,
            appModel: AppModel,
            listener: (AppModel) -> Unit,
            appInfoListener: (AppModel) -> Unit,
        ) =
            with(binding) {
                appHideLayout.visibility = View.GONE
                appHideButton.text = (if (flag == Constants.FLAG_HIDDEN_APPS) "SHOW" else "HIDE")
                appTitle.text = appModel.appLabel
                appTitle.gravity = appLabelGravity

                if (appModel.user == android.os.Process.myUserHandle())
                    otherProfileIndicator.visibility = View.GONE
                else otherProfileIndicator.visibility = View.VISIBLE

                appTitle.setOnClickListener { listener(appModel) }
                appTitle.setOnLongClickListener {
                    appHideLayout.visibility = View.VISIBLE
                    true
                }

                appInfo.setOnClickListener { appInfoListener(appModel) }
                appHideLayout.setOnClickListener { appHideLayout.visibility = View.GONE }
            }
    }
}