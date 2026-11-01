package works.axiora.z370

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

/**
 * Downloads the whole UAE for offline use: zoom 0–14 stored, the map over-zooms the vector
 * tiles for street level, so detail holds up without the much larger zoom 15–16 sets.
 */
object OfflineMap {
    val status = mutableStateOf("")
    val busy = mutableStateOf(false)

    private const val NAME = "uae"
    private val UAE = LatLngBounds.Builder()
        .include(LatLng(22.55, 51.45))
        .include(LatLng(26.15, 56.45))
        .build()

    fun start(ctx: Context) {
        if (busy.value) return
        busy.value = true
        status.value = "Preparing…"
        MapLibre.getInstance(ctx.applicationContext)
        val om = OfflineManager.getInstance(ctx.applicationContext)
        om.setOfflineMapboxTileCountLimit(200_000)
        om.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val existing = offlineRegions?.firstOrNull { String(it.metadata) == NAME }
                if (existing != null) resume(existing) else create(ctx, om)
            }
            override fun onError(error: String) = fail(error)
        })
    }

    private fun create(ctx: Context, om: OfflineManager) {
        val def = OfflineTilePyramidRegionDefinition(
            MAP_STYLE, UAE, 0.0, 14.0, ctx.resources.displayMetrics.density,
        )
        om.createOfflineRegion(def, NAME.toByteArray(), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) = resume(offlineRegion)
            override fun onError(error: String) = fail(error)
        })
    }

    private fun resume(region: OfflineRegion) {
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(s: OfflineRegionStatus) {
                val mb = s.completedResourceSize / 1_048_576.0
                if (s.isComplete) {
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    status.value = "UAE map saved · %.0f MB · works without internet".format(mb)
                    busy.value = false
                } else {
                    val pct = if (s.requiredResourceCount > 0) 100.0 * s.completedResourceCount / s.requiredResourceCount else 0.0
                    status.value = "Downloading UAE · %.0f%% · %.0f MB so far".format(pct, mb)
                }
            }
            override fun onError(error: OfflineRegionError) {
                status.value = "Paused: ${error.message}. It retries by itself."
            }
            override fun mapboxTileCountLimitExceeded(limit: Long) = fail("tile limit $limit reached")
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
    }

    private fun fail(msg: String) {
        status.value = "Download failed: $msg"
        busy.value = false
    }
}
