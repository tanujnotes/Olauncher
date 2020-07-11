package app.olauncher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.fragment_app.*

class AppListFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_app, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewModel = activity?.run {
            ViewModelProvider(this).get(MainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        val onAppClicked: (appModel: AppModel) -> Unit = { appModel ->
            Toast.makeText(requireContext(), appModel.appLabel, Toast.LENGTH_SHORT).show()
        }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = AppListAdapter(getAppsList(requireContext()), onAppClicked)
        }
    }
}