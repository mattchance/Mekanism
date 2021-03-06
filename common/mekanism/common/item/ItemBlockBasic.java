package mekanism.common.item;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Icon;

/**
 * Item class for handling multiple metal block IDs.
 * 0: Osmium Block
 * 1: Bronze Block
 * 2: Refined Obsidian
 * 3: Coal Block
 * 4: Refined Glowstone
 * 5: Steel Block
 * 6: OPEN
 * 7: Teleporter Frame
 * 8: Steel Casing
 * 9: Dynamic Tank
 * 10: Dynamic Glass
 * 11: Dynamic Valve
 * @author AidanBrady
 *
 */
public class ItemBlockBasic extends ItemBlock
{
	public Block metaBlock;
	
	public ItemBlockBasic(int id, Block block)
	{
		super(id);
		metaBlock = block;
		setHasSubtypes(true);
	}
	
	@Override
	public int getMetadata(int i)
	{
		return i;
	}
	
	@Override
	public Icon getIconFromDamage(int i)
	{
		return metaBlock.getIcon(2, i);
	}
	
	@Override
	public String getUnlocalizedName(ItemStack itemstack)
	{
		String name = "";
		switch(itemstack.getItemDamage())
		{
			case 0:
				name = "OsmiumBlock";
				break;
			case 1:
				name = "BronzeBlock";
				break;
			case 2:
				name = "RefinedObsidian";
				break;
			case 3:
				name = "CoalBlock";
				break;
			case 4:
				name = "RefinedGlowstone";
				break;
			case 5:
				name = "SteelBlock";
				break;
			case 6:
				name = "empty";//OPEN
				break;
			case 7:
				name = "TeleporterFrame";
				break;
			case 8:
				name = "SteelCasing";
				break;
			case 9:
				name = "DynamicTank";
				break;
			case 10:
				name = "DynamicGlass";
				break;
			case 11:
				name = "DynamicValve";
				break;
			default:
				name = "Unknown";
				break;
		}
		return getUnlocalizedName() + "." + name;
	}
}
