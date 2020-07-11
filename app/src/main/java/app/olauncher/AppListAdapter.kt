package app.olauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.adapter_app_list.view.*

class AppListAdapter(private val items: List<AppModel>, private val listener: (AppModel) -> Unit) :
    RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.adapter_app_list, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], listener)
    }

    override fun getItemCount(): Int {
        return items.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(appModel: AppModel, listener: (AppModel) -> Unit) = with(itemView) {
            app_label.text = appModel.appLabel
            app_label.setOnClickListener { listener(appModel) }
        }
    }
}