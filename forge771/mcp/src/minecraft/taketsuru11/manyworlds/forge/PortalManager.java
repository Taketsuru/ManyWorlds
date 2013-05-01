package taketsuru11.manyworlds.forge;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Tuple;
import net.minecraft.world.Teleporter;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

public class PortalManager {

	private final static int portalFrameSize = 4;
	private final static int portalCoordinatesFudgeFactor = 8;
	private final static int verticalSpace = 2;

	class Portal {
		WorldServer worldServer;
		ChunkCoordinates northWestCorner;
		int sourceDimension;
		int targetDimension;

		public Portal(WorldServer worldServer, ChunkCoordinates northWestCorner, int sourceDimension, int targetDimension) {
			this.worldServer = worldServer;
			this.northWestCorner = northWestCorner;
			this.sourceDimension = sourceDimension;
			this.targetDimension = targetDimension;
		}

		public void teleportPlayerTo(EntityPlayerMP player) {
			ManyWorldsTeleporter.teleport(player, sourceDimension,
					northWestCorner.posX + 0.5, northWestCorner.posY + 1, northWestCorner.posZ + 0.5,
					player.rotationYaw, player.rotationPitch);
		}
	};

	class EntityPosition {
	
		World world;
		int x;
		int y;
		int z;

		public EntityPosition(World world, int x, int y, int z) {
			super();
			this.world = world;
			this.x = x;
			this.y = y;
			this.z = z;
		}

	};

	private Map<WorldServer, Set<Portal>> portals = new HashMap<WorldServer, Set<Portal>>();
	private Map<Entity, EntityPosition> lastPortalEntranceCheckPos = new HashMap<Entity, EntityPosition>();

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
	
	public boolean doesPortalExistAt(WorldServer world, ChunkCoordinates portalCoordinates) {
		Set<Portal> portalsInTheWorld = portals.get(world);
		if (portalsInTheWorld == null) {
			return false;
		}
		for (Iterator<Portal> i = portalsInTheWorld.iterator(); i.hasNext(); ) {
			Portal p = i.next();
			if (p.northWestCorner.equals(portalCoordinates)) {
				if (isValidPortal(p)) {
					return true;
				}
				i.remove();
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
			return buildPortal(destinationWorld, sourcePortal.northWestCorner, sourcePortal.targetDimension, sourcePortal.sourceDimension);
		}

		Vector<Portal> candidates = new Vector<Portal>();
		Vector<Portal> invalids = new Vector<Portal>();
		Iterator<Portal> i = portalsInTheWorld.iterator();
		while (i.hasNext()) {
			Portal portal = i.next();
			if (isNearerThan(sourcePortal.northWestCorner, portal.northWestCorner, portalCoordinatesFudgeFactor)) {
				if (isValidPortal(portal)) {
					candidates.add(portal);
				} else {
					invalids.add(portal);
				}
			}
		}
		portalsInTheWorld.removeAll(invalids);
		if (portalsInTheWorld.isEmpty()) {
			portals.remove(destinationWorld);
		}

		if (candidates.isEmpty()) {
			return buildPortal(destinationWorld, sourcePortal.northWestCorner, sourcePortal.targetDimension, sourcePortal.sourceDimension);
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

		EntityPosition lastCheck = lastPortalEntranceCheckPos.get(player);
		if (lastCheck == null) {
			lastPortalEntranceCheckPos.put(player, new EntityPosition(player.worldObj, x, y, z));
		} else if (lastCheck.world == player.worldObj
				&& lastCheck.x == x && lastCheck.y == y && lastCheck.z == z) {
			return;
		} else {
			lastCheck.world = player.worldObj;
			lastCheck.x = x;
			lastCheck.y = y;
			lastCheck.z = z;
		}

		WorldServer world = (WorldServer)player.worldObj;
		ChunkCoordinates position = findPortalShape(world, x, y, z);
		if (position == null) {
			return;
		}

		Set<Portal> portalsInTheWorld = portals.get(world);
		if (portalsInTheWorld == null) {
			return;
		}

		Iterator<Portal> i = portalsInTheWorld.iterator();
		while (i.hasNext()) {
			Portal p = i.next();
			if (p.northWestCorner.equals(position)) {

				if (isValidPortal(p)) {
					Portal destination = getDestinationPortal(p);
					if (destination != null) {
						destination.teleportPlayerTo(player);
					}
					
					return;
				}

				i.remove();
				if (portalsInTheWorld.isEmpty()) {
					portals.remove(world);
				}

				break;
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
					world.setBlock(x + i, y, z + j, Block.stone.blockID);
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

	boolean validatePortal(Portal portal) {
		if (isValidPortal(portal)) {
			return true;
		}

		Set<Portal> portalsInTheWorld = portals.get(portal.worldServer);
		if (portalsInTheWorld != null) {
			portalsInTheWorld.remove(portal);
			if (portalsInTheWorld.isEmpty()) {
				portals.remove(portal.worldServer);
			}
		}

		return false;
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
