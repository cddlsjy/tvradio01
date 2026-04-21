package tv.radio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import tv.radio.data.Station
import tv.radio.data.StationStorage
import tv.radio.player.ExoPlayerManager
import tv.radio.ui.PlaybackState
import tv.radio.ui.StationAdapter
import tv.radio.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var stationStorage: StationStorage
    private lateinit var playerManager: ExoPlayerManager
    private lateinit var stationAdapter: StationAdapter

    private var selectedStation: Station? = null
    private var stations: List<Station> = emptyList()

    // 开关状态
    private var remoteControlAutoPlay = true      // 遥控器自动播放
    private var autoPlayLastStation = true        // 启动时自动播放上次电台

    // 暂存待操作的电台
    private var pendingAddStation: Station? = null
    private var pendingDeleteStation: Station? = null

    companion object {
        private const val REQUEST_CODE_STORAGE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 隐藏ActionBar
        supportActionBar?.hide()

        stationStorage = StationStorage(this)
        playerManager = ExoPlayerManager.getInstance(this)
        playerManager.initialize()

        setupUI()
        setupPlayerManager()

        // 让RecyclerView请求焦点，确保按键事件能正确传递
        binding.stationsRecyclerView.requestFocus()

        // 加载设置
        remoteControlAutoPlay = stationStorage.getRemoteControlPlay()
        autoPlayLastStation = stationStorage.getAutoPlayLastStation()

        // 直接加载电台列表，不检查权限
        loadStations()
        restoreLastPlayed()
    }

    private fun setupUI() {
        // 设置RecyclerView
        binding.stationsRecyclerView.layoutManager = LinearLayoutManager(this)
        stationAdapter = StationAdapter(
            onStationClick = { station ->
                // 选中电台并立即播放
                selectStation(station)
                playerManager.playStation(station)
            },
            onDeleteClick = { station ->
                deleteStation(station)
            },
            onAddClick = {
                showAddStationDialog()
            },
            onSettingsClick = {
                showSettingsDialog()
            }
        )
        binding.stationsRecyclerView.adapter = stationAdapter

        binding.playPauseButton.setOnClickListener {
            selectedStation?.let {
                if (playerManager.isPlaying()) {
                    playerManager.pause()
                } else {
                    playerManager.playStation(it)
                }
            }
        }

        // 隐藏底部的设置和添加按钮
        binding.settingsButton.visibility = View.GONE
        binding.addStationButton.visibility = View.GONE
    }

    private fun setupPlayerManager() {
        // 播放状态订阅
        CoroutineScope(Dispatchers.Main).launch {
            playerManager.playbackState.collectLatest { state ->
                updatePlaybackState(state)
            }
        }
        // 歌曲元数据订阅
        CoroutineScope(Dispatchers.Main).launch {
            playerManager.metadataFlow.collectLatest { metadata ->
                binding.songTitleTextView.text = metadata
                binding.songTitleTextView.visibility = if (metadata.isNotBlank()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun loadStations() {
        stations = stationStorage.getStations()
        stationAdapter.submitList(stations)
        
        // 显示空视图
        if (stations.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
        } else {
            binding.emptyView.visibility = View.GONE
        }
    }

    private fun selectStation(station: Station) {
        selectedStation = station
        stationAdapter.setSelectedStation(station)
        stationStorage.saveLastPlayed(station)
        binding.songTitleTextView.text = station.name
    }

    /**
     * 播放电台并更新UI
     */
    private fun playStationAndUpdateUI(station: Station) {
        selectStation(station)
        playerManager.playStation(station)
    }

    private fun updatePlaybackState(state: PlaybackState) {
        when (state) {
            is PlaybackState.Playing -> {
                binding.playPauseButton.setImageResource(R.drawable.ic_pause)
                binding.statusTextView.text = "正在播放"
                // 仅在没有元数据时才显示电台名称
                if (binding.songTitleTextView.text.isBlank()) {
                    binding.songTitleTextView.text = state.stationName
                    binding.songTitleTextView.visibility = View.VISIBLE
                }
            }
            is PlaybackState.Paused -> {
                binding.playPauseButton.setImageResource(R.drawable.ic_play)
                binding.statusTextView.text = "已暂停"
            }
            is PlaybackState.Stopped -> {
                binding.playPauseButton.setImageResource(R.drawable.ic_play)
                binding.statusTextView.text = "已停止"
                binding.songTitleTextView.visibility = View.GONE
            }
            is PlaybackState.Buffering -> {
                binding.statusTextView.text = "正在缓冲"
            }
            is PlaybackState.Error -> {
                binding.playPauseButton.setImageResource(R.drawable.ic_play)
                binding.statusTextView.text = "播放错误"
                Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun restoreLastPlayed() {
        val lastStation = stationStorage.getLastPlayed()
        lastStation?.let {
            selectStation(it)
            if (autoPlayLastStation) {
                playerManager.playStation(it)
            }
        }
    }

    private fun showStationOptions(station: Station) {
        val options = arrayOf(
            "播放电台",
            "编辑电台",
            "删除电台"
        )

        AlertDialog.Builder(this)
            .setTitle(station.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> selectStation(station)
                    1 -> editStation(station)
                    2 -> deleteStation(station)
                }
            }
            .show()
    }

    private fun editStation(station: Station) {
        val dialog = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_add_station, null)
        dialog.setView(view)

        val nameInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.station_name_input)
        val urlInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.station_url_input)
        val descriptionInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.station_description_input)

        nameInput.setText(station.name)
        urlInput.setText(station.url)
        descriptionInput.setText(station.description)

        dialog.setTitle("编辑电台")
        dialog.setPositiveButton("保存") { _, _ ->
            val updatedStation = Station(
                id = station.id,
                name = nameInput.text.toString().trim(),
                url = urlInput.text.toString().trim(),
                description = descriptionInput.text.toString().trim()
            )

            if (updatedStation.isValid()) {
                stationStorage.updateStation(updatedStation)
                loadStations()
                if (selectedStation?.id == station.id) {
                    selectStation(updatedStation)
                }
                Toast.makeText(this, "电台已更新", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "无效的电台信息", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.setNegativeButton("取消", null)
        dialog.show()
    }

    private fun addStation(station: Station) {
        if (!station.isValid()) {
            Toast.makeText(this, "无效的电台信息", Toast.LENGTH_SHORT).show()
            return
        }

        if (hasStoragePermission()) {
            // 已有权限，直接保存
            performAddStation(station)
        } else {
            // 无权限，请求权限
            pendingAddStation = station
            requestStoragePermission()
        }
    }

    private fun performAddStation(station: Station) {
        val saved = stationStorage.addStation(station)
        loadStations()
        if (saved) {
            Toast.makeText(this, "电台已添加", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "电台已添加至列表，但因权限限制未保存到文件", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 删除电台
     */
    private fun deleteStation(station: Station) {
        if (hasStoragePermission()) {
            // 已有权限，直接删除
            performDeleteStation(station)
        } else {
            // 无权限，请求权限
            pendingDeleteStation = station
            requestStoragePermission()
        }
    }

    private fun performDeleteStation(station: Station) {
        if (playerManager.getCurrentStation()?.id == station.id) {
            playerManager.stop()
        }

        if (selectedStation?.id == station.id) {
            selectedStation = null
        }

        val deleted = stationStorage.removeStation(station)
        loadStations()
        if (deleted) {
            Toast.makeText(this, "电台已删除", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "电台已删除，但因权限限制未更新文件", Toast.LENGTH_LONG).show()
        }
    }

    private fun showAddStationDialog() {
        val dialog = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_add_station, null)
        dialog.setView(view)

        val nameInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.station_name_input)
        val urlInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.station_url_input)
        val descriptionInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.station_description_input)

        dialog.setTitle("添加电台")
        dialog.setPositiveButton("保存") { _, _ ->
            val station = Station(
                name = nameInput.text.toString().trim(),
                url = urlInput.text.toString().trim(),
                description = descriptionInput.text.toString().trim()
            )
            addStation(station)
        }
        dialog.setNegativeButton("取消", null)
        dialog.show()
    }

    private fun showSettingsDialog() {
        val dialog = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        dialog.setView(view)

        val volumeSlider = view.findViewById<com.google.android.material.slider.Slider>(R.id.volume_slider)
        val decodeModeGroup = view.findViewById<android.widget.RadioGroup>(R.id.decode_mode_group)
        val radioHardware = view.findViewById<android.widget.RadioButton>(R.id.radio_hardware)
        val radioSoftware = view.findViewById<android.widget.RadioButton>(R.id.radio_software)
        val autoPlaySwitch = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.auto_play_switch)
        val autoPlayLastStationSwitch = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.auto_play_last_station_switch)

        volumeSlider.value = stationStorage.getVolume().toFloat()
        radioHardware.isChecked = stationStorage.getUseHardwareDecode()
        radioSoftware.isChecked = !stationStorage.getUseHardwareDecode()
        autoPlaySwitch.isChecked = stationStorage.getRemoteControlPlay()
        autoPlayLastStationSwitch.isChecked = stationStorage.getAutoPlayLastStation()

        dialog.setTitle("设置")
        dialog.setPositiveButton("保存") { _, _ ->
            val volume = volumeSlider.value.toFloat()
            val useHardwareDecode = radioHardware.isChecked

            stationStorage.saveVolume(volume)
            stationStorage.saveUseHardwareDecode(useHardwareDecode)
            stationStorage.saveRemoteControlPlay(autoPlaySwitch.isChecked)
            stationStorage.saveAutoPlayLastStation(autoPlayLastStationSwitch.isChecked)
            playerManager.setVolume(volume)

            // 同步更新成员变量
            remoteControlAutoPlay = autoPlaySwitch.isChecked
            autoPlayLastStation = autoPlayLastStationSwitch.isChecked

            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        }
        dialog.setNegativeButton("取消", null)
        dialog.show()
    }

    private fun hasStoragePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 5.1 及以下不需要动态权限
        }
    }

    private fun requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_CODE_STORAGE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予，执行暂存的操作
                pendingAddStation?.let {
                    performAddStation(it)
                    pendingAddStation = null
                }
                pendingDeleteStation?.let {
                    performDeleteStation(it)
                    pendingDeleteStation = null
                }
            } else {
                Toast.makeText(this, "未授予存储权限，电台将仅在本次运行中生效", Toast.LENGTH_LONG).show()
                // 即使无权限，也应在内存中添加/删除（保证UI一致性）
                pendingAddStation?.let {
                    performAddStation(it)
                    pendingAddStation = null
                }
                pendingDeleteStation?.let {
                    performDeleteStation(it)
                    pendingDeleteStation = null
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        playerManager.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager.release()
    }

    override fun onBackPressed() {
        if (playerManager.isPlaying()) {
            playerManager.stop()
        }
        super.onBackPressed()
    }

    /**
     * 移动选择
     */
    private fun moveSelection(delta: Int) {
        val stationCount = stations.size
        if (stationCount == 0) return

        // 获取当前选中电台的位置，若未选中则根据方向决定起始位置
        val currentPos = if (selectedStation == null) {
            if (delta > 0) -1 else stationCount
        } else {
            stations.indexOfFirst { it.id == selectedStation?.id }.coerceAtLeast(0)
        }

        var newPos = currentPos + delta
        // 循环处理：超出顶部则跳到底部，超出底部则跳到顶部
        if (newPos < 0) newPos = stationCount - 1
        else if (newPos >= stationCount) newPos = 0

        val station = stations[newPos]
        // 更新选中状态并滚动到对应位置
        stationAdapter.setSelectedStation(station)
        binding.stationsRecyclerView.smoothScrollToPosition(newPos)
        // 根据用户设置决定是否自动播放
        if (remoteControlAutoPlay) {
            playStationAndUpdateUI(station)
        }
    }

    /**
     * 处理遥控器按键事件
     */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                moveSelection(-1)   // 向上移动
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                moveSelection(1)    // 向下移动
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER -> {
                // 遥控器 OK 键：播放或暂停当前选中的电台
                selectedStation?.let { station ->
                    if (playerManager.isPlaying()) {
                        playerManager.pause()
                    } else {
                        playerManager.playStation(station)
                    }
                }
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}