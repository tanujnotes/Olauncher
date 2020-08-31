package app.olauncher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import kotlinx.android.synthetic.main.adapter_app_drawer.view.*

class AppDrawerAdapter(
    private var flag: Int,
    private val clickListener: (AppModel) -> Unit,
    private val longPressListener: (AppModel) -> Unit
) : RecyclerView.Adapter<AppDrawerAdapter.ViewHolder>(), Filterable {

    var appsList: List<AppModel> = listOf()
    var appFilteredList: List<AppModel> = listOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.adapter_app_drawer, parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(appFilteredList[position], clickListener, longPressListener)

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
                else appsList.filter { app -> appLabelMatches(app.appLabel, searchChars) })

                val filterResults = FilterResults()
                filterResults.values = appFilteredList
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                appFilteredList = results?.values as List<AppModel>
                notifyDataSetChanged()
            }
        }
    }

    private fun appLabelMatches(appLabel: String, searchChars: String): Boolean {
        return (appLabel.contains(searchChars, true) or
                appLabel.replace(Regex("[-_+,. ]"), "")
                    .contains(searchChars, true))
    }

    fun setAppList(appsList: List<AppModel>) {
        this.appsList = appsList
        this.appFilteredList = appsList
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(
            appModel: AppModel,
            listener: (AppModel) -> Unit,
            longPressListener: (AppModel) -> Unit
        ) = with(itemView) {
            app_label.text = appModel.appLabel
            app_label.setOnClickListener { listener(appModel) }
            app_label.setOnLongClickListener {
                longPressListener(appModel)
                true
            }
        }
    }
}