package com.lambda.client.event.events

import com.lambda.client.event.Event
import net.minecraft.block.state.IBlockState
import net.minecraft.util.math.BlockPos

class BlockPlaceEvent(val position: BlockPos, val blockState: IBlockState): Event