package com.github.tvbox.osc.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.github.tvbox.osc.R
import com.github.tvbox.osc.util.HawkConfig
import com.github.tvbox.osc.util.KV

/**
 * 自动跳过片头片尾设置对话框
 */
class AutoSkipSettingsDialog(context: Context) : Dialog(context, R.style.CustomDialog) {
    
    private val switchIntro: Switch by lazy { Switch(context) }
    private val switchOutro: Switch by lazy { Switch(context) }
    private val switchAd: Switch by lazy { Switch(context) }
    
    init {
        switchIntro.isChecked = KV.get(HawkConfig.AUTO_SKIP_INTRO, false)
        switchOutro.isChecked = KV.get(HawkConfig.AUTO_SKIP_OUTRO, false)
        switchAd.isChecked = KV.get(HawkConfig.AUTO_SKIP_AD, false)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_auto_skip_settings)
        
        findViewById<TextView>(R.id.tvAutoSkipTitle).text = "自动跳过设置"
        findViewById<TextView>(R.id.tvIntroLabel).text = "自动跳过片头"
        findViewById<TextView>(R.id.tvOutroLabel).text = "自动跳过片尾"
        findViewById<TextView>(R.id.tvAdLabel).text = "自动跳过广告"
        
        switchIntro.setOnCheckedChangeListener { _, isChecked ->
            KV.put(HawkConfig.AUTO_SKIP_INTRO, isChecked)
        }
        switchOutro.setOnCheckedChangeListener { _, isChecked ->
            KV.put(HawkConfig.AUTO_SKIP_OUTRO, isChecked)
        }
        switchAd.setOnCheckedChangeListener { _, isChecked ->
            KV.put(HawkConfig.AUTO_SKIP_AD, isChecked)
        }
        
        findViewById<Switch>(R.id.switchIntro).setChecked(switchIntro.isChecked)
        findViewById<Switch>(R.id.switchOutro).setChecked(switchOutro.isChecked)
        findViewById<Switch>(R.id.switchAd).setChecked(switchAd.isChecked)
        
        switchIntro.setOnCheckedChangeListener { _, isChecked ->
            KV.put(HawkConfig.AUTO_SKIP_INTRO, isChecked)
        }
        switchOutro.setOnCheckedChangeListener { _, isChecked ->
            KV.put(HawkConfig.AUTO_SKIP_OUTRO, isChecked)
        }
        switchAd.setOnCheckedChangeListener { _, isChecked ->
            KV.put(HawkConfig.AUTO_SKIP_AD, isChecked)
        }
        
        findViewById<TextView>(R.id.tvIntroLabel).text = "自动跳过片头"
        findViewById<TextView>(R.id.tvOutroLabel).text = "自动跳过片尾"
        findViewById<TextView>(R.id.tvAdLabel).text = "自动跳过广告"
        
        findViewById<TextView>(R.id.btnConfirm).setOnClickListener {
            dismiss()
        }
        findViewById<TextView>(R.id.btnConfirm).text = "确定"
    }
    
    companion object {
        const val TAG = "AutoSkipSettingsDialog"
    }
}
