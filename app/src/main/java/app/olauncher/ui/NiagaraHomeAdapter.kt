package app.olauncher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.databinding.AdapterNiagaraHomeBinding
import app.olauncher.helper.getUserHandleFromString

/**
 * Niagara Launcher 風 ホーム画面アダプター
 * お気に入りアプリをアイコン＋テキストの横並びリストで表示
 * 空スロットは「＋」アイコンと薄いヒントテキストを表示
 */
class NiagaraHomeAdapter(
    private val context: Context,
    private val onAppClick: (appIndex: Int) -> Unit,
    private val onAppLongClick: (appIndex: Int) -> Unit,
) : RecyclerView.Adapter<NiagaraHomeAdapter.ViewHolder>() {

    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val packageManager = context.packageManager

    var appItems: List<HomeAppItem> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    /** パッケージ名 → 通知数 のマップ */
    var notificationCounts: Map<String, Int> = emptyMap()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = AdapterNiagaraHomeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(appItems[position], position)
    }

    override fun getItemCount(): Int = appItems.size

    inner class ViewHolder(private val binding: AdapterNiagaraHomeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HomeAppItem, index: Int) {
            if (item.isEmpty) {
                // 空スロット: 「＋」アイコンと薄いヒント
                binding.appIcon.setImageDrawable(
                    ContextCompat.getDrawable(context, R.drawable.ic_check)
                )
                binding.appIcon.imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(context, android.R.color.darker_gray)
                )
                binding.appIcon.alpha = 0.4f
                binding.appName.text = context.getString(R.string.app)  // "App" のヒント
                binding.appName.alpha = 0.35f
                // タップでアプリ追加
                binding.root.setOnClickListener { onAppLongClick(index) }
                binding.root.setOnLongClickListener(null)
            } else {
                // 通常スロット: アイコン＋アプリ名
                binding.appIcon.imageTintList = null
                binding.appIcon.alpha = 1f
                binding.appName.alpha = 1f
                loadAppIcon(item.packageName, item.userString, binding.appIcon)
                binding.appName.text = item.displayName
                // 通知バッジ
                val count = notificationCounts[item.packageName] ?: 0
                if (count > 0) {
                    binding.badgeCount.text = if (count > 99) "99+" else count.toString()
                    binding.badgeCount.visibility = View.VISIBLE
                } else {
                    binding.badgeCount.visibility = View.GONE
                }
                binding.root.setOnClickListener { onAppClick(index) }
                binding.root.setOnLongClickListener {
                    onAppLongClick(index)
                    true
                }
            }
        }

        private fun loadAppIcon(packageName: String, userString: String, imageView: ImageView) {
            if (packageName.isEmpty()) {
                imageView.setImageDrawable(
                    ContextCompat.getDrawable(context, R.drawable.ic_check)
                )
                return
            }
            try {
                val userHandle = getUserHandleFromString(context, userString)
                val activityList = launcherApps.getActivityList(packageName, userHandle)
                if (activityList.isNotEmpty()) {
                    val info = activityList.first()
                    val icon = info.getIcon(0)
                    imageView.setImageDrawable(icon)
                    return
                }
                // Fallback: try loading from package manager
                val info = packageManager.getApplicationInfo(packageName, 0)
                val icon = info.loadIcon(packageManager)
                imageView.setImageDrawable(icon)
            } catch (e: Exception) {
                imageView.setImageDrawable(
                    ContextCompat.getDrawable(context, R.drawable.ic_check)
                )
            }
        }
    }

    data class HomeAppItem(
        val displayName: String,
        val packageName: String,
        val userString: String,
        val activityClassName: String?,
        val slotIndex: Int,       // 1-8 のスロット番号
        val isShortcut: Boolean = false,
        val shortcutId: String? = null,
    ) {
        /** 空スロットかどうか（アプリ未設定） */
        val isEmpty: Boolean get() = packageName.isBlank()
    }
}
