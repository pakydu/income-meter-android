package com.example.incomemeter

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.example.incomemeter.databinding.ActivityMainBinding
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

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

    // 累计收入动画
    private var displayedMonthEarned = 0.0
    private var monthAnimator: android.animation.ValueAnimator? = null

    // 数字格式化
    private val nfCny: NumberFormat = NumberFormat.getNumberInstance(Locale.CHINA).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    companion object {
        private const val PREFS_NAME = "income_meter_prefs"
        private const val KEY_SALARY = "monthly_salary"
        private const val DEFAULT_SALARY = 10000.0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 初始化 SharedPreferences
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 设置 Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // 从设置加载月薪
        val savedSalary = prefs.getFloat(KEY_SALARY, DEFAULT_SALARY.toFloat())
        monthlySalary = savedSalary.toDouble()

        // 初始化静态数据
        updateStatic(monthlySalary)

        // 按钮点击
        binding.btnToggle.setOnClickListener {
            toggleTimer()
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次回到页面时刷新月薪（可能在设置页面修改了）
        val savedSalary = prefs.getFloat(KEY_SALARY, DEFAULT_SALARY.toFloat())
        if (!isRunning && savedSalary.toDouble() != monthlySalary) {
            monthlySalary = savedSalary.toDouble()
            updateStatic(monthlySalary)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.menu_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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

        // 更新本月累计
        updateMonthCumulative()
    }

    /** 计算并显示本月累计收入（每10元更新一次） */
    private fun updateMonthCumulative() {
        val rates = calcRates(monthlySalary)
        val perSecond = rates[4]
        val perDay = rates[1]

        val cal = Calendar.getInstance()

        // 计算本月已过去的工作日天数（从1日开始）
        val today = cal.get(Calendar.DAY_OF_MONTH)
        val daysWorkedThisMonth = minOf(today, 22)  // 最多按22个工作日算

        // 计算今日已工作时间（按 9:00~18:00 工作制）
        val secondsIntoDay = cal.get(Calendar.HOUR_OF_DAY) * 3600 +
                cal.get(Calendar.MINUTE) * 60 +
                cal.get(Calendar.SECOND) +
                cal.get(Calendar.MILLISECOND) / 1000.0
        val workStart = 9 * 3600
        val workDuration = 8 * 3600
        val workedToday = maxOf(0.0, minOf(workDuration.toDouble(), secondsIntoDay - workStart))

        // 本月累计 = 已完成工作日收入 + 今日已工作收入
        val newMonthEarned = (daysWorkedThisMonth - 1) * perDay + workedToday * perSecond

        // 四舍五入到整数判断是否需要更新（每10元更新一次）
        val newRounded = Math.floor(newMonthEarned / 10.0).toInt()
        val currentRounded = Math.floor(displayedMonthEarned / 10.0).toInt()

        if (newRounded != currentRounded || displayedMonthEarned == 0.0) {
            // 动画更新数字
            animateNumber(displayedMonthEarned, newMonthEarned)
        }

        // 更新进度文字
        binding.tvMonthProgress.text = "本月已过 $today 天 / 22 个工作日"
    }

    /** 数字动画效果 */
    private fun animateNumber(from: Double, to: Double) {
        monthAnimator?.cancel()

        monthAnimator = android.animation.ValueAnimator.ofFloat(from.toFloat(), to.toFloat()).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                displayedMonthEarned = (animator.animatedValue as Float).toDouble()
                binding.tvMonthEarned.text = "¥${String.format("%.2f", displayedMonthEarned)}"
            }
            start()
        }
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
        binding.progressDay.progress = (pct * 100).toInt()
        binding.tvDayProgress.text = "今日工作进度 ${String.format("%.1f", pct)}%（9:00~18:00 工作制）"

        // 更新本月累计
        updateMonthCumulative()
    }

    private fun toggleTimer() {
        if (!isRunning) {
            // 启动
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
        monthAnimator?.cancel()
    }
}
