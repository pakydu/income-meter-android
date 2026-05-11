package com.example.incomemeter

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.incomemeter.databinding.ActivityMainBinding
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // 计时状态
    private var isRunning = false
    private var sessionStartMs = 0L

    // Handler for periodic UI refresh (every ~16ms ≈ 60fps)
    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                updateTick()
                handler.postDelayed(this, 16L)
            }
        }
    }

    // 月薪（元）
    private var monthlySalary = 10000.0

    // 数字格式化
    private val nfCny: NumberFormat = NumberFormat.getNumberInstance(Locale.CHINA).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化静态数据
        updateStatic(10000.0)

        // 按钮点击
        binding.btnToggle.setOnClickListener {
            toggleTimer()
        }

        // 输入框变化时刷新静态数据（未计时中）
        binding.etSalary.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !isRunning) {
                val s = parseSalary()
                if (s != null) updateStatic(s)
            }
        }
    }

    private fun parseSalary(): Double? {
        val raw = binding.etSalary.text?.toString()?.trim() ?: return null
        return raw.toDoubleOrNull()?.takeIf { it > 0 }
    }

    /** 换算各时间单位收入速率 */
    private fun calcRates(monthly: Double): DoubleArray {
        val perDay = monthly / 22.0
        val perHour = perDay / 8.0
        val perMinute = perHour / 60.0
        val perSecond = perMinute / 60.0
        return doubleArrayOf(monthly, perDay, perHour, perMinute, perSecond)
    }

    /** 刷新不依赖计时的静态字段 */
    private fun updateStatic(salary: Double) {
        monthlySalary = salary
        val rates = calcRates(salary)
        binding.tvPerSecond.text = "¥${String.format("%.6f", rates[4])}"
        binding.tvPerMinute.text = "¥${String.format("%.4f", rates[3])}"
        binding.tvPerHour.text = "¥${nfCny.format(rates[2])}"
        binding.tvMonthSalary.text = "¥${NumberFormat.getNumberInstance(Locale.CHINA).apply { maximumFractionDigits = 0 }.format(salary)}"
        binding.tvDaySalary.text = "¥${nfCny.format(rates[1])}"
    }

    /** 每帧刷新 */
    private fun updateTick() {
        val rates = calcRates(monthlySalary)
        val perSecond = rates[4]

        // 本次计时已赚
        val sessionSecs = (System.currentTimeMillis() - sessionStartMs) / 1000.0
        binding.tvSession.text = "¥${String.format("%.6f", sessionSecs * perSecond)}"

        // 今日已赚（按 9:00~18:00 工作制）
        val cal = Calendar.getInstance()
        val secondsIntoDay = cal.get(Calendar.HOUR_OF_DAY) * 3600 +
                cal.get(Calendar.MINUTE) * 60 +
                cal.get(Calendar.SECOND) +
                cal.get(Calendar.MILLISECOND) / 1000.0
        val workStart = 9 * 3600
        val workDuration = 8 * 3600
        val workedToday = maxOf(0.0, minOf(workDuration.toDouble(), secondsIntoDay - workStart))
        val todayEarned = workedToday * perSecond
        val pct = minOf(100.0, (workedToday / workDuration) * 100.0)

        binding.tvTodayEarned.text = "¥${String.format("%.6f", todayEarned)}"
        binding.progressDay.progress = (pct * 100).toInt()   // max=10000
        binding.tvDayProgress.text = "今日工作进度 ${String.format("%.1f", pct)}%（9:00~18:00 工作制）"
    }

    private fun toggleTimer() {
        if (!isRunning) {
            // 启动
            val s = parseSalary()
            if (s == null) {
                Toast.makeText(this, getString(R.string.error_invalid_salary), Toast.LENGTH_SHORT).show()
                return
            }
            monthlySalary = s
            updateStatic(s)

            isRunning = true
            sessionStartMs = System.currentTimeMillis()

            binding.btnToggle.text = getString(R.string.btn_pause)
            binding.tvStatus.text = getString(R.string.status_running)
            binding.statusDot.setBackgroundResource(R.drawable.dot_running)

            // 启动脉冲动画
            val anim = AnimationUtils.loadAnimation(this, R.anim.pulse)
            binding.statusDot.startAnimation(anim)

            // 启动帧刷新
            handler.post(tickRunnable)

        } else {
            // 暂停
            isRunning = false
            handler.removeCallbacks(tickRunnable)

            binding.btnToggle.text = getString(R.string.btn_resume)
            binding.tvStatus.text = getString(R.string.status_paused)
            binding.statusDot.setBackgroundResource(R.drawable.dot_stopped)
            binding.statusDot.clearAnimation()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(tickRunnable)
    }
}
