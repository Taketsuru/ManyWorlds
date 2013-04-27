package taketsuru11.manyworlds.forge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import cpw.mods.fml.common.FMLLog;

import net.minecraft.block.Block;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.oredict.OreDictionary;

public class TossedItemMonitor {
	
	class MonitoredItem {
		EntityItem entityItem;
		int lastX;
		int lastY;
		int lastZ;
		int targetDimension;
		
		MonitoredItem(EntityItem entityItem, int targetDimension) {
			this.entityItem = entityItem;
			lastX = Integer.MAX_VALUE;
			lastY = Integer.MAX_VALUE;
			lastX = Integer.MAX_VALUE;
			this.targetDimension = (char)targetDimension;
		}

		@Override
		public boolean equals(Object x) {
			return x instanceof MonitoredItem && entityItem == ((MonitoredItem)x).entityItem;
		}
		
		@Override
		public int hashCode() {
			return entityItem.hashCode();
		}
	}
	
	class Key {
		String item;
		int dimension;

		Key(String item, int dimension) {
			this.item = item;
			this.dimension = dimension;
		}
	}

	private PortalManager portalManager;
	private Vector<Key> keys = new Vector<Key>();
	private Map<WorldServer, Set<MonitoredItem>> monitoredItems = new HashMap<WorldServer, Set<MonitoredItem>>();

	public TossedItemMonitor(PortalManager portalManager) {
		this.portalManager = portalManager;
	}

	@ForgeSubscribe
	public void onItemTossed(ItemTossEvent event) {
		ItemStack stack = event.entityItem.getEntityItem();
		for (Key key : keys) {
			if (Pattern.matches("\\d+", key.item) && stack.itemID == Integer.parseInt(key.item)) {
				startMonitoring(event, key.dimension);
				return;
			}

			int oreId = OreDictionary.getOreID(stack);
			if (oreId != -1) {
				String name = OreDictionary.getOreName(oreId);
				if (name.equals(key.item)) {
					startMonitoring(event, key.dimension);
					return;
				}
			}
			
			if (stack.getItemName().equals(key.item)) {
				startMonitoring(event, key.dimension);
				return;
			}
		}
	}

	private void startMonitoring(ItemTossEvent event, int targetDimension) {
		WorldServer server = (WorldServer)event.entityItem.worldObj;
		Set<MonitoredItem> set = monitoredItems.get(server);
		if (set == null) {
			set = new HashSet<MonitoredItem>();
			monitoredItems.put(server, set);
		}
		set.add(new MonitoredItem(event.entityItem, targetDimension));
	}

	public void onPostWorldTick(WorldServer worldServer) {
		Set<MonitoredItem> set = monitoredItems.get(worldServer);
		if (set == null) {
			return;
		}

		Vector<MonitoredItem> removedItems = new Vector<MonitoredItem>();

		for (MonitoredItem monitoredItem : set) {
			EntityItem entityItem = monitoredItem.entityItem;

			if (entityItem.isDead) {
				removedItems.add(monitoredItem);
				continue;
			}
			
			if (! entityItem.onGround) {
				continue;
			}
			
			int x = (int)MathHelper.floor_double(entityItem.posX + entityItem.width * 0.5);
			int y = (int)MathHelper.floor_double(entityItem.posY + entityItem.height * 0.5);
			int z = (int)MathHelper.floor_double(entityItem.posZ + entityItem.width * 0.5);
			if (x == monitoredItem.lastX && y == monitoredItem.lastY || z == monitoredItem.lastX) {
				continue;
			}

			monitoredItem.lastX = x;
			monitoredItem.lastY = y;
			monitoredItem.lastZ = z;

			ChunkCoordinates portalCoordinates = portalManager.findPortalShape(worldServer, x, y, z);
			if (portalCoordinates == null) {
				continue;
			}
	
			entityItem.setDead();
			
			if (portalManager.doesPortalExistAt(worldServer, portalCoordinates)) {
				continue;
			}
			
			worldServer.addWeatherEffect(new EntityLightningBolt(worldServer, x, y, z));

			int targetDimension = entityItem.dimension == monitoredItem.targetDimension ? 0 : monitoredItem.targetDimension;
			portalManager.openPortal(worldServer, portalCoordinates, entityItem.dimension, targetDimension);
		}

		set.removeAll(removedItems);
		if (set.isEmpty()) {
			monitoredItems.remove(worldServer);
		}
	}

	public void addKey(String item, int i) {
		keys.add(new Key(item, i));
	}
	
}
