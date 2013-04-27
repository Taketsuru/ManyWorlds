package taketsuru11.manyworlds.forge;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Property;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.IScheduledTickHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.Mod.PostInit;
import cpw.mods.fml.common.Mod.PreInit;
import cpw.mods.fml.common.Mod.ServerStarting;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkMod;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;

@Mod(modid="ManyWorlds", name="ManyWorlds", version="0.0.0")
@NetworkMod(clientSideRequired=false, serverSideRequired=false)
public class ManyWorlds implements IScheduledTickHandler {
	
    class DimensionConfiguration {
    	int dimension;
    	int provider;
    	String key;
   
		public DimensionConfiguration(int dimension, int provider, String key) {
			super();
			this.dimension = dimension;
			this.provider = provider;
			this.key = key;
		}
    }
    
	private DimensionConfiguration[] dimensionConfigurations;
	private TossedItemMonitor tossedItemMonitor;
	private PortalManager portalManager;

    @Instance("ManyWorlds")
    public static ManyWorlds instance;
    
    @PreInit
    public void preInit(FMLPreInitializationEvent event) {
    	Configuration config = new Configuration(event.getSuggestedConfigurationFile());
    	config.load();
    	
    	String[] dimensionsDefaultValues = {
    			"2:overworld:logWood",
    			"3:nether:87",
    			"4:overworld",
    			"5:nether"
    	};

    	Property dimensionsProperty = config.get(Configuration.CATEGORY_GENERAL, "dimensions", dimensionsDefaultValues);
    	dimensionsProperty.comment = "List of dimensions.\n"
    			+ "The format of each element is <dimension number>:<world type>:<key item>.\n"
    			+ "<key item> and the preceding ':' is optional.\n"
    			+ "<world type> is one of 'overworld', 'nether', or 'the_end'.";
    	Pattern dimensionElementPattern = Pattern.compile("\\s*(\\d+):(\\w+)(:(\\w+(:\\d+)?))?\\s*(,|$)");
    	int dimensionNumberGroup = 1;
    	int worldTypeGroup = 2;
    	int keyItemGroup = 4;
    	
    	HashMap<String, Integer> worldTypeMap = new HashMap<String, Integer>();
    	worldTypeMap.put("overworld", 0);
    	worldTypeMap.put("the_end", 1);
    	worldTypeMap.put("nether", 2);

    	Vector<DimensionConfiguration> dimConfigs = new Vector<DimensionConfiguration>();
    	for (String v : dimensionsProperty.valueList) {
    		Matcher m = dimensionElementPattern.matcher(v);
    		if (! m.matches()) {
    			FMLLog.severe("failed to parse an element %s in configuration parameter 'dimensions'.", v);
    			continue;
    		}
    		int dimension = Integer.parseInt(m.group(dimensionNumberGroup));
    		Integer worldType = worldTypeMap.get(m.group(worldTypeGroup).toLowerCase());
    		if (worldType == null) {
    			FMLLog.severe("unknown world type: %s",  m.group(worldTypeGroup));
    			continue;
    		}
    		String key = m.group(keyItemGroup);
    		dimConfigs.add(new DimensionConfiguration(dimension, worldType.intValue(), key));
    	}
    	dimensionConfigurations = new DimensionConfiguration[dimConfigs.size()];
    	dimConfigs.toArray(dimensionConfigurations);
    	
    	config.save();
    }

    @Init
    public void load(FMLInitializationEvent event) {
    	portalManager = new PortalManager();
    	tossedItemMonitor = new TossedItemMonitor(portalManager);

 		DimensionManager.registerProviderType(2, PseudoNetherProvider.class, true);

    	for (int i = 0; i < dimensionConfigurations.length; ++i) {
     		DimensionManager.registerDimension(dimensionConfigurations[i].dimension,
     										   dimensionConfigurations[i].provider);
     		if (dimensionConfigurations[i].key != null) {
     			tossedItemMonitor.addKey(dimensionConfigurations[i].key,
     									 dimensionConfigurations[i].dimension);
     		}
    	}
 
    	MinecraftForge.EVENT_BUS.register(tossedItemMonitor);
    	MinecraftForge.EVENT_BUS.register(portalManager);

    	ManyWorldsCommand.getInstance().registerCommand(new TeleportCommand());

    	TickRegistry.registerScheduledTickHandler(this, Side.SERVER);
    }

    @PostInit
    public void postInit(FMLPostInitializationEvent event) {
    }

    @ServerStarting
    public void serverStarting(FMLServerStartingEvent event) {
    	event.registerServerCommand(ManyWorldsCommand.getInstance());
    }

	@Override
	public void tickStart(EnumSet<TickType> type, Object... tickData) {
	}

	@Override
	public void tickEnd(EnumSet<TickType> type, Object... tickData) {
		if (type.contains(TickType.WORLD)) {
			tossedItemMonitor.onPostWorldTick((WorldServer)tickData[0]);
			portalManager.onPostWorldTick((WorldServer)tickData[0]);
		} else if (type.contains(TickType.PLAYER)) {
			portalManager.onPlayerPostTick((EntityPlayerMP)tickData[0]);
		}
	}

	@Override
	public EnumSet<TickType> ticks() {
		return EnumSet.of(TickType.WORLD, TickType.PLAYER);
	}

	@Override
	public String getLabel() {
		return "ManyWorldsTick";
	}

	@Override
	public int nextTickSpacing() {
		return 8; // We don't need to run so fast.
	}
}
