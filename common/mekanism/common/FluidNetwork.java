package mekanism.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import mekanism.api.DynamicNetwork;
import mekanism.api.ITransmitter;
import mekanism.api.Object3D;
import mekanism.api.TransmissionType;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.Event;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;
import cpw.mods.fml.common.FMLCommonHandler;

public class FluidNetwork extends DynamicNetwork<IFluidHandler, FluidNetwork>
{
	public FluidNetwork(ITransmitter<FluidNetwork>... varPipes)
	{
		transmitters.addAll(Arrays.asList(varPipes));
		register();
	}
	
	public FluidNetwork(Collection<ITransmitter<FluidNetwork>> collection)
	{
		transmitters.addAll(collection);
		register();
	}
	
	public FluidNetwork(Set<FluidNetwork> networks)
	{
		for(FluidNetwork net : networks)
		{
			if(net != null)
			{
				addAllTransmitters(net.transmitters);
				net.deregister();
			}
		}
		
		refresh();
		register();
	}
	
	public int emit(FluidStack fluidToSend, boolean doTransfer, TileEntity emitter)
	{
		List availableAcceptors = Arrays.asList(getAcceptors(fluidToSend).toArray());
		
		Collections.shuffle(availableAcceptors);
		
		int fluidSent = 0;
		
		if(!availableAcceptors.isEmpty())
		{
			int divider = availableAcceptors.size();
			int remaining = fluidToSend.amount % divider;
			int sending = (fluidToSend.amount-remaining)/divider;
			
			for(Object obj : availableAcceptors)
			{
				if(obj instanceof IFluidHandler && obj != emitter)
				{
					IFluidHandler acceptor = (IFluidHandler)obj;
					int currentSending = sending;
					
					if(remaining > 0)
					{
						currentSending++;
						remaining--;
					}
					
					fluidSent += acceptor.fill(acceptorDirections.get(acceptor), new FluidStack(fluidToSend.fluidID, currentSending), doTransfer);
				}
			}
		}
		
		if(doTransfer && fluidSent > 0 && FMLCommonHandler.instance().getEffectiveSide().isServer())
		{
			FluidStack sendStack = fluidToSend.copy();
			sendStack.amount = fluidSent;
			MinecraftForge.EVENT_BUS.post(new FluidTransferEvent(this, sendStack));
		}
		
		return fluidSent;
	}
	
	@Override
	public Set<IFluidHandler> getAcceptors(Object... data)
	{
		FluidStack fluidToSend = (FluidStack)data[0];
		Set<IFluidHandler> toReturn = new HashSet<IFluidHandler>();
		
		for(IFluidHandler acceptor : possibleAcceptors)
		{
			if(acceptor.canFill(acceptorDirections.get(acceptor).getOpposite(), fluidToSend.getFluid()))
			{
				toReturn.add(acceptor);
			}
		}
		
		return toReturn;
	}
 
	@Override
	public void refresh()
	{
		Set<ITransmitter<FluidNetwork>> iterPipes = (Set<ITransmitter<FluidNetwork>>)transmitters.clone();
		Iterator it = iterPipes.iterator();
		
		possibleAcceptors.clear();
		acceptorDirections.clear();

		while(it.hasNext())
		{
			ITransmitter<FluidNetwork> conductor = (ITransmitter<FluidNetwork>)it.next();

			if(conductor == null || ((TileEntity)conductor).isInvalid())
			{
				it.remove();
				transmitters.remove(conductor);
			}
			else {
				conductor.setNetwork(this);
			}
		}
		
		for(ITransmitter<FluidNetwork> pipe : iterPipes)
		{
			IFluidHandler[] acceptors = PipeUtils.getConnectedAcceptors((TileEntity)pipe);
		
			for(IFluidHandler acceptor : acceptors)
			{
				if(acceptor != null && !(acceptor instanceof ITransmitter))
				{
					possibleAcceptors.add(acceptor);
					acceptorDirections.put(acceptor, ForgeDirection.getOrientation(Arrays.asList(acceptors).indexOf(acceptor)));
				}
			}
		}
	}

	@Override
	public void merge(FluidNetwork network)
	{
		if(network != null && network != this)
		{
			Set<FluidNetwork> networks = new HashSet<FluidNetwork>();
			networks.add(this);
			networks.add(network);
			FluidNetwork newNetwork = new FluidNetwork(networks);
			newNetwork.refresh();
		}
	}

	@Override
	public void split(ITransmitter<FluidNetwork> splitPoint)
	{
		if(splitPoint instanceof TileEntity)
		{
			removeTransmitter(splitPoint);
			
			TileEntity[] connectedBlocks = new TileEntity[6];
			boolean[] dealtWith = {false, false, false, false, false, false};
			
			for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS)
			{
				TileEntity sideTile = Object3D.get((TileEntity)splitPoint).getFromSide(direction).getTileEntity(((TileEntity)splitPoint).worldObj);
				
				if(sideTile != null)
				{
					connectedBlocks[Arrays.asList(ForgeDirection.values()).indexOf(direction)] = sideTile;
				}
			}

			for(int countOne = 0; countOne < connectedBlocks.length; countOne++)
			{
				TileEntity connectedBlockA = connectedBlocks[countOne];

				if(MekanismUtils.checkTransmissionType(connectedBlockA, TransmissionType.FLUID) && !dealtWith[countOne])
				{
					NetworkFinder finder = new NetworkFinder(((TileEntity)splitPoint).worldObj, Object3D.get(connectedBlockA), Object3D.get((TileEntity)splitPoint));
					List<Object3D> partNetwork = finder.exploreNetwork();
					
					for(int countTwo = countOne + 1; countTwo < connectedBlocks.length; countTwo++)
					{
						TileEntity connectedBlockB = connectedBlocks[countTwo];
						
						if(MekanismUtils.checkTransmissionType(connectedBlockB, TransmissionType.FLUID) && !dealtWith[countTwo])
						{
							if(partNetwork.contains(Object3D.get(connectedBlockB)))
							{
								dealtWith[countTwo] = true;
							}
						}
					}
					
					Set<ITransmitter<FluidNetwork>> newNetPipes= new HashSet<ITransmitter<FluidNetwork>>();
					
					for(Object3D node : finder.iterated)
					{
						TileEntity nodeTile = node.getTileEntity(((TileEntity)splitPoint).worldObj);

						if(MekanismUtils.checkTransmissionType(nodeTile, TransmissionType.FLUID))
						{
							if(nodeTile != splitPoint)
							{
								newNetPipes.add((ITransmitter<FluidNetwork>)nodeTile);
							}
						}
					}
					
					FluidNetwork newNetwork = new FluidNetwork(newNetPipes);					
					newNetwork.refresh();
				}
			}
			
			deregister();
		}
	}
	
	@Override
	public void fixMessedUpNetwork(ITransmitter<FluidNetwork> pipe)
	{
		if(pipe instanceof TileEntity)
		{
			NetworkFinder finder = new NetworkFinder(((TileEntity)pipe).getWorldObj(), Object3D.get((TileEntity)pipe), null);
			List<Object3D> partNetwork = finder.exploreNetwork();
			Set<ITransmitter<FluidNetwork>> newPipes = new HashSet<ITransmitter<FluidNetwork>>();
			
			for(Object3D node : partNetwork)
			{
				TileEntity nodeTile = node.getTileEntity(((TileEntity)pipe).worldObj);

				if(MekanismUtils.checkTransmissionType(nodeTile, TransmissionType.FLUID))
				{
					((ITransmitter<FluidNetwork>)nodeTile).removeFromNetwork();
					newPipes.add((ITransmitter<FluidNetwork>)nodeTile);
				}
			}
			
			FluidNetwork newNetwork = new FluidNetwork(newPipes);
			newNetwork.refresh();
			newNetwork.fixed = true;
			deregister();
		}
	}
	
	public static class NetworkFinder
	{
		public World worldObj;
		public Object3D start;
		
		public List<Object3D> iterated = new ArrayList<Object3D>();
		public List<Object3D> toIgnore = new ArrayList<Object3D>();
		
		public NetworkFinder(World world, Object3D location, Object3D... ignore)
		{
			worldObj = world;
			start = location;
			
			if(ignore != null)
			{
				toIgnore = Arrays.asList(ignore);
			}
		}

		public void loopAll(Object3D location)
		{
			if(MekanismUtils.checkTransmissionType(location.getTileEntity(worldObj), TransmissionType.FLUID))
			{
				iterated.add(location);
			}
			
			for(ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS)
			{
				Object3D obj = location.getFromSide(direction);
				
				if(!iterated.contains(obj) && !toIgnore.contains(obj))
				{
					TileEntity tileEntity = obj.getTileEntity(worldObj);
					
					if(MekanismUtils.checkTransmissionType(tileEntity, TransmissionType.FLUID))
					{
						loopAll(obj);
					}
				}
			}
		}

		public List<Object3D> exploreNetwork()
		{
			loopAll(start);
			
			return iterated;
		}
	}
	
	public static class FluidTransferEvent extends Event
	{
		public final FluidNetwork fluidNetwork;
		
		public final FluidStack fluidSent;
		
		public FluidTransferEvent(FluidNetwork network, FluidStack fluid)
		{
			fluidNetwork = network;
			fluidSent = fluid;
		}
	}
		
	@Override
	public String toString()
	{
		return "[FluidNetwork] " + transmitters.size() + " transmitters, " + possibleAcceptors.size() + " acceptors.";
	}
}