package taketsuru11.manyworlds.forge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;

public class ManyWorldsCommand implements ICommand {

	private static final ManyWorldsCommand instance = new ManyWorldsCommand();

	private Map<String, ICommand> subcommands = new HashMap<String, ICommand>();
	
	public static ManyWorldsCommand getInstance() {
		return instance;
	}
	
	@Override
	public int compareTo(Object o) {
		if (! (o instanceof ICommand)) {
			return 0;
		}

		return getCommandName().compareTo(((ICommand)o).getCommandName());
	}

	@Override
	public String getCommandName() {
		return "mw";
	}

	@Override
	public String getCommandUsage(ICommandSender icommandsender) {
		StringBuffer result = new StringBuffer();
		for (String subcommand : subcommands.keySet()) {
			result.append("mw " + subcommand + "\n");
		}
		return result.toString();
	}

	@Override
	public List getCommandAliases() {
		return null;
	}

	@Override
	public void processCommand(ICommandSender icommandsender, String[] astring) {
		ICommand subcommand = subcommands.get(astring[0]);
		if (subcommand == null) {
			throw new WrongUsageException("unknown subcommand", (Object [])astring);
		}
		
		subcommand.processCommand(icommandsender, astring);
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
		if (i == 0) {
			return false;
		}

		ICommand subcommand = subcommands.get(astring[0]);
		if (subcommand == null) {
			return false;
		}
		
		return subcommand.isUsernameIndex(astring, i);
	}

	public void registerCommand(ICommand cmd) {
		if (subcommands.containsKey(cmd.getCommandName())) {
			throw new RuntimeException("CONFLICT: command mw " + cmd.getCommandName());
		}
		subcommands.put(cmd.getCommandName(), cmd);
	}

}
