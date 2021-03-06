package mekanism.generators.common.tileentity;

import java.util.ArrayList;

import mekanism.api.IStorageTank;
import mekanism.api.gas.EnumGas;
import mekanism.api.gas.IGasAcceptor;
import mekanism.api.gas.IGasStorage;
import mekanism.api.gas.ITubeConnection;
import mekanism.common.util.ChargeUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.generators.common.MekanismGenerators;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.ForgeDirection;

import com.google.common.io.ByteArrayDataInput;

import dan200.computer.api.IComputerAccess;
import dan200.computer.api.ILuaContext;

public class TileEntityHydrogenGenerator extends TileEntityGenerator implements IGasAcceptor, IGasStorage, ITubeConnection
{
	/** The maximum amount of hydrogen this block can store. */
	public int MAX_HYDROGEN = 18000;
	
	/** The amount of hydrogen this block is storing. */
	public int hydrogenStored;
	
	public TileEntityHydrogenGenerator()
	{
		super("Hydrogen Generator", 40000, MekanismGenerators.hydrogenGeneration*2);
		inventory = new ItemStack[2];
	}
	
	@Override
	public void onUpdate()
	{
		super.onUpdate();
		
		if(!worldObj.isRemote)
		{
			ChargeUtils.charge(1, this);
			
			if(inventory[0] != null && hydrogenStored < MAX_HYDROGEN)
			{
				if(inventory[0].getItem() instanceof IStorageTank)
				{
					IStorageTank item = (IStorageTank)inventory[0].getItem();
					
					if(item.canProvideGas(inventory[0], EnumGas.HYDROGEN) && item.getGasType(inventory[0]) == EnumGas.HYDROGEN)
					{
						int received = 0;
						int hydrogenNeeded = MAX_HYDROGEN - hydrogenStored;
						
						if(item.getRate() <= hydrogenNeeded)
						{
							received = item.removeGas(inventory[0], EnumGas.HYDROGEN, item.getRate());
						}
						else if(item.getRate() > hydrogenNeeded)
						{
							received = item.removeGas(inventory[0], EnumGas.HYDROGEN, hydrogenNeeded);
						}
						
						setGas(EnumGas.HYDROGEN, hydrogenStored + received);
					}
				}
			}
			
			if(canOperate())
			{
				setActive(true);
				
				hydrogenStored-=2;
				setEnergy(electricityStored + MekanismGenerators.hydrogenGeneration);
			}
			else {
				setActive(false);
			}
		}
	}
	
	@Override
	public boolean canExtractItem(int slotID, ItemStack itemstack, int side)
	{
		if(slotID == 1)
		{
			return ChargeUtils.canBeOutputted(itemstack, true);
		}
		else if(slotID == 0)
		{
			return (itemstack.getItem() instanceof IStorageTank && ((IStorageTank)itemstack.getItem()).getGas(EnumGas.NONE, itemstack) == 0);
		}
		
		return false;
	}
	
	@Override
	public boolean isItemValidForSlot(int slotID, ItemStack itemstack)
	{
		if(slotID == 0)
		{
			return itemstack.getItem() instanceof IStorageTank && ((IStorageTank)itemstack.getItem()).getGasType(itemstack) == EnumGas.HYDROGEN;
		}
		else if(slotID == 1)
		{
			return ChargeUtils.canBeCharged(itemstack);
		}
		
		return true;
	}
	
	@Override
	public int[] getAccessibleSlotsFromSide(int side)
	{
		return ForgeDirection.getOrientation(side) == MekanismUtils.getRight(facing) ? new int[] {1} : new int[] {0};
	}
    
    @Override
	public void setGas(EnumGas type, int amount, Object... data)
	{
		if(type == EnumGas.HYDROGEN)
		{
			hydrogenStored = Math.max(Math.min(amount, MAX_HYDROGEN), 0);
		}
	}
    
	@Override
	public int getGas(EnumGas type, Object... data)
	{
		if(type == EnumGas.HYDROGEN)
		{
			return hydrogenStored;
		}
		
		return 0;
	}
	
	@Override
	public boolean canOperate()
	{
		return electricityStored < MAX_ELECTRICITY && hydrogenStored-2 >= 0 && MekanismUtils.canFunction(this);
	}
	
	/**
	 * Gets the scaled hydrogen level for the GUI.
	 * @param i - multiplier
	 * @return
	 */
	public int getScaledHydrogenLevel(int i)
	{
		return hydrogenStored*i / MAX_HYDROGEN;
	}

	@Override
	public String[] getMethodNames() 
	{
		return new String[] {"getStored", "getOutput", "getMaxEnergy", "getEnergyNeeded", "getHydrogen", "getHydrogenNeeded"};
	}

	@Override
	public Object[] callMethod(IComputerAccess computer, ILuaContext context, int method, Object[] arguments) throws Exception 
	{
		switch(method)
		{
			case 0:
				return new Object[] {electricityStored};
			case 1:
				return new Object[] {output};
			case 2:
				return new Object[] {MAX_ELECTRICITY};
			case 3:
				return new Object[] {(MAX_ELECTRICITY-electricityStored)};
			case 4:
				return new Object[] {hydrogenStored};
			case 5:
				return new Object[] {MAX_HYDROGEN-hydrogenStored};
			default:
				System.err.println("[Mekanism] Attempted to call unknown method with computer ID " + computer.getID());
				return null;
		}
	}

	@Override
	public void handlePacketData(ByteArrayDataInput dataStream)
	{
		super.handlePacketData(dataStream);
		hydrogenStored = dataStream.readInt();
	}
	
	@Override
	public ArrayList getNetworkedData(ArrayList data)
	{
		super.getNetworkedData(data);
		data.add(hydrogenStored);
		return data;
	}
	
	@Override
	public float getVoltage()
	{
		return 240;
	}

	@Override
	public int transferGasToAcceptor(int amount, EnumGas type)
	{
		if(type == EnumGas.HYDROGEN)
		{
	    	int rejects = 0;
	    	int neededHydrogen = MAX_HYDROGEN-hydrogenStored;
	    	if(amount <= neededHydrogen)
	    	{
	    		hydrogenStored += amount;
	    	}
	    	else if(amount > neededHydrogen)
	    	{
	    		hydrogenStored += neededHydrogen;
	    		rejects = amount-neededHydrogen;
	    	}
	    	
	    	return rejects;
		}
		
		return amount;
	}
	
	@Override
    public void readFromNBT(NBTTagCompound nbtTags)
    {
        super.readFromNBT(nbtTags);

        hydrogenStored = nbtTags.getInteger("hydrogenStored");
    }

	@Override
    public void writeToNBT(NBTTagCompound nbtTags)
    {
        super.writeToNBT(nbtTags);
        
        nbtTags.setInteger("hydrogenStored", hydrogenStored);
    }

	@Override
	public boolean canReceiveGas(ForgeDirection side, EnumGas type) 
	{
		return type == EnumGas.HYDROGEN && side != ForgeDirection.getOrientation(facing);
	}

	@Override
	public boolean canTubeConnect(ForgeDirection side) 
	{
		return side != ForgeDirection.getOrientation(facing);
	}

	@Override
	public int getMaxGas(EnumGas type, Object... data) 
	{
		if(type == EnumGas.HYDROGEN)
		{
			return MAX_HYDROGEN;
		}
		
		return 0;
	}
}
