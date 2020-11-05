package app.olauncher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import kotlinx.android.synthetic.main.adapter_app_drawer.view.*

class AppDrawerAdapter(
    private var flag: Int,
    private val clickListener: (AppModel) -> Unit,
    private val appInfoListener: (AppModel) -> Unit,
    private val appHideListener: (Int, AppModel) -> Unit
) : RecyclerView.Adapter<AppDrawerAdapter.ViewHolder>(), Filterable {

    var appsList: MutableList<AppModel> = mutableListOf()
    var appFilteredList: MutableList<AppModel> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.adapter_app_drawer, parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appModel = appFilteredList[holder.adapterPosition]
        holder.bind(flag, appModel, clickListener, appInfoListener)

        holder.appHideButton.setOnClickListener {
            appFilteredList.removeAt(holder.adapterPosition)
            appsList.remove(appModel)
            notifyItemRemoved(holder.adapterPosition)
            appHideListener(flag, appModel)
        }
        try { // Automatically open the app when there's only one search result
            if ((itemCount == 1) and (flag == Constants.FLAG_LAUNCH_APP))
                clickListener(appFilteredList[position])
        } catch (e: Exception) {

        }
    }

    override fun getItemCount(): Int = appFilteredList.size

    // Filter app search results
    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val searchChars = constraint.toString()
                appFilteredList = (if (searchChars.isEmpty()) appsList
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
                appLabel.replace(Regex("[-_+,. ]"), "")
                    .contains(searchChars, true))
    }

    fun setAppList(appsList: MutableList<AppModel>) {
        this.appsList = appsList
        this.appFilteredList = appsList
        notifyDataSetChanged()
    }

    fun getTopApp(): AppModel? {
        return if (appFilteredList.size > 0) appFilteredList[0]
        else null
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appHideButton: TextView = itemView.appHide

        fun bind(flag: Int, appModel: AppModel, listener: (AppModel) -> Unit, appInfoListener: (AppModel) -> Unit) =
            with(itemView) {
                appHideLayout.visibility = View.GONE
                appHideButton.text = (if (flag == Constants.FLAG_HIDDEN_APPS) "SHOW" else "HIDE")

                if (appModel.user == android.os.Process.myUserHandle())
                    appTitle.text = appModel.appLabel
                else
                    appTitle.text = appModel.appLabel + "w"

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