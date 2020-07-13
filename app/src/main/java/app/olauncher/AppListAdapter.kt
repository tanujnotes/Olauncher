package app.olauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.adapter_app_list.view.*

class AppListAdapter(
    private var appsList: List<AppModel>,
    private val listener: (AppModel) -> Unit
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>(), Filterable {

    var appFilteredList = appsList

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.adapter_app_list, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(appFilteredList[position], listener)
        if (itemCount == 1) listener(appFilteredList[position])
    }

    override fun getItemCount(): Int = appFilteredList.size

    // Filter app search results
    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val charSearch = constraint.toString()
                appFilteredList = (if (charSearch.isEmpty()) appsList
                else appsList.filter { app -> app.appLabel.contains(charSearch, true) })

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

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(appModel: AppModel, listener: (AppModel) -> Unit) = with(itemView) {
            app_label.text = appModel.appLabel
            app_label.setOnClickListener { listener(appModel) }
        }
    }
}