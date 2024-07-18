package app.olauncher.ui

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnItemTouchListener
import app.olauncher.R
import app.olauncher.data.DrawerCharacterModel
import app.olauncher.databinding.DrawerAlphabetBinding


class DrawerCharacterAdapter :
    ListAdapter<DrawerCharacterModel, DrawerCharacterAdapter.ViewHolder>(diffObject) {
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val binding = DrawerAlphabetBinding.bind(view)
        fun bind(character: DrawerCharacterModel) {

            if (character.inRange) {
                binding.character.setTextColor(itemView.context.getColor(R.color.design_default_color_secondary))
            } else {
                val typedValue = TypedValue()
                itemView.context.theme.resolveAttribute(R.attr.primaryColor, typedValue, true)
                binding.character.setTextColor(itemView.context.getColor(typedValue.resourceId))
            }

            binding.character.text = character.character

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.drawer_alphabet, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pos = getItem(position)
        holder.bind(pos)
    }

    companion object {
        val diffObject = object : DiffUtil.ItemCallback<DrawerCharacterModel>() {
            override fun areItemsTheSame(
                oldItem: DrawerCharacterModel,
                newItem: DrawerCharacterModel
            ): Boolean {
                return oldItem.character == newItem.character
            }

            override fun areContentsTheSame(
                oldItem: DrawerCharacterModel,
                newItem: DrawerCharacterModel
            ): Boolean {
                return oldItem.hashCode() == newItem.hashCode()
            }
        }
    }


    class CharacterTouchListener(
        private val adapter: DrawerCharacterAdapter,
        private val clickListener: ((String) -> Unit)?
    ) :
        OnItemTouchListener {

        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            val child = rv.findChildViewUnder(e.x, e.y)
            if (child != null) {
                val itemPosition = rv.getChildAdapterPosition(child)
                clickListener?.let { it(adapter.currentList[itemPosition].character) }
            }

            return true
        }

        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
            val child = rv.findChildViewUnder(e.x, e.y)
            if (child != null) {
                val itemPosition = rv.getChildAdapterPosition(child)
                clickListener?.let { it(adapter.currentList[itemPosition].character) }
            }
        }

        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        }

    }

}

