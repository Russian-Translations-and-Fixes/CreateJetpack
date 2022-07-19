package com.possible_triangle.create_jetpack

import com.jozufozu.flywheel.core.PartialModel
import com.possible_triangle.create_jetpack.CreateJetpackMod.MOD_ID
import com.possible_triangle.create_jetpack.block.JetpackBlock
import com.possible_triangle.create_jetpack.capability.IJetpack
import com.possible_triangle.create_jetpack.capability.JetpackLogic
import com.possible_triangle.create_jetpack.client.ControlsDisplay
import com.possible_triangle.create_jetpack.client.JetpackArmorLayer
import com.possible_triangle.create_jetpack.item.Jetpack
import com.possible_triangle.create_jetpack.network.ControlManager
import com.possible_triangle.create_jetpack.network.ModNetwork
import com.simibubi.create.AllTags.pickaxeOnly
import com.simibubi.create.Create
import com.simibubi.create.content.AllSections
import com.simibubi.create.content.CreateItemGroup
import com.simibubi.create.content.curiosities.armor.CopperBacktankInstance
import com.simibubi.create.content.curiosities.armor.CopperBacktankItem.CopperBacktankBlockItem
import com.simibubi.create.content.curiosities.armor.CopperBacktankRenderer
import com.simibubi.create.content.curiosities.armor.CopperBacktankTileEntity
import com.simibubi.create.foundation.block.BlockStressDefaults
import com.simibubi.create.foundation.data.AssetLookup
import com.simibubi.create.foundation.data.CreateRegistrate
import com.simibubi.create.foundation.data.SharedProperties
import com.simibubi.create.repack.registrate.util.entry.ItemEntry
import com.simibubi.create.repack.registrate.util.nullness.NonNullFunction
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.loot.LootPool
import net.minecraft.world.level.storage.loot.LootTable
import net.minecraft.world.level.storage.loot.entries.LootItem
import net.minecraft.world.level.storage.loot.functions.CopyNameFunction
import net.minecraft.world.level.storage.loot.functions.CopyNbtFunction
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition
import net.minecraft.world.level.storage.loot.providers.nbt.ContextNbtProvider
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.common.capabilities.CapabilityManager
import net.minecraftforge.common.capabilities.CapabilityToken
import net.minecraftforge.common.capabilities.ICapabilityProvider
import net.minecraftforge.event.AttachCapabilitiesEvent
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import thedarkcolour.kotlinforforge.forge.FORGE_BUS
import java.util.function.BiConsumer
import java.util.function.BiFunction
import java.util.function.Supplier

object Content {

    val REGISTRATE = CreateRegistrate.lazy(MOD_ID).get()
        .creativeModeTab { CreateItemGroup.TAB_TOOLS }
        .startSection(AllSections.CURIOSITIES)

    //private val ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID)
    //private val BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID)
    //private val TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITIES, MOD_ID)
    //private val EFFECTS = DeferredRegister.create(ForgeRegistries.POTIONS, MOD_ID)
    //private val FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, MOD_ID)
    //private val RECIPE_SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MOD_ID)

    val JETPACK_BLOCK = REGISTRATE.block<JetpackBlock>("jetpack", ::JetpackBlock)
        .initialProperties { SharedProperties.copperMetal() }
        .blockstate { c, p ->
            p.horizontalBlock(c.entry,
                AssetLookup.partialBaseModel(c, p))
        }
        .transform(pickaxeOnly())
        .addLayer { Supplier { RenderType.cutoutMipped() } }
        .transform(BlockStressDefaults.setImpact(4.0))
        .loot { lt, block ->
            val builder = LootTable.lootTable()
            val survivesExplosion = ExplosionCondition.survivesExplosion()
            lt.add(block, builder.withPool(LootPool.lootPool()
                .`when`(survivesExplosion)
                .setRolls(ConstantValue.exactly(1F))
                .add(LootItem.lootTableItem(JETPACK.get())
                    .apply(CopyNameFunction.copyName(CopyNameFunction.NameSource.BLOCK_ENTITY))
                    .apply(CopyNbtFunction.copyData(ContextNbtProvider.BLOCK_ENTITY)
                        .copy("Air", "Air"))
                    .apply(CopyNbtFunction.copyData(ContextNbtProvider.BLOCK_ENTITY)
                        .copy("Enchantments", "Enchantments")))))
        }
        .register()

    val JETPACK_TILE = Create.registrate()
        .tileEntity("jetpack",
            ::CopperBacktankTileEntity)
        .instance {
            BiFunction { manager, tile ->
                CopperBacktankInstance(manager, tile)
            }
        }
        .validBlocks(JETPACK_BLOCK)
        .renderer {
            NonNullFunction { context: BlockEntityRendererProvider.Context? ->
                CopperBacktankRenderer(context)
            }
        }
        .register()

    val JETPACK_PLACEABLE = REGISTRATE.item<CopperBacktankBlockItem>("jetpack_placeable") {
        CopperBacktankBlockItem(JETPACK_BLOCK.get(), it)
    }.model { context, provider ->
        provider.withExistingParent(context.name, provider.mcLoc("item/barrier"))
    }.register()

    val JETPACK: ItemEntry<Jetpack> = REGISTRATE.item<Jetpack>("jetpack") { Jetpack(it, JETPACK_PLACEABLE) }
        .model(AssetLookup.customGenericItemModel("_", "item"))
        .register()

    val JETPACK_CAPABILITY = CapabilityManager.get(object : CapabilityToken<IJetpack>() {})

    val THRUSTERS_MODEL = PartialModel(ResourceLocation(MOD_ID, "block/jetpack/thrusters"))

    fun attachCapabilities(stack: ItemStack, add: BiConsumer<ResourceLocation, ICapabilityProvider>) {
        val item = stack.item
        if (item is Jetpack) add.accept(ResourceLocation(MOD_ID, "jetpack"), item)
    }

    fun register(modBus: IEventBus) {
        modBus.addListener { _: FMLCommonSetupEvent ->
            // TODO check if neccessary
            //CapabilityManager.INSTANCE.(IJetpack::class.java, JetpackStorage) { FakeJetpack }
            ModNetwork.init()
        }

        modBus.addListener { _: FMLClientSetupEvent ->
            ControlManager.registerKeybinds()
            ControlsDisplay.register()

            //InstancedRenderRegistry().register(Content.JETPACK_TILE, ::CopperBacktankInstance)
            //ClientRegistry.bindTileEntityRenderer(Content.JETPACK_TILE, ::CopperBacktankRenderer)
            //RenderTypeLookup.setRenderLayer(Content.JETPACK_BLOCK, RenderType.getCutoutMipped())
        }

        modBus.addListener { _: EntityRenderersEvent.AddLayers ->
            val dispatcher = Minecraft.getInstance().entityRenderDispatcher
            JetpackArmorLayer.registerOnAll(dispatcher)
        }

        FORGE_BUS.addListener(ControlManager::onDimensionChange)
        FORGE_BUS.addListener(ControlManager::onLogout)

        FORGE_BUS.addListener(JetpackLogic::tick)
        FORGE_BUS.addGenericListener(ItemStack::class.java) { event: AttachCapabilitiesEvent<ItemStack> ->
            attachCapabilities(event.`object`, event::addCapability)
        }

        FORGE_BUS.addListener(ControlManager::onTick)
        FORGE_BUS.addListener(ControlManager::onKey)
    }

}