package com.apexvpn.client.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.ListenableFuture

/**
 * 409. Плитка APEX VPN: состояние, сервер и большая кнопка включения.
 * Нажатие на кнопку отправляет телефону «переключить».
 */
class ApexTile : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        if (requestParams.currentState.lastClickableId == "toggle") toggle()
        val st = WatchState.load(this)
        val on = st.status == "started"
        val accent = if (on) 0xFF22D3EE.toInt() else 0xFF8E8E9A.toInt()

        fun text(t: String, size: Float, color: Int, bold: Boolean = false) = LayoutElementBuilders.Text.Builder()
            .setText(t)
            .setMaxLines(1)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                    .build(),
            )
            .build()

        val button = LayoutElementBuilders.Box.Builder()
            .setWidth(dp(76f))
            .setHeight(dp(76f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(if (on) 0xFF6D4AFF.toInt() else 0xFF2A2A33.toInt()))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(38f)).build())
                            .build(),
                    )
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId("toggle")
                            .setOnClick(ActionBuilders.LoadAction.Builder().build())
                            .build(),
                    )
                    .build(),
            )
            .addContent(text("⏻", 30f, 0xFFFFFFFF.toInt(), bold = true))
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text("APEX VPN", 12f, 0xFF8E8E9A.toInt()))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build())
            .addContent(button)
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build())
            .addContent(text(WatchState.label(st.status), 15f, 0xFFFFFFFF.toInt(), bold = true))
            .addContent(text(if (on && st.ping > 0) "${st.server} · ${st.ping} мс" else st.server.ifBlank { "Откройте APEX Watch" }, 12f, accent))
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RES)
            .setFreshnessIntervalMillis(60_000)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(column))
            .build()
        return CallbackToFutureAdapter.getFuture { it.set(tile); "tile" }
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { it.set(ResourceBuilders.Resources.Builder().setVersion(RES).build()); "res" }

    private fun toggle() {
        val ctx = this
        Wearable.getNodeClient(ctx).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { Wearable.getMessageClient(ctx).sendMessage(it.id, "/apex/toggle", ByteArray(0)) }
        }
    }

    private companion object {
        const val RES = "1"
    }
}
