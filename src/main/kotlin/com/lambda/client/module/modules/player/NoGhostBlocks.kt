package com.lambda.client.module.modules.player

import com.lambda.client.LambdaMod
import com.lambda.client.event.events.BlockPlaceEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.graphics.GeometryMasks
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.safeListener
import com.lambda.mixin.world.MixinItemBlock
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.minecraft.block.state.IBlockState
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent

/**
 * @see MixinItemBlock.ignoreSetBlockState
 */
object NoGhostBlocks : Module(
    name = "NoGhostBlocks",
    alias = arrayOf("NoGlitchBlocks"),
    description = "Syncs block interactions for strict environments",
    category = Category.PLAYER
) {
    private val renderHologram by setting("Render Hologram", true, description = "Render a hologram of the block until it is confirmed server side")
    private val filled by setting("Filled", true, { renderHologram })
    private val outline by setting("Outline", true, { renderHologram })
    private val aFilled by setting("Filled Alpha", 31, 0..255, 1, { renderHologram && filled })
    private val aOutline by setting("Outline Alpha", 127, 0..255, 1, { renderHologram && outline })
    private var blockPositions = HashMap<BlockPos, IBlockState>()
    private val blockRenderer = ESPRenderer()
    private var blockRenderUpdateJob: Job? = null
    init {
        safeListener<BlockPlaceEvent> {
            blockPositions[it.position] = it.blockState
        }
        safeListener<PacketEvent.Receive> {
            if(it.packet !is SPacketBlockChange || !blockPositions.containsKey(it.packet.blockPosition)) return@safeListener
            blockPositions.remove(it.packet.blockPosition)
        }
        safeListener<TickEvent.ClientTickEvent> {
            if (blockRenderUpdateJob == null || blockRenderUpdateJob?.isCompleted == true) {
                blockRenderUpdateJob = defaultScope.launch {
                    blockRenderer.aFilled = aFilled
                    blockRenderer.aOutline = aOutline
                    blockRenderer.aTracer = 0
                    val renderList = blockPositions.map {
                        val colorInt = it.value.getMapColor(world, it.key).colorValue
                        val color = ColorHolder((colorInt shr 16), (colorInt shr 8 and 255), (colorInt and 255))
                        Triple(
                            it.value.getSelectedBoundingBox(world, it.key),
                            color,
                            GeometryMasks.Quad.ALL
                        )
                    }
                    blockRenderer.replaceAll(renderList.toMutableList())
                }
            }
        }
        safeListener<RenderWorldEvent> {
            if(!renderHologram) return@safeListener
            blockRenderer.render(false)
        }
        onDisable {
            blockPositions.clear()
            blockRenderer.clear()
            blockRenderUpdateJob?.cancel()
        }
    }
}