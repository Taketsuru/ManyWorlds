package taketsuru11.manyworlds.forge;

import java.util.List;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;

public class TeleportCommand implements ICommand {

	@Override
	public int compareTo(Object o) {
		if (! (o instanceof ICommand)) {
			return 0;
		}

		return getCommandName().compareTo(((ICommand)o).getCommandName());
	}

	@Override
	public String getCommandName() {
		return "teleport";
	}

	@Override
	public String getCommandUsage(ICommandSender icommandsender) {
		return "teleport";
	}

	@Override
	public List getCommandAliases() {
		return null;
	}

	@Override
	public void processCommand(ICommandSender icommandsender, String[] astring) {
		int argpos = 1;
		if (icommandsender instanceof EntityPlayerMP) {
			EntityPlayerMP entity = (EntityPlayerMP)icommandsender;
			try {
				int destination = Integer.parseInt(astring[argpos]);
				WorldInfo info = entity.mcServer.worldServerForDimension(destination).getWorldInfo();
				ManyWorldsTeleporter.teleport(entity, destination,
						(double)info.getSpawnX() + 0.5,
						(double)info.getSpawnY() + entity.getYOffset(),
						(double)info.getSpawnZ() + 0.5,
						entity.rotationYaw, entity.rotationPitch);
				return;
			} catch (NumberFormatException e) {
			}
		}
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender icommandsender) {
		return true;
	}

	@Override
	public List addTabCompletionOptions(ICommandSender icommandsender, String[] astring) {
		return null;
	}

	@Override
	public boolean isUsernameIndex(String[] astring, int i) {
		return false;
	}

}
