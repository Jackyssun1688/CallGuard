package com.callguard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.callguard.storage.CallLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 通话记录列表适配器。
 */
class CallLogAdapter : ListAdapter<CallLogEntity, CallLogAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: CardView = itemView.findViewById(R.id.cardCallLog)
        private val textNumber: TextView = itemView.findViewById(R.id.textNumber)
        private val textTime: TextView = itemView.findViewById(R.id.textTime)
        private val textStatus: TextView = itemView.findViewById(R.id.textStatus)
        private val textTranscript: TextView = itemView.findViewById(R.id.textTranscript)
        private val textCategory: TextView = itemView.findViewById(R.id.textCategory)

        private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.CHINA)

        fun bind(log: CallLogEntity) {
            textNumber.text = log.phoneNumber
            textTime.text = dateFormat.format(Date(log.timestamp))

            // 状态显示
            textStatus.text = if (log.isSpam) "🚫 已拦截" else "📞 安全"
            textStatus.setTextColor(
                ContextCompat.getColor(
                    itemView.context,
                    if (log.isSpam) android.R.color.holo_red_dark
                    else android.R.color.holo_green_dark
                )
            )

            // 分类标签
            textCategory.text = when (log.spamCategory) {
                "loan" -> "贷款"
                "scam" -> "诈骗⚠️"
                "promo" -> "推销"
                "auto_voice" -> "自动语音"
                "other" -> "骚扰"
                else -> if (log.isSpam) "骚扰" else ""
            }
            textCategory.visibility = if (log.isSpam) View.VISIBLE else View.GONE

            // 文字稿
            val preview = if (log.transcript.length > 100) {
                log.transcript.take(100) + "…"
            } else {
                log.transcript
            }
            textTranscript.text = if (preview.isEmpty()) "（无对话内容）" else "\"$preview\""
            textTranscript.visibility = View.VISIBLE

            // 卡片背景色
            val bg = if (log.isSpam) {
                ContextCompat.getColor(itemView.context, android.R.color.white)
            } else {
                ContextCompat.getColor(itemView.context, android.R.color.holo_green_light)
            }
            card.setCardBackgroundColor(bg)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CallLogEntity>() {
        override fun areItemsTheSame(old: CallLogEntity, new: CallLogEntity): Boolean =
            old.id == new.id

        override fun areContentsTheSame(old: CallLogEntity, new: CallLogEntity): Boolean =
            old == new
    }
}
