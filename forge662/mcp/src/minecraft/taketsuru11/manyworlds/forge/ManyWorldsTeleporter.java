package taketsuru11.manyworlds.forge;

import java.util.Iterator;

import cpw.mods.fml.common.registry.GameRegistry;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetServerHandler;
import net.minecraft.network.packet.Packet41EntityEffect;
import net.minecraft.network.packet.Packet70GameEvent;
import net.minecraft.network.packet.Packet9Respawn;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.stats.AchievementList;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;


public class ManyWorldsTeleporter {

	public static void teleport(EntityPlayerMP player, int newDim, double x, double y, double z, float yaw, float pitch) {
		
		MinecraftServer server = player.mcServer;
		ServerConfigurationManager configManager = server.getConfigurationManager();
		NetServerHandler netServerHandler = player.playerNetServerHandler;

		int oldDim = player.dimension;
		WorldServer oldWorldServer = server.worldServerForDimension(oldDim);

		player.dimension = newDim;
		WorldServer newWorldServer = server.worldServerForDimension(player.dimension);

		netServerHandler.sendPacketToPlayer(new Packet9Respawn(player.dimension,
				(byte)newWorldServer.difficultySetting,
				newWorldServer.getWorldInfo().getTerrainType(),
				newWorldServer.getHeight(),
				player.theItemInWorldManager.getGameType()));

		oldWorldServer.removePlayerEntityDangerously(player);
		player.isDead = false;

		newWorldServer.spawnEntityInWorld(player);

		player.setWorld(newWorldServer);

		oldWorldServer.getPlayerManager().removePlayer(player);

		newWorldServer.getPlayerManager().addPlayer(player);
		newWorldServer.theChunkProviderServer.loadChunk((int)x >> 4, (int)z >> 4);

		netServerHandler.setPlayerLocation(x, y, z, yaw, pitch);
		player.theItemInWorldManager.setWorld(newWorldServer);
		configManager.updateTimeAndWeatherForPlayer(player, newWorldServer);
		configManager.syncPlayerInventory(player);

		Iterator iterator = player.getActivePotionEffects().iterator();
		while (iterator.hasNext()) {
			PotionEffect potioneffect = (PotionEffect)iterator.next();
			netServerHandler.sendPacketToPlayer(new Packet41EntityEffect(player.entityId, potioneffect));
		}

		GameRegistry.onPlayerChangedDimension(player);

		player.addExperience(0);
		player.setPlayerHealthUpdated();
	}

}
