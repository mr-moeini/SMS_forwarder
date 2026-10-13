package com.mrmoeini.smsforwarder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RuleAdapter(
    private val items: List<Rule>,
    private val onToggle: (Rule, Boolean) -> Unit,
    private val onEdit: (Rule) -> Unit,
    private val onDelete: (Rule) -> Unit
) : RecyclerView.Adapter<RuleAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvTitle)
        val detail: TextView = v.findViewById(R.id.tvDetail)
        val switch: CompoundButton = v.findViewById(R.id.swEnabled)
        val delete: ImageButton = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_rule, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val rule = items[position]
        val ctx = holder.itemView.context
        holder.title.text = if (rule.name.isNotBlank()) rule.name else rule.sender
        val dest = if (rule.destName.isNotBlank()) "${rule.destName} (${rule.destNumber})" else rule.destNumber
        holder.detail.text = ctx.getString(R.string.rule_detail, rule.sender, dest)

        holder.switch.setOnCheckedChangeListener(null)
        holder.switch.isChecked = rule.enabled
        holder.switch.setOnCheckedChangeListener { _, checked -> onToggle(rule, checked) }

        holder.itemView.setOnClickListener { onEdit(rule) }
        holder.delete.setOnClickListener { onDelete(rule) }
    }

    override fun getItemCount(): Int = items.size
}
