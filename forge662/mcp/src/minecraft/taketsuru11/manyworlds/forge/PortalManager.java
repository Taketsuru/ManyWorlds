package taketsuru11.manyworlds.forge;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;

import cpw.mods.fml.common.FMLLog;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Tuple;
import net.minecraft.world.storage.SaveHandler;
import net.minecraft.world.Teleporter;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.world.WorldEvent;

public class PortalManager {

	private final static int portalFrameSize = 4;
	private final static int portalCoordinatesFudgeFactor = 4;
	private final static int verticalSpace = 2;

	class Portal {
		WorldServer worldServer;
		ChunkCoordinates northWestCorner;
		int targetDimension;

		public Portal(WorldServer worldServer, ChunkCoordinates northWestCorner, int sourceDimension, int targetDimension) {
			this.worldServer = worldServer;
			this.northWestCorner = northWestCorner;
			this.targetDimension = targetDimension;
		}

		public void teleportPlayerTo(EntityPlayerMP player) {
			ManyWorldsTeleporter.teleport(player, worldServer.provider.dimensionId,
					northWestCorner.posX + 0.5, northWestCorner.posY + 1, northWestCorner.posZ + 0.5,
					player.rotationYaw, player.rotationPitch);
		}
	};

	private Map<WorldServer, Set<Portal>> portals = new HashMap<WorldServer, Set<Portal>>();

	/**
	 * 
	 * @param worldServer
	 * @param x x of a water block
	 * @param y y of a water block
	 * @param z z of a water block
	 * @return BlockCoordinates of the north west corner of a portal blocks if there are such blocks.  Otherwise null.
	 */
	public static ChunkCoordinates findPortalShape(WorldServer worldServer, int x, int y, int z) {
		int surroundingBlockID = worldServer.getBlockId(x, y, z);
		if (surroundingBlockID != Block.waterStill.blockID) {
			return null;
		}

		// Is the pool of water a 2x2 rectangle of still water blocks?
		int directions[][] = {
				{  0, -1 },  // north
				{  1,  0 },  // east
				{  0,  1 },  // south
				{ -1,  0 }   // west
		};
		int offsets[][] = { // offsets + item coordinate = coordinate of the north west corner of a gate candidate
				{ -1, -2 }, // south west corner
				{ -1, -1 }, // north west corner
				{ -2, -1 }, // north east corner
				{ -2, -2 }  // south east corner
		};
		int corner = -1;
		for (int i = 0; i < 4; ++i) {
			int ni = i == 3 ? 0 : i + 1;
			int id1 = worldServer.getBlockId(x + directions[i][0], y, z + directions[i][1]);
			int id2 = worldServer.getBlockId(x + directions[ni][0], y, z + directions[ni][1]);
			int id3 = worldServer.getBlockId(x + directions[i][0] + directions[ni][0], y, z + directions[i][1] + + directions[ni][1]);
			if (id1 != Block.waterStill.blockID || id2 != Block.waterStill.blockID || id3 != Block.waterStill.blockID) {
				continue;
			}
			if (corner != -1) {
				return null;
			}
			corner = i;
		}
		if (corner == -1) { 
			return null;
		}

		int portalX = x + offsets[corner][0];
		int portalZ = z + offsets[corner][1];

		// Is the pool of water is surrounded by obsidian blocks?
		for (int i = 0; i < 4; ++i) {
			if (worldServer.getBlockId(portalX + i, y, portalZ) != Block.obsidian.blockID
				|| worldServer.getBlockId(portalX + i, y, portalZ + 3) != Block.obsidian.blockID
				|| worldServer.getBlockId(portalX, y, portalZ + i) != Block.obsidian.blockID
				|| worldServer.getBlockId(portalX + 3, y, portalZ + i) != Block.obsidian.blockID) {
				return null;
			}
		}
	
		return new ChunkCoordinates(portalX, y, portalZ);
	}

	public static boolean isNearerThan(ChunkCoordinates p1, ChunkCoordinates p2, int threshold) {
		return p1.posX - threshold < p2.posX
			&& p2.posX < p1.posX + threshold
			&& p1.posZ - threshold < p2.posZ
			&& p2.posZ < p1.posZ + threshold;
	}
	
	public static float getDistanceSquared(Portal x, Portal y) {
		return x.northWestCorner.getDistanceSquaredToChunkCoordinates(y.northWestCorner);	
	}
	
	public static int getDimension(WorldServer world) {
		Integer[] IDs = DimensionManager.getIDs();
		for (int dimension : IDs) {
			if (DimensionManager.getWorld(dimension) == world) {
				return dimension;
			}
		}

		throw new RuntimeException("unknown world");
	}

	@ForgeSubscribe
	public void onWorldLoad(WorldEvent.Load event) {
		if (event.world.isRemote) {
			return;
		}

		WorldServer world = (WorldServer)event.world;

		portals.remove(world);

		File root = new File(world.getChunkSaveLocation(), "ManyWords");
		if (! root.exists()) {
			return;
		}

		File portalFile = new File(root, "portals");
		if (! portalFile.exists()) {
			return;
		}

		int dimension = getDimension(world);
		Set<Portal> portalsInTheWorld = new HashSet<Portal>();
		try {
			DataInputStream stream = new DataInputStream(new FileInputStream(portalFile));
			try {
				for (;;) {
					int x = stream.readInt();
					int y = stream.readInt();
					int z = stream.readInt();
					int target = stream.readInt();
					Portal portal = new Portal(world, new ChunkCoordinates(x, y, z), dimension, target);
					if (isValidPortal(portal)) {
						portalsInTheWorld.add(portal);
						FMLLog.info("%d,%d,%d in dim%d -> dim%d", x, y, z, dimension, target);
					}
				}
			} catch (EOFException eof) {
			}
			stream.close();
		} catch (IOException ioe) {
		}
		
		if (! portalsInTheWorld.isEmpty()) {
			portals.put(world, portalsInTheWorld);
		}
	}

	@ForgeSubscribe
	public void onWorldSave(WorldEvent.Save event) {
		if (event.world.isRemote) {
			return;
		}

		WorldServer world = (WorldServer)event.world;

		File root = new File(world.getChunkSaveLocation(), "ManyWords");
		if (! root.exists() && ! root.mkdirs()) {
			FMLLog.severe("Can't create %s", root.getAbsolutePath());
			return;
		}

		Set<Portal> portalsInTheWorld = portals.get(world);

		File portalFile = new File(root, "portals");

		try {
			if (portalsInTheWorld == null) {
				portalFile.delete();
			} else {
				File portalTmpFile = new File(root, "portals.tmp");
				portalTmpFile.delete();
				portalTmpFile.createNewFile();

				DataOutputStream stream = new DataOutputStream(new FileOutputStream(portalTmpFile));
				for (Portal p : portalsInTheWorld) {
					stream.writeInt(p.northWestCorner.posX);
					stream.writeInt(p.northWestCorner.posY);
					stream.writeInt(p.northWestCorner.posZ);
					stream.writeInt(p.targetDimension);
				}
				stream.flush();
				stream.close();
				
				portalFile.delete();
				portalTmpFile.renameTo(portalFile);
			}
		} catch (IOException ioe) {
		}
	}

	@ForgeSubscribe
	public void onWorldUnload(WorldEvent.Unload event) {
		if (event.world.isRemote) {
			return;
		}

		WorldServer world = (WorldServer)event.world;

		portals.remove(world);
	}

	public boolean doesPortalExistAt(WorldServer world, ChunkCoordinates portalCoordinates) {
		Set<Portal> portalsInTheWorld = portals.get(world);
		if (portalsInTheWorld == null) {
			return false;
		}
		for (Portal p : portalsInTheWorld) {
			if (p.northWestCorner.equals(portalCoordinates)
				&& isValidPortal(p)) {
				return true;
			}
		}
		return false;
	}

	public void openPortal(WorldServer world, ChunkCoordinates portalCoordinates, int sourceDimension, int targetDimension) {
		assert(DimensionManager.getWorld(sourceDimension) == world);

		Set<Portal> portalsInTheWorld = portals.get(world);
		if (portalsInTheWorld == null) {
			portalsInTheWorld = new HashSet<Portal>();
			portals.put(world, portalsInTheWorld);
		}

		portalsInTheWorld.add(new Portal(world, portalCoordinates, sourceDimension, targetDimension));
	}

	public Portal getDestinationPortal(final Portal sourcePortal) {
		WorldServer sourceWorld = sourcePortal.worldServer;
		WorldServer destinationWorld = DimensionManager.getWorld(sourcePortal.targetDimension);
		if (destinationWorld == null) {
			return null;
		}

		Set<Portal> portalsInTheWorld = portals.get(destinationWorld);
		if (portalsInTheWorld == null) {
			return buildPortal(destinationWorld, sourcePortal.northWestCorner, sourcePortal.targetDimension, sourceWorld.provider.dimensionId);
		}

		Vector<Portal> candidates = new Vector<Portal>();
		for (Portal portal : portalsInTheWorld) {
			if (isNearerThan(sourcePortal.northWestCorner, portal.northWestCorner, portalCoordinatesFudgeFactor)
				&& isValidPortal(portal)) {
				candidates.add(portal);
			}
		}

		if (candidates.isEmpty()) {
			return buildPortal(destinationWorld, sourcePortal.northWestCorner, sourcePortal.targetDimension, sourceWorld.provider.dimensionId);
		}
		
		Collections.sort(candidates, new Comparator() {
			public int compare(Object x, Object y) {
				return (int)(getDistanceSquared((Portal)x, sourcePortal) - getDistanceSquared((Portal)y, sourcePortal));
			}
		});

		return candidates.firstElement();
	}

	public void onPlayerPostTick(EntityPlayerMP player) {
		int x = MathHelper.floor_double(player.posX);
		int y = MathHelper.floor_double(player.posY);
		int z = MathHelper.floor_double(player.posZ);

		WorldServer world = (WorldServer)player.worldObj;
		ChunkCoordinates position = findPortalShape(world, x, y, z);
		if (position == null) {
			return;
		}

		Set<Portal> portalsInTheWorld = portals.get(world);
		if (portalsInTheWorld == null) {
			return;
		}

		for (Portal p : portalsInTheWorld) {
			if (! p.northWestCorner.equals(position)
				|| ! isValidPortal(p)) {
				continue;
			}
			
			Portal destination = getDestinationPortal(p);
			if (destination != null) {
				destination.teleportPlayerTo(player);
			}

			return;
		}
	}
	
	public void onPostWorldTick(WorldServer world) {
		Set<Portal> portalsInTheWorld = portals.get(world);
		if (portalsInTheWorld == null) {
			return;
		}

		for (Portal p : portalsInTheWorld) {
			for (int i = 1; i < portalFrameSize; ++i) {
				for (int j = 1; j < portalFrameSize; ++j) {
					if (world.getBlockId(p.northWestCorner.posX + i,
							p.northWestCorner.posY,
							p.northWestCorner.posZ + j) == Block.ice.blockID) {
						world.setBlock(p.northWestCorner.posX + i,
								p.northWestCorner.posY,
								p.northWestCorner.posZ + j,
								Block.waterStill.blockID);
					}
				}
			}
		}
	}

	Portal buildPortal(WorldServer world, ChunkCoordinates position, int sourceDimension, int targetDimension) {

		int x = position.posX;
		int y = position.posY;
		int z = position.posZ;

		boolean found = false;
	placeSearchingLoop:
		for (int k = world.getActualHeight() - verticalSpace - 1; k > 0; --k) {
			for (int i = 0; i < portalCoordinatesFudgeFactor; ++i) {
				int j;
				for (j = -i; j <= i; ++j) {
					if (isPortalPlaceable(world, x + j, k, z - i)) {
						x = x + j;
						y = k;
						z = z - i;
						found = true;
						break placeSearchingLoop;
					}
					if (i != 0 && isPortalPlaceable(world, x + j, k, z + i)) {
						x = x + j;
						y = k;
						z = z + i;
						found = true;
						break placeSearchingLoop;					
					}
				}

				for (j = -i + 1; j <= i - 1; ++j) {
					if (isPortalPlaceable(world, x - i, k, z + j)) {
						x = x - i;
						y = k;
						z = z + j;
						found = true;
						break placeSearchingLoop;
					}
					if (i != 0 && isPortalPlaceable(world, x + i, k, z + j)) {
						x = x + i;
						y = k;
						z = z + j;
						found = true;
						break placeSearchingLoop;					
					}
				}
			}
		}

		// Failed to find.  Create a portal in the air.
		if (! found) {
			int maxY = world.getActualHeight() - 2;
			if (maxY < y) {
				y = maxY;
			}
		}

		placePortalBlocks(world, x, y, z);

		Portal result = new Portal(world, new ChunkCoordinates(x, y, z), sourceDimension, targetDimension);

		Set<Portal> portalsInTheWorld = portals.get(world);
		if (portalsInTheWorld == null) {
			portalsInTheWorld = new HashSet<Portal>();
			portals.put(world,  portalsInTheWorld);
		}
		portalsInTheWorld.add(result);

		return result;
	}
	
	void placePortalBlocks(World world, int x, int y, int z) {
		assert(1 <= y && y < world.getActualHeight() - verticalSpace);
		for (int i = 0; i < portalFrameSize; ++i) {
			for (int j = 0; j < portalFrameSize; ++j) {
				boolean isEdge = i == 0 || i == portalFrameSize - 1 || j == 0 || j == portalFrameSize - 1;
				world.setBlock(x + i, y, z + j, isEdge ? Block.obsidian.blockID : Block.waterStill.blockID);
				if (! isEdge && ! world.getBlockMaterial(x + i, y - 1, z + j).isSolid()) {
					world.setBlock(x + i, y - 1, z + j, Block.stone.blockID);
				}
				for (int k = 1; k <= verticalSpace; ++k) {
					world.setBlock(x + i, y + k, z + j, 0);					
				}
			}
		}		
	}

	boolean isValidPortal(Portal portal) {
		ChunkCoordinates pos = portal.northWestCorner;
		return findPortalShape(portal.worldServer, pos.posX + 1, pos.posY, pos.posZ + 1) != null;
	}

	boolean isPortalPlaceable(World world, int x, int y, int z) {
		if (y < 1 || world.getActualHeight() - verticalSpace <= y) {
			return false;
		}

		for (int i = 1; i < portalFrameSize - 1; ++i) {
			for (int j = 1; j < portalFrameSize - 1; ++j) {
				if (! world.getBlockMaterial(x + i, y - 1, z + j).isSolid()) {
					return false;
				}
			}
		}

		for (int i = 0; i < portalFrameSize; ++i) {
			for (int j = 0; j < portalFrameSize; ++j) {
				for (int k = 1; k <= verticalSpace; ++k) {
					if (world.getBlockId(x + i, y + k, z + j) != 0) {
						return false;
					}
				}
			}
		}

		return true;
	}

}
