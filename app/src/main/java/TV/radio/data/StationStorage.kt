package TV.radio.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.google.gson.Gson
import java.io.File

/**
 * 电台存储管理器
 * - 电台列表使用 /sdcard/tvradio/radio.m3u 文件存储
 * - 配置项（音量、解码方式、上次播放ID等）仍使用 SharedPreferences
 */
class StationStorage(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "tv_radio_prefs"
        private const val KEY_LAST_PLAYED_ID = "last_played_id"
        private const val KEY_LAST_VOLUME = "last_volume"
        private const val KEY_LAST_POSITION = "last_position"
        private const val KEY_USE_EXO_PLAYER = "use_exo_player"
        private const val KEY_USE_HARDWARE_DECODE = "use_hardware_decode"

        // M3U 文件路径
        private const val M3U_DIR = "tvradio"
        private const val M3U_FILE = "radio.m3u"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 获取 M3U 文件对象（确保目录存在）
     */
    private fun getM3UFile(): File {
        val dir = File(Environment.getExternalStorageDirectory(), M3U_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, M3U_FILE)
    }

    // ==================== 电台列表（M3U）读写 ====================

    /**
     * 获取电台列表（从 M3U 文件读取，首次运行自动生成）
     */
    fun getStations(): List<Station> {
        val m3uFile = getM3UFile()
        return if (m3uFile.exists()) {
            parseM3U(m3uFile)
        } else {
            // 文件不存在：生成默认电台列表并写入 M3U
            val defaultStations = getDefaultStations()
            saveStationsToM3U(defaultStations, m3uFile)
            defaultStations
        }
    }

    /**
     * 保存电台列表到 M3U 文件
     */
    private fun saveStationsToM3U(stations: List<Station>, m3uFile: File) {
        try {
            m3uFile.bufferedWriter().use { writer ->
                writer.write("#EXTM3U\n")
                stations.forEach { station ->
                    // 显示名称：name + description（如果有）
                    val displayName = if (station.description.isNotBlank()) {
                        "${station.name} - ${station.description}"
                    } else {
                        station.name
                    }
                    writer.write("#EXTINF:0,$displayName\n")
                    writer.write("${station.url}\n")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 解析 M3U 文件为 Station 列表
     */
    private fun parseM3U(m3uFile: File): List<Station> {
        val stations = mutableListOf<Station>()
        try {
            val lines = m3uFile.readLines()
            var i = 0
            while (i < lines.size) {
                var line = lines[i].trim()
                // 跳过空行和注释头
                if (line.isEmpty() || line == "#EXTM3U") {
                    i++
                    continue
                }
                // 处理 #EXTINF 行
                if (line.startsWith("#EXTINF:")) {
                    // 提取显示名称（逗号后部分）
                    val displayName = line.substringAfter(",", "").trim()
                    var name = displayName
                    var description = ""
                    // 简单拆分：如果有 " - "，则前面为 name，后面为 description
                    if (displayName.contains(" - ")) {
                        val parts = displayName.split(" - ", limit = 2)
                        name = parts[0].trim()
                        description = parts[1].trim()
                    }
                    i++
                    // 下一行应为 URL
                    if (i < lines.size) {
                        val url = lines[i].trim()
                        if (url.isNotEmpty() && !url.startsWith("#")) {
                            stations.add(
                                Station(
                                    name = name,
                                    url = url,
                                    description = description
                                )
                            )
                        }
                    }
                } else {
                    // 无 EXTINF 的纯 URL 行（简单兼容）
                    val url = line
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        stations.add(
                            Station(
                                name = url.substringAfterLast("/", "未知电台"),
                                url = url,
                                description = ""
                            )
                        )
                    }
                }
                i++
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return stations
    }

    /**
     * 添加电台（追加到 M3U 文件）
     */
    fun addStation(station: Station) {
        val stations = getStations().toMutableList()
        // 去重：同名且同 URL 不重复添加
        if (stations.none { it.name == station.name && it.url == station.url }) {
            stations.add(station)
            saveStationsToM3U(stations, getM3UFile())
        }
    }

    /**
     * 删除电台
     */
    fun removeStation(station: Station) {
        val stations = getStations().toMutableList()
        val iterator = stations.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().id == station.id) {
                iterator.remove()
                break
            }
        }
        saveStationsToM3U(stations, getM3UFile())
    }

    /**
     * 删除电台 by ID
     */
    fun removeStationById(stationId: String) {
        val stations = getStations().toMutableList()
        val iterator = stations.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().id == stationId) {
                iterator.remove()
                break
            }
        }
        saveStationsToM3U(stations, getM3UFile())
    }

    /**
     * 更新电台（先删后增）
     */
    fun updateStation(station: Station) {
        val stations = getStations().toMutableList()
        val index = stations.indexOfFirst { it.id == station.id }
        if (index != -1) {
            stations[index] = station
            saveStationsToM3U(stations, getM3UFile())
        }
    }

    // ==================== 默认电台列表 ====================
    private fun getDefaultStations(): List<Station> {
        return listOf(
            Station(
                name = "湖南交通广播",
                url = "http://a.live.hnradio.com/jtpd/radio120k_jtpd.m3u8?auth_key=1588751155-0-0-301d7e28868eff70a72edf5e4569b546",
                description = "湖南交通广播"
            ),
            Station(
                name = "CNR-1-中国之声",
                url = "http://ngcdn001.cnr.cn/live/zgzs/index.m3u8",
                description = "CNR-1-中国之声"
            ),
            Station(
                name = "CNR-15 中国交通广播",
                url = "https://ngcdn002.cnr.cn/live/gsgljtgb/index.m3u8",
                description = "CNR-15 中国交通广播"
            ),
            Station(
                name = "CMG环球资讯广播",
                url = "https://sk.cri.cn/905.m3u8",
                description = "CMG环球资讯广播"
            ),
            Station(
                name = "CNR-2 经济之声",
                url = "http://ngcdn002.cnr.cn/live/jjzs/index.m3u8",
                description = "CNR-2 经济之声"
            ),
            Station(
                name = "AsiaFM高清音乐台",
                url = "http://asiafm.hk:8000/asiahd",
                description = "AsiaFM高清音乐台"
            ),
            Station(
                name = "AsiaFM 亚洲热歌台",
                url = "http://hot.asiafm.net:8000/asiafm",
                description = "AsiaFM 亚洲热歌台"
            ),
            Station(
                name = "AsiaFM亚洲经典台",
                url = "http://goldfm.cn:8000/goldfm",
                description = "AsiaFM亚洲经典台"
            ),
            Station(
                name = "AsiaFM 亚洲音乐台",
                url = "http://asiafm.hk:8000/asiafm",
                description = "AsiaFM 亚洲音乐台"
            ),
            Station(
                name = "深圳交通广播",
                url = "http://lhttp.qingting.fm/live/1272/64k.mp3",
                description = "深圳交通广播"
            ),
            Station(
                name = "上海交通广播",
                url = "http://lhttp.qingting.fm/live/266/64k.mp3",
                description = "上海交通广播"
            ),
            Station(
                name = "青岛交通广播",
                url = "http://lhttp.qingting.fm/live/1676/64k.mp3",
                description = "青岛交通广播"
            ),
            Station(
                name = "北京交通广播",
                url = "https://lhttp.qingting.fm/live/336/64k.mp3",
                description = "北京交通广播"
            ),
            Station(
                name = "常德交通广播",
                url = "https://lhttp.qingting.fm/live/15318209/64k.mp3",
                description = "常德交通广播"
            )
        )
    }

    // ==================== 配置项（SharedPreferences） ====================
    fun saveLastPlayed(station: Station) {
        prefs.edit().putString(KEY_LAST_PLAYED_ID, station.id).apply()
    }

    fun getLastPlayedId(): String? = prefs.getString(KEY_LAST_PLAYED_ID, null)

    fun getLastPlayed(): Station? {
        val lastId = getLastPlayedId() ?: return null
        return getStations().find { it.id == lastId }
    }

    fun saveVolume(volume: Float) {
        prefs.edit().putFloat(KEY_LAST_VOLUME, volume).apply()
    }

    fun getVolume(): Float = prefs.getFloat(KEY_LAST_VOLUME, 1.0f)

    fun savePosition(position: Long) {
        prefs.edit().putLong(KEY_LAST_POSITION, position).apply()
    }

    fun getPosition(): Long = prefs.getLong(KEY_LAST_POSITION, 0L)

    fun saveUseExoPlayer(useExoPlayer: Boolean) {
        prefs.edit().putBoolean(KEY_USE_EXO_PLAYER, useExoPlayer).apply()
    }

    fun getUseExoPlayer(): Boolean {
        return prefs.getBoolean(KEY_USE_EXO_PLAYER, true) // 默认使用ExoPlayer
    }

    fun saveUseHardwareDecode(useHardware: Boolean) {
        prefs.edit().putBoolean(KEY_USE_HARDWARE_DECODE, useHardware).apply()
    }

    fun getUseHardwareDecode(): Boolean = prefs.getBoolean(KEY_USE_HARDWARE_DECODE, true)

    fun clearAll() {
        prefs.edit().clear().apply()
        getM3UFile().delete()
    }

    /**
     * 检查是否为首次运行
     */
    fun isFirstRun(): Boolean {
        return !getM3UFile().exists()
    }

    /**
     * 标记首次运行已完成
     */
    fun markFirstRunComplete() {
        // 首次运行后会生成M3U文件，所以只需检查文件是否存在即可
    }
}