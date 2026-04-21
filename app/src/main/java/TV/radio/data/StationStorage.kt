package tv.radio.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import java.io.File

class StationStorage(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "tv_radio_prefs"
        private const val KEY_LAST_PLAYED_ID = "last_played_id"
        private const val KEY_LAST_VOLUME = "last_volume"
        private const val KEY_LAST_POSITION = "last_position"
        private const val KEY_USE_EXO_PLAYER = "use_exo_player"
        private const val KEY_USE_HARDWARE_DECODE = "use_hardware_decode"
        private const val KEY_REMOTE_CONTROL_PLAY = "remote_control_play"
        private const val KEY_AUTO_PLAY_LAST_STATION = "auto_play_last_station"

        private const val M3U_DIR = "tvradio"
        private const val M3U_FILE = "radio.m3u"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // 内存缓存（本次运行期间的自定义列表）
    private var cachedStations: MutableList<Station>? = null

    private fun getM3UFile(): File {
        val dir = File(Environment.getExternalStorageDirectory(), M3U_DIR)
        return File(dir, M3U_FILE)
    }

    // ========== 电台列表读取（不依赖权限） ==========
    fun getStations(): List<Station> {
        // 优先返回缓存（如果有）
        cachedStations?.let { return it.toList() }

        val defaultStations = getDefaultStations()
        val m3uFile = getM3UFile()
        val stations = try {
            if (m3uFile.exists() && m3uFile.canRead()) {
                val parsed = parseM3U(m3uFile)
                if (parsed.isNotEmpty()) parsed else defaultStations
            } else {
                defaultStations
            }
        } catch (e: Exception) {
            e.printStackTrace()
            defaultStations
        }
        cachedStations = stations.toMutableList()
        return stations
    }

    // ========== 增删改（需要权限，返回是否成功写入文件） ==========
    fun addStation(station: Station): Boolean {
        val stations = getStations().toMutableList()
        if (stations.none { it.name == station.name && it.url == station.url }) {
            stations.add(station)
            cachedStations = stations
            return saveStationsToM3U(stations, getM3UFile())
        }
        return false
    }

    fun removeStation(station: Station): Boolean {
        val stations = getStations().toMutableList()
        val iterator = stations.iterator()
        var removed = false
        while (iterator.hasNext()) {
            if (iterator.next().id == station.id) {
                iterator.remove()
                removed = true
                break
            }
        }
        if (removed) {
            cachedStations = stations
            return saveStationsToM3U(stations, getM3UFile())
        }
        return false
    }

    fun removeStationById(stationId: String): Boolean {
        val stations = getStations().toMutableList()
        val iterator = stations.iterator()
        var removed = false
        while (iterator.hasNext()) {
            if (iterator.next().id == stationId) {
                iterator.remove()
                removed = true
                break
            }
        }
        if (removed) {
            cachedStations = stations
            return saveStationsToM3U(stations, getM3UFile())
        }
        return false
    }

    fun updateStation(station: Station): Boolean {
        val stations = getStations().toMutableList()
        val index = stations.indexOfFirst { it.id == station.id }
        if (index != -1) {
            stations[index] = station
            cachedStations = stations
            return saveStationsToM3U(stations, getM3UFile())
        }
        return false
    }

    // 写入 M3U 文件（可能因权限不足失败）
    private fun saveStationsToM3U(stations: List<Station>, m3uFile: File): Boolean {
        return try {
            val dir = m3uFile.parentFile
            if (dir?.exists() == false) {
                dir.mkdirs()
            }
            m3uFile.bufferedWriter().use { writer ->
                writer.write("#EXTM3U\n")
                stations.forEach { station ->
                    val displayName = if (station.description.isNotBlank()) {
                        "${station.name} - ${station.description}"
                    } else {
                        station.name
                    }
                    writer.write("#EXTINF:0,$displayName\n")
                    writer.write("${station.url}\n")
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun parseM3U(m3uFile: File): List<Station> {
        val stations = mutableListOf<Station>()
        try {
            val lines = m3uFile.readLines()
            var i = 0
            while (i < lines.size) {
                var line = lines[i].trim()
                if (line.isEmpty() || line == "#EXTM3U") {
                    i++
                    continue
                }
                if (line.startsWith("#EXTINF:")) {
                    val displayName = line.substringAfter(",", "").trim()
                    var name = displayName
                    var description = ""
                    if (displayName.contains(" - ")) {
                        val parts = displayName.split(" - ", limit = 2)
                        name = parts[0].trim()
                        description = parts[1].trim()
                    }
                    i++
                    if (i < lines.size) {
                        val url = lines[i].trim()
                        if (url.isNotEmpty() && !url.startsWith("#")) {
                            stations.add(Station(name = name, url = url, description = description))
                        }
                    }
                } else {
                    val url = line
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        stations.add(Station(name = url.substringAfterLast("/", "未知电台"), url = url))
                    }
                }
                i++
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return stations
    }

    // ========== 默认电台列表 ==========
    private fun getDefaultStations(): List<Station> {
        return listOf(
            Station("湖南交通广播", "http://a.live.hnradio.com/jtpd/radio120k_jtpd.m3u8?auth_key=1588751155-0-0-301d7e28868eff70a72edf5e4569b546", "湖南交通广播"),
            Station("CNR-1-中国之声", "http://ngcdn001.cnr.cn/live/zgzs/index.m3u8", "CNR-1-中国之声"),
            Station("CNR-15 中国交通广播", "https://ngcdn002.cnr.cn/live/gsgljtgb/index.m3u8", "CNR-15 中国交通广播"),
            Station("CMG环球资讯广播", "https://sk.cri.cn/905.m3u8", "CMG环球资讯广播"),
            Station("CNR-2 经济之声", "http://ngcdn002.cnr.cn/live/jjzs/index.m3u8", "CNR-2 经济之声"),
            Station("AsiaFM高清音乐台", "http://asiafm.hk:8000/asiahd", "AsiaFM高清音乐台"),
            Station("AsiaFM 亚洲热歌台", "http://hot.asiafm.net:8000/asiafm", "AsiaFM 亚洲热歌台"),
            Station("AsiaFM亚洲经典台", "http://goldfm.cn:8000/goldfm", "AsiaFM亚洲经典台"),
            Station("AsiaFM 亚洲音乐台", "http://asiafm.hk:8000/asiafm", "AsiaFM 亚洲音乐台"),
            Station("深圳交通广播", "http://lhttp.qingting.fm/live/1272/64k.mp3", "深圳交通广播"),
            Station("上海交通广播", "http://lhttp.qingting.fm/live/266/64k.mp3", "上海交通广播"),
            Station("青岛交通广播", "http://lhttp.qingting.fm/live/1676/64k.mp3", "青岛交通广播"),
            Station("北京交通广播", "https://lhttp.qingting.fm/live/336/64k.mp3", "北京交通广播"),
            Station("常德交通广播", "https://lhttp.qingting.fm/live/15318209/64k.mp3", "常德交通广播")
        )
    }

    // ========== 配置项（SharedPreferences 不变） ==========
    fun saveLastPlayed(station: Station) {
        prefs.edit().putString(KEY_LAST_PLAYED_ID, station.id).apply()
    }
    fun getLastPlayedId(): String? = prefs.getString(KEY_LAST_PLAYED_ID, null)
    fun getLastPlayed(): Station? = getLastPlayedId()?.let { id -> getStations().find { it.id == id } }
    fun saveVolume(volume: Float) { prefs.edit().putFloat(KEY_LAST_VOLUME, volume).apply() }
    fun getVolume(): Float = prefs.getFloat(KEY_LAST_VOLUME, 1.0f)
    fun savePosition(position: Long) { prefs.edit().putLong(KEY_LAST_POSITION, position).apply() }
    fun getPosition(): Long = prefs.getLong(KEY_LAST_POSITION, 0L)
    fun saveUseExoPlayer(useExoPlayer: Boolean) {
        prefs.edit().putBoolean(KEY_USE_EXO_PLAYER, useExoPlayer).apply()
    }
    fun getUseExoPlayer(): Boolean {
        return prefs.getBoolean(KEY_USE_EXO_PLAYER, true) // 默认使用ExoPlayer
    }
    fun saveUseHardwareDecode(useHardware: Boolean) { prefs.edit().putBoolean(KEY_USE_HARDWARE_DECODE, useHardware).apply() }
    fun getUseHardwareDecode(): Boolean = prefs.getBoolean(KEY_USE_HARDWARE_DECODE, true)
    fun saveRemoteControlPlay(enabled: Boolean) { prefs.edit().putBoolean(KEY_REMOTE_CONTROL_PLAY, enabled).apply() }
    fun getRemoteControlPlay(): Boolean = prefs.getBoolean(KEY_REMOTE_CONTROL_PLAY, true)
    fun saveAutoPlayLastStation(enabled: Boolean) { prefs.edit().putBoolean(KEY_AUTO_PLAY_LAST_STATION, enabled).apply() }
    fun getAutoPlayLastStation(): Boolean = prefs.getBoolean(KEY_AUTO_PLAY_LAST_STATION, true)
    fun clearAll() {
        prefs.edit().clear().apply()
        cachedStations = null
        try { getM3UFile().delete() } catch (_: Exception) {}
    }

    /**
     * 检查是否为首次运行
     */
    fun isFirstRun(): Boolean {
        return true
    }

    /**
     * 标记首次运行已完成
     */
    fun markFirstRunComplete() {
        // 无需操作
    }
}