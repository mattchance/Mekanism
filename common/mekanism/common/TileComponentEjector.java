package mekanism.common;

import java.util.ArrayList;
import java.util.List;

import mekanism.api.EnumColor;
import mekanism.api.IConfigurable;
import mekanism.api.IEjector;
import mekanism.api.Object3D;
import mekanism.api.SideData;
import mekanism.common.tileentity.TileEntityContainerBlock;
import mekanism.common.tileentity.TileEntityLogisticalTransporter;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TransporterUtils;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.ForgeDirection;

import com.google.common.io.ByteArrayDataInput;

public class TileComponentEjector implements ITileComponent, IEjector
{
	public TileEntityContainerBlock tileEntity;
	
	public boolean ejecting;
	
	public EnumColor ejectColor;
	
	public SideData sideData;
	
	public int[] trackers;
	
	public TileComponentEjector(TileEntityContainerBlock tile, SideData data)
	{
		tileEntity = tile;
		sideData = data;
		trackers = new int[sideData.availableSlots.length];
		
		tile.components.add(this);
	}
	
	private List<ForgeDirection> getTrackedOutputs(int index, List<ForgeDirection> dirs)
	{
		List<ForgeDirection> sides = new ArrayList<ForgeDirection>();
		
		for(int i = trackers[index]+1; i <= trackers[index]+6; i++)
		{
			for(ForgeDirection side : dirs)
			{
				if(ForgeDirection.getOrientation(i%6) == side)
				{
					sides.add(side);
				}
			}
		}
		
		return sides;
	}
	
	@Override
	public void onOutput()
	{
		if(!ejecting || tileEntity.worldObj.isRemote)
		{
			return;
		}
		
		List<ForgeDirection> outputSides = new ArrayList<ForgeDirection>();
		
		IConfigurable configurable = (IConfigurable)tileEntity;
		
		for(int i = 0; i < configurable.getConfiguration().length; i++)
		{
			if(configurable.getConfiguration()[i] == configurable.getSideData().indexOf(sideData))
			{
				outputSides.add(ForgeDirection.getOrientation(MekanismUtils.getBaseOrientation(i, tileEntity.facing)));
			}
		}
		
		for(int index = 0; index < sideData.availableSlots.length; index++)
		{
			int slotID = sideData.availableSlots[index];
			
			if(tileEntity.inventory[slotID] == null)
			{
				continue;
			}
			
			ItemStack stack = tileEntity.inventory[slotID];
			List<ForgeDirection> outputs = getTrackedOutputs(index, outputSides);
			
			for(ForgeDirection side : outputs)
			{
				TileEntity tile = Object3D.get(tileEntity).getFromSide(side).getTileEntity(tileEntity.worldObj);
				ItemStack prev = stack.copy();
				
				if(tile instanceof IInventory && !(tile instanceof TileEntityLogisticalTransporter))
				{
					stack = TransporterUtils.putStackInInventory((IInventory)tile, stack, side.ordinal());
				}
				else if(tile instanceof TileEntityLogisticalTransporter)
				{
					if(TransporterUtils.insert(tileEntity, (TileEntityLogisticalTransporter)tile, stack, ejectColor))
					{
						stack = null;
					}
				}
				
				if(stack == null || prev.stackSize != stack.stackSize)
				{
					trackers[index] = side.ordinal();
				}
				
				if(stack == null)
				{
					break;
				}
			}
			
			tileEntity.inventory[slotID] = stack;
		}
	}
	
	@Override
	public boolean isEjecting()
	{
		return ejecting;
	}
	
	@Override
	public void setEjecting(boolean eject)
	{
		ejecting = eject;
	}
	
	@Override
	public void setColor(EnumColor color)
	{
		ejectColor = color;
	}
	
	@Override
	public EnumColor getColor()
	{
		return ejectColor;
	}

	@Override
	public void tick() {}

	@Override
	public void read(NBTTagCompound nbtTags) 
	{
		ejecting = nbtTags.getBoolean("ejecting");
		
		if(nbtTags.hasKey("ejectColor"))
		{
			ejectColor = TransporterUtils.colors.get(nbtTags.getInteger("ejectColor"));
		}
		
		for(int i = 0; i < sideData.availableSlots.length; i++)
		{
			trackers[i] = nbtTags.getInteger("tracker" + i);
		}
	}

	@Override
	public void read(ByteArrayDataInput dataStream) 
	{
		ejecting = dataStream.readBoolean();
		
		int c = dataStream.readInt();
		
		if(c != -1)
		{
			ejectColor = TransporterUtils.colors.get(c);
		}
		else {
			ejectColor = null;
		}
	}

	@Override
	public void write(NBTTagCompound nbtTags) 
	{
		nbtTags.setBoolean("ejecting", ejecting);
		
		if(ejectColor != null)
		{
			nbtTags.setInteger("ejectColor", TransporterUtils.colors.indexOf(ejectColor));
		}
		
		for(int i = 0; i < sideData.availableSlots.length; i++)
		{
			nbtTags.setInteger("tracker" + i, trackers[i]);
		}
	}
	
	@Override
	public void write(ArrayList data) 
	{
		data.add(ejecting);
		
		if(ejectColor != null)
		{
			data.add(TransporterUtils.colors.indexOf(ejectColor));
		}
		else {
			data.add(-1);
		}
	}
}
