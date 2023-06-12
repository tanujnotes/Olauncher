package app.olauncher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppModel


class AlphabetAdapter(
    private val alphabetList: List<String>,
    private val clickListener: AlphabetClickListener,
) : RecyclerView.Adapter<AlphabetAdapter.AlphabetViewHolder>() {

    interface AlphabetClickListener {
        fun onAlphabetClick(alphabet: String)
    }

    var dataList: List<String> = alphabetList

    inner class AlphabetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val alphabetTextView: TextView = itemView.findViewById(R.id.alphabetTextView)

        fun bind(alphabet: String) {
            alphabetTextView.text = alphabet

            itemView.setOnClickListener {
                clickListener.onAlphabetClick(alphabet)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlphabetViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.alphabet, parent, false)
        return AlphabetViewHolder(view)
    }

    override fun getItemCount(): Int {
        return alphabetList.size
    }

    override fun onBindViewHolder(holder: AlphabetViewHolder, position: Int) {
        val alphabet = alphabetList[position]
        holder.bind(alphabet)
    }


}
