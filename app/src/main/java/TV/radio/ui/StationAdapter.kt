package tv.radio.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import tv.radio.R
import tv.radio.data.Station

/**
 * 电台列表适配器
 * 使用 ListAdapter 和 DiffUtil 实现高效的列表更新
 */
class StationAdapter(
    private val onStationClick: (Station) -> Unit,
    private val onDeleteClick: (Station) -> Unit,
    private val onAddClick: () -> Unit,
    private val onSettingsClick: () -> Unit
) : ListAdapter<Station, RecyclerView.ViewHolder>(StationDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_STATION = 0
        private const val VIEW_TYPE_FOOTER = 1
    }

    // 当前选中的电台ID
    private var selectedStationId: String? = null

    // 正在播放的电台ID
    private var playingStationId: String? = null

    override fun getItemViewType(position: Int): Int {
        return if (position < itemCount - 1) {
            VIEW_TYPE_STATION
        } else {
            VIEW_TYPE_FOOTER
        }
    }

    override fun getItemCount(): Int {
        return super.getItemCount() + 1 // +1 for footer
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_FOOTER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_footer, parent, false)
            FooterViewHolder(view, onAddClick, onSettingsClick)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_station, parent, false)
            StationViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is StationViewHolder) {
            val station = getItem(position)
            holder.bind(station, station.id == selectedStationId, station.id == playingStationId)
        }
    }

    /**
     * 设置选中的电台
     */
    fun setSelectedStation(station: Station?) {
        val oldSelectedId = selectedStationId
        selectedStationId = station?.id

        // 更新旧选中项
        oldSelectedId?.let { id ->
            currentList.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let {
                notifyItemChanged(it)
            }
        }

        // 更新新选中项
        station?.id?.let { id ->
            currentList.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let {
                notifyItemChanged(it)
            }
        }
    }

    /**
     * 设置正在播放的电台
     */
    fun setPlayingStation(station: Station?) {
        val oldPlayingId = playingStationId
        playingStationId = station?.id

        // 更新旧播放项
        oldPlayingId?.let { id ->
            currentList.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let {
                notifyItemChanged(it)
            }
        }

        // 更新新播放项
        station?.id?.let { id ->
            currentList.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let {
                notifyItemChanged(it)
            }
        }
    }

    /**
     * 刷新列表
     */
    fun refreshList() {
        notifyDataSetChanged()
    }

    /**
     * 获取当前选中的电台
     */
    fun getSelectedStation(): Station? {
        return selectedStationId?.let { id ->
            currentList.find { it.id == id }
        }
    }

    /**
     * 获取指定位置的电台
     */
    fun getItemAt(position: Int): Station? {
        return if (position in 0 until itemCount) getItem(position) else null
    }

    inner class StationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.station_name)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.station_description)
        private val playingIndicator: View = itemView.findViewById(R.id.playing_indicator)

        fun bind(station: Station, isSelected: Boolean, isPlaying: Boolean) {
            nameTextView.text = station.name
            descriptionTextView.text = station.getDisplayName()
            descriptionTextView.visibility = if (station.description.isNotBlank()) {
                View.VISIBLE
            } else {
                View.GONE
            }

            // 设置选中状态
            itemView.isSelected = isSelected

            // 设置播放状态
            playingIndicator.visibility = if (isPlaying) View.VISIBLE else View.GONE

            // 设置背景
            itemView.setBackgroundResource(
                if (isSelected) R.drawable.bg_station_selected
                else R.drawable.bg_station_normal
            )

            // 点击事件
            itemView.setOnClickListener {
                onStationClick(station)
            }
        }
    }

    /**
     * 底部操作栏ViewHolder
     */
    inner class FooterViewHolder(
        itemView: View,
        private val onAddClick: () -> Unit,
        private val onSettingsClick: () -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        init {
            itemView.findViewById<View>(R.id.add_station_button).setOnClickListener {
                onAddClick()
            }
            itemView.findViewById<View>(R.id.settings_button).setOnClickListener {
                onSettingsClick()
            }
        }
    }

    /**
     * DiffUtil 回调
     */
    class StationDiffCallback : DiffUtil.ItemCallback<Station>() {
        override fun areItemsTheSame(oldItem: Station, newItem: Station): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Station, newItem: Station): Boolean {
            return oldItem == newItem
        }
    }
}
