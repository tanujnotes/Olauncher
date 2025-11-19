package app.olauncher.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.data.AppLimitModel
import app.olauncher.databinding.AdapterAppLimitBinding

class AppLimitsAdapter(private val appLimitClickListener: (AppLimitModel) -> Unit) :
        RecyclerView.Adapter<AppLimitsAdapter.ViewHolder>() {

  private var appLimits: MutableList<AppLimitModel> = mutableListOf()

  inner class ViewHolder(private val binding: AdapterAppLimitBinding) :
          RecyclerView.ViewHolder(binding.root) {

    fun bind(appLimit: AppLimitModel) {
      binding.tvAppName.text = appLimit.appName
      binding.tvTimeRange.text = "${appLimit.getStartTimeString()} - ${appLimit.getEndTimeString()}"

      if (appLimit.isCurrentlyBlocked()) {
        binding.tvStatus.text = "Active"
      } else {
        binding.tvStatus.text = "Inactive"
      }

      binding.root.setOnClickListener { appLimitClickListener(appLimit) }
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val binding = AdapterAppLimitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    return ViewHolder(binding)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bind(appLimits[position])
  }

  override fun getItemCount(): Int = appLimits.size

  fun setAppLimits(limits: List<AppLimitModel>) {
    appLimits = limits.toMutableList()
    notifyDataSetChanged()
  }
}
