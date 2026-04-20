package TV.radio

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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import TV.radio.data.Station
import TV.radio.data.StationStorage
import TV.radio.player.ExoPlayerManager
import TV.radio.ui.PlaybackState
import TV.radio.ui.StationAdapter
import TV.radio.databinding.ActivityMainBinding
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

        stationStorage = StationStorage(this)
        playerManager = ExoPlayerManager.getInstance(this)
        playerManager.initialize()

        setupUI()
        setupPlayerManager()

        // 直接加载电台列表，不检查权限
        loadStations()
        restoreLastPlayed()
    }

    private fun setupUI() {
        // 设置RecyclerView
        binding.stationsRecyclerView.layoutManager = LinearLayoutManager(this)
        stationAdapter = StationAdapter(
            onStationClick = { station ->
                selectStation(station)
            },
            onDeleteClick = { station ->
                deleteStation(station)
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

        binding.settingsButton.setOnClickListener {
            showSettingsDialog()
        }

        binding.addStationButton.setOnClickListener {
            showAddStationDialog()
        }
    }

    private fun setupPlayerManager() {
        CoroutineScope(Dispatchers.Main).launch {
            playerManager.playbackState.collectLatest {
                updatePlaybackState(it)
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

    private fun updatePlaybackState(state: PlaybackState) {
        when (state) {
            is PlaybackState.Playing -> {
                binding.playPauseButton.setImageResource(R.drawable.ic_pause)
                binding.statusTextView.text = "正在播放"
                binding.songTitleTextView.text = state.stationName
                binding.songTitleTextView.visibility = View.VISIBLE
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
            playerManager.playStation(it)
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

        volumeSlider.value = stationStorage.getVolume().toFloat()
        radioHardware.isChecked = stationStorage.getUseHardwareDecode()
        radioSoftware.isChecked = !stationStorage.getUseHardwareDecode()

        dialog.setTitle("设置")
        dialog.setPositiveButton("保存") { _, _ ->
            val volume = volumeSlider.value.toFloat()
            val useHardwareDecode = radioHardware.isChecked

            stationStorage.saveVolume(volume)
            stationStorage.saveUseHardwareDecode(useHardwareDecode)
            playerManager.setVolume(volume)

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
}