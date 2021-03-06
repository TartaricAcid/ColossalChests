package org.cyclops.colossalchests.block;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.tuple.Pair;
import org.cyclops.colossalchests.Advancements;
import org.cyclops.colossalchests.client.gui.container.GuiColossalChest;
import org.cyclops.colossalchests.inventory.container.ContainerColossalChest;
import org.cyclops.colossalchests.tileentity.TileColossalChest;
import org.cyclops.cyclopscore.block.multi.CubeDetector;
import org.cyclops.cyclopscore.block.multi.DetectionResult;
import org.cyclops.cyclopscore.block.property.BlockProperty;
import org.cyclops.cyclopscore.block.property.BlockPropertyManagerComponent;
import org.cyclops.cyclopscore.config.configurable.ConfigurableBlockContainerGui;
import org.cyclops.cyclopscore.config.extendedconfig.BlockConfig;
import org.cyclops.cyclopscore.config.extendedconfig.ExtendedConfig;
import org.cyclops.cyclopscore.datastructure.Wrapper;
import org.cyclops.cyclopscore.helper.*;

import javax.annotation.Nullable;

/**
 * A machine that can infuse stuff with blood.
 *
 * @author rubensworks
 */
public class ColossalChest extends ConfigurableBlockContainerGui implements CubeDetector.IDetectionListener {

    @BlockProperty
    public static final PropertyBool ACTIVE = PropertyBool.create("active");
    @BlockProperty
    public static final PropertyMaterial MATERIAL = PropertyMaterial.create("material", PropertyMaterial.Type.class);

    private static ColossalChest _instance = null;

    /**
     * Get the unique instance.
     *
     * @return The instance.
     */
    public static ColossalChest getInstance() {
        return _instance;
    }

    public ColossalChest(ExtendedConfig<BlockConfig> eConfig) {
        super(eConfig, Material.ROCK, TileColossalChest.class);
        this.setHardness(5.0F);
        this.setSoundType(SoundType.WOOD);
        this.setHarvestLevel("axe", 0); // Wood tier
        this.setRotatable(false);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean getUseNeighborBrightness(IBlockState state) {
        return true;
    }

    public static boolean isToolEffectiveShared(String type, IBlockState state) {
        if(PropertyMaterial.Type.WOOD == state.getValue(MATERIAL)) {
            return "axe".equals(type);
        }
        return "pickaxe".equals(type);
    }

    public static boolean canPlace(World world, BlockPos pos) {
        for(EnumFacing side : EnumFacing.VALUES) {
            IBlockState blockState = world.getBlockState(pos.offset(side));
            if(blockState.getProperties().containsKey(ACTIVE) && blockState.getValue(ACTIVE)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isToolEffective(String type, IBlockState state) {
        return isToolEffectiveShared(type, state);
    }

    @SuppressWarnings("deprecation")
    @SideOnly(Side.CLIENT)
    @Override
    public boolean isOpaqueCube(IBlockState blockState) {
        return false;
    }

    @SuppressWarnings("deprecation")
    @SideOnly(Side.CLIENT)
    @Override
    public boolean isFullCube(IBlockState blockState) {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getBlockLayer() {
        return BlockRenderLayer.CUTOUT_MIPPED;
    }

    public static DetectionResult triggerDetector(World world, BlockPos blockPos, boolean valid, @Nullable EntityPlayer player) {
        DetectionResult detectionResult = TileColossalChest.detector.detect(world, blockPos, valid ? null : blockPos, new MaterialValidationAction(), true);
        if (player instanceof EntityPlayerMP && detectionResult.getError() == null) {
            IBlockState blockState = world.getBlockState(blockPos);
            if (blockState.getValue(ACTIVE)) {
                PropertyMaterial.Type material = blockState.getValue(MATERIAL);

                TileColossalChest tile = TileHelpers.getSafeTile(world, blockPos, TileColossalChest.class);
                if (tile == null) {
                    BlockPos corePos = getCoreLocation(world, blockPos);
                    tile = TileHelpers.getSafeTile(world, corePos, TileColossalChest.class);
                }

                Advancements.CHEST_FORMED.trigger((EntityPlayerMP) player,
                        Pair.of(material, tile.getSizeSingular()));
            }
        }
        return detectionResult;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (stack.hasDisplayName()) {
            TileColossalChest tile = TileHelpers.getSafeTile(world, pos, TileColossalChest.class);
            if (tile != null) {
                tile.setCustomName(stack.getDisplayName());
                tile.setSize(Vec3i.NULL_VECTOR);
            }
        }
        triggerDetector(world, pos, true, placer instanceof EntityPlayer ? (EntityPlayer) placer : null);
    }

    @Override
    public void onBlockAdded(World world, BlockPos blockPos, IBlockState blockState) {
        super.onBlockAdded(world, blockPos, blockState);
        triggerDetector(world, blockPos, true, null);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if((Boolean)state.getValue(ACTIVE)) triggerDetector(world, pos, false, null);
        super.breakBlock(world, pos, state);
    }

    @Override
    public void onDetect(World world, BlockPos location, Vec3i size, boolean valid, BlockPos originCorner) {
        Block block = world.getBlockState(location).getBlock();
        if(block == this) {
            world.setBlockState(location, world.getBlockState(location).withProperty(ACTIVE, valid), MinecraftHelpers.BLOCK_NOTIFY_CLIENT);
            TileColossalChest tile = TileHelpers.getSafeTile(world, location, TileColossalChest.class);
            if(tile != null) {
                tile.setMaterial(BlockHelpers.getSafeBlockStateProperty(
                        world.getBlockState(location), ColossalChest.MATERIAL, PropertyMaterial.Type.WOOD));
                tile.setSize(valid ? size : Vec3i.NULL_VECTOR);
                tile.setCenter(new Vec3d(
                        originCorner.getX() + ((double) size.getX()) / 2,
                        originCorner.getY() + ((double) size.getY()) / 2,
                        originCorner.getZ() + ((double) size.getZ()) / 2
                ));
                tile.addInterface(location);
            }
        }
    }

    @Override
    public Class<? extends Container> getContainer() {
        return ContainerColossalChest.class;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Class<? extends GuiScreen> getGui() {
        return GuiColossalChest.class;
    }

    /**
     * Get the core block location.
     * @param world The world.
     * @param blockPos The start position to search from.
     * @return The found location.
     */
    public static @Nullable BlockPos getCoreLocation(World world, BlockPos blockPos) {
        final Wrapper<BlockPos> tileLocationWrapper = new Wrapper<BlockPos>();
        TileColossalChest.detector.detect(world, blockPos, null, new CubeDetector.IValidationAction() {

            @Override
            public L10NHelpers.UnlocalizedString onValidate(BlockPos location, IBlockState blockState) {
                if (blockState.getBlock() == ColossalChest.getInstance()) {
                    tileLocationWrapper.set(location);
                }
                return null;
            }

        }, false);
        return tileLocationWrapper.get();
    }

    /**
     * Show the structure forming error in the given player chat window.
     * @param world The world.
     * @param blockPos The start position.
     * @param player The player.
     * @param hand The used hand.
     */
    public static void addPlayerChatError(World world, BlockPos blockPos, EntityPlayer player, EnumHand hand) {
        if(!world.isRemote && player.getHeldItem(hand).isEmpty()) {
            DetectionResult result = TileColossalChest.detector.detect(world, blockPos, null,  new MaterialValidationAction(), false);
            if (result != null && result.getError() != null) {
                addPlayerChatError(player, result.getError());
            } else {
                player.sendMessage(new TextComponentString(L10NHelpers.localize(
                        "multiblock.colossalchests.error.unexpected")));
            }
        }
    }

    public static void addPlayerChatError(EntityPlayer player, L10NHelpers.UnlocalizedString unlocalizedError) {
        ITextComponent chat = new TextComponentString("");
        ITextComponent prefix = new TextComponentString(
                String.format("[%s]: ", L10NHelpers.localize("multiblock.colossalchests.error.prefix"))
        ).setStyle(new Style().
                        setColor(TextFormatting.GRAY).
                        setHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                new TextComponentTranslation("multiblock.colossalchests.error.prefix.info")
                        ))
        );
        ITextComponent error = new TextComponentString(unlocalizedError.localize());
        chat.appendSibling(prefix);
        chat.appendSibling(error);
        player.sendMessage(chat);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void getSubBlocks(CreativeTabs creativeTabs, NonNullList<ItemStack> list) {
        if (!BlockHelpers.isValidCreativeTab(this, creativeTabs)) return;
        for(PropertyMaterial.Type material : PropertyMaterial.Type.values()) {
            list.add(new ItemStack(getInstance(), 1, material.ordinal()));
        }
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return (propertyManager = new BlockPropertyManagerComponent(this,
                new BlockPropertyManagerComponent.PropertyComparator() {
                    @Override
                    public int compare(IProperty o1, IProperty o2) {
                        return o2.getName().compareTo(o1.getName());
                    }
                },
                new BlockPropertyManagerComponent.UnlistedPropertyComparator())).createDelegatedBlockState();
    }

    @Override
    public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        // Meta * 2 because we always want the inactive state
        return super.getStateForPlacement(worldIn, pos, facing, hitX, hitY, hitZ, meta * 2, placer, hand);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos blockPos, IBlockState blockState, EntityPlayer player, EnumHand hand, EnumFacing side, float par7, float par8, float par9) {
        if(!(blockState.getValue(ACTIVE))) {
            ColossalChest.addPlayerChatError(world, blockPos, player, hand);
            return false;
        }
        return super.onBlockActivated(world, blockPos, blockState, player, hand, side, par7, par8, par9);
    }

    @Override
    public int damageDropped(IBlockState state) {
        return state.getValue(ColossalChest.MATERIAL).ordinal();
    }

    @Override
    public boolean isKeepNBTOnDrop() {
        return false;
    }

    @Override
    public boolean canPlaceBlockAt(World worldIn, BlockPos pos) {
        return super.canPlaceBlockAt(worldIn, pos) && canPlace(worldIn, pos);
    }

    @Override
    public boolean canSilkHarvest(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        return false;
    }

    @Override
    public float getExplosionResistance(World world, BlockPos pos, Entity exploder, Explosion explosion) {
        if (world.getBlockState(pos).getValue(ColossalChest.MATERIAL).isExplosionResistant()) {
            return 10000F;
        }
        return super.getExplosionResistance(world, pos, exploder, explosion);
    }

    private static class MaterialValidationAction implements CubeDetector.IValidationAction {
        private final Wrapper<PropertyMaterial.Type> requiredMaterial;

        public MaterialValidationAction() {
            this.requiredMaterial = new Wrapper<PropertyMaterial.Type>(null);
        }

        @Override
        public L10NHelpers.UnlocalizedString onValidate(BlockPos blockPos, IBlockState blockState) {
            PropertyMaterial.Type material = BlockHelpers.
                    getSafeBlockStateProperty(blockState, ColossalChest.MATERIAL, null);
            if(requiredMaterial.get() == null) {
                requiredMaterial.set(material);
                return null;
            }
            return requiredMaterial.get() == material ? null : new L10NHelpers.UnlocalizedString(
                    "multiblock.colossalchests.error.material", material.getLocalizedName(), LocationHelpers.toCompactString(blockPos),
                    requiredMaterial.get().getLocalizedName());
        }
    }
}
