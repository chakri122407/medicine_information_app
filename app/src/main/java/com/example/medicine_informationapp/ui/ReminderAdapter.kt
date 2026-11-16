package com.example.medicine_informationapp.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.medicine_informationapp.data.db.Reminder
import com.example.medicine_informationapp.databinding.ItemReminderBinding
import java.util.Locale

class ReminderAdapter(
    private val onToggleEnable: (Reminder, Boolean) -> Unit,
    private val onDelete: (Reminder) -> Unit
) : RecyclerView.Adapter<ReminderAdapter.ViewHolder>() {

    private val reminders = mutableListOf<Reminder>()

    fun submitList(newReminders: List<Reminder>) {
        reminders.clear()
        reminders.addAll(newReminders)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReminderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(reminders[position])
    }

    override fun getItemCount(): Int = reminders.size

    inner class ViewHolder(private val binding: ItemReminderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(reminder: Reminder) {
            binding.tvMedicineName.text = reminder.medicineName
            binding.tvReminderTime.text = String.format(
                Locale.getDefault(),
                "%02d:%02d",
                reminder.hour,
                reminder.minute
            )
            binding.tvReminderDays.text = reminder.daysOfWeek
            binding.switchReminder.setOnCheckedChangeListener(null)
            binding.switchReminder.isChecked = reminder.isEnabled

            binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
                onToggleEnable(reminder, isChecked)
            }

            binding.btnDeleteReminder.setOnClickListener {
                onDelete(reminder)
            }
        }
    }
}
