package com.example.incomemeter

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.incomemeter.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREFS_NAME = "income_meter_prefs"
        private const val KEY_SALARY = "monthly_salary"
        private const val DEFAULT_SALARY = 10000.0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 加载保存的月薪
        val savedSalary = prefs.getFloat(KEY_SALARY, DEFAULT_SALARY.toFloat())
        binding.etSalary.setText(savedSalary.toInt().toString())

        // 保存按钮
        binding.btnSave.setOnClickListener {
            saveSalary()
        }
    }

    private fun saveSalary() {
        val salaryText = binding.etSalary.text?.toString()?.trim()
        val salary = salaryText?.toDoubleOrNull()

        if (salary == null || salary <= 0) {
            Toast.makeText(this, getString(R.string.error_invalid_salary), Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit().putFloat(KEY_SALARY, salary.toFloat()).apply()
        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        finish()
    }
}
