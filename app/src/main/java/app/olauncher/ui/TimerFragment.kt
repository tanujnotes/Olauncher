package app.olauncher.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentTimerBinding
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.showKeyboard

class TimerFragment : Fragment() {
    private var _binding: FragmentTimerBinding? = null
    private val binding get() = _binding!!

    private lateinit var timer: CountDownTimer

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val launchAction = arguments?.getSerializable(Constants.Key.LAUNCH) as () -> Unit
        val launchDelay = arguments?.getInt(Constants.Key.LAUNCH_DELAY, 0)!!
        val appName = arguments?.getString(Constants.Key.APP_NAME, "unknown")!!

        binding.appnameText.text = getString(R.string.launch_delay_question, appName)

        timer = object : CountDownTimer(launchDelay * 1000L, 1000L) {
            override fun onTick(millisUntilFinish: Long) {
                val secondsLeft = (millisUntilFinish + 999) / 1000
                binding.timeText.text = getString(R.string.launch_delay_launch_message, secondsLeft)
            }

            override fun onFinish() {
                launchAction()
            }
        }
        timer.start()

        binding.cancel.setOnClickListener {
            findNavController().navigate(R.id.action_timerFragment_to_mainFragment)
        }
    }

    override fun onStop() {
        timer.cancel()
        super.onStop()
    }
}