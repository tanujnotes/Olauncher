package app.olaunchercf.ui

import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import app.olaunchercf.R
import app.olaunchercf.data.AppModel
import app.olaunchercf.data.Constants
import app.olaunchercf.data.Prefs
import app.olaunchercf.databinding.AdapterAppDrawerBinding
import kotlinx.coroutines.NonCancellable.cancel
import java.text.Normalizer

class AppDrawerAdapter(
    private var flag: Int,
    private val gravity: Int,
    private val clickListener: (AppModel) -> Unit,
    private val appInfoListener: (AppModel) -> Unit,
    private val appHideListener: (Int, AppModel) -> Unit,
    private val appRenameListener: (String, String) -> Unit
) : RecyclerView.Adapter<AppDrawerAdapter.ViewHolder>(), Filterable {

    private var appFilter = createAppFilter()
    var appsList: MutableList<AppModel> = mutableListOf()
    var appFilteredList: MutableList<AppModel> = mutableListOf()
    private lateinit var binding: AdapterAppDrawerBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        //val view = LayoutInflater.from(parent.context)
        //    .inflate(R.layout.adapter_app_drawer, parent, false)

        binding = AdapterAppDrawerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        //val view = binding.root
        binding.appTitle.textSize = Prefs(parent.context).textSize.toFloat()

        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (appFilteredList.size == 0) return
        val appModel = appFilteredList[holder.adapterPosition]
        holder.bind(flag, gravity, appModel, clickListener, appInfoListener)

        holder.appHideButton.setOnClickListener {
            appFilteredList.removeAt(holder.adapterPosition)
            appsList.remove(appModel)
            notifyItemRemoved(holder.adapterPosition)
            appHideListener(flag, appModel)
        }

        holder.appRenameButton.setOnClickListener {
            val name = holder.appRenameEdit.text.toString().trim()
            appModel.appAlias = name
            notifyItemChanged(holder.adapterPosition)
            Log.d("rename", "$appModel")
            appRenameListener(appModel.appPackage, appModel.appAlias)
        }

        try { // Automatically open the app when there's only one search result
            if ((itemCount == 1) and (flag == Constants.FLAG_LAUNCH_APP))
                clickListener(appFilteredList[position])
        } catch (e: Exception) {

        }
    }

    override fun getItemCount(): Int = appFilteredList.size

    override fun getFilter(): Filter = this.appFilter

    private fun createAppFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val searchChars = constraint.toString()
/*                 val appFilteredList = (if (searchChars.isEmpty()) appsList
 *                 else appsList.filter { app -> appLabelMatches(app.appLabel, searchChars) } as MutableList<AppModel>)
 *  */
                val appFilteredList = (if (searchChars.isEmpty()) appsList
                else appsList.filter { app ->
                    if (app.appAlias.isEmpty()) {
                        appLabelMatches(app.appLabel, searchChars)
                    } else {
                        appLabelMatches(app.appAlias, searchChars)
                    }
                } as MutableList<AppModel>)

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

    class ViewHolder(itemView: AdapterAppDrawerBinding) : RecyclerView.ViewHolder(itemView.root) {
        val appHideButton: TextView = itemView.appHide
        val appRenameButton: TextView = itemView.appRename
        val appRenameEdit: EditText = itemView.appRenameEdit
        private val appHideLayout: ConstraintLayout = itemView.appHideLayout
        private val appTitle: TextView = itemView.appTitle
        private val otherProfileIndicator: ImageView = itemView.otherProfileIndicator
        private val appInfo: ImageView = itemView.appInfo

        fun bind(
            flag: Int,
            appLabelGravity: Int,
            appModel: AppModel,
            listener: (AppModel) -> Unit,
            appInfoListener: (AppModel) -> Unit
        ) =
            with(itemView) {
                appHideLayout.visibility = View.GONE
                appHideButton.text = if (flag == Constants.FLAG_HIDDEN_APPS) {
                    context.getString(R.string.show)
                } else {
                    context.getString(R.string.hide)
                }


                appRenameEdit.addTextChangedListener(object : TextWatcher {

                    override fun afterTextChanged(s: Editable) {}

                    override fun beforeTextChanged(s: CharSequence, start: Int,
                                                   count: Int, after: Int) {
                    }

                    override fun onTextChanged(s: CharSequence, start: Int,
                                               before: Int, count: Int) {
                        if (appRenameEdit.text.isEmpty()) {
                            appRenameButton.text = context.getString(R.string.reset)
                        } else if (appRenameEdit.text.toString() == appModel.appAlias || appRenameEdit.text.toString() == appModel.appLabel) {
                            appRenameButton.text = context.getString(R.string.cancel)
                        } else {
                            appRenameButton.text = context.getString(R.string.rename)
                        }
                    }
                })

                // set current name as default text in EditText
                appRenameEdit.text = if (appModel.appAlias.isEmpty()) {
                    Editable.Factory.getInstance().newEditable(appModel.appLabel);
                } else {
                    Editable.Factory.getInstance().newEditable(appModel.appAlias);
                }

                appTitle.text = if (appModel.appAlias.isEmpty()) {
                    appModel.appLabel
                } else {
                    appModel.appAlias
                }

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
