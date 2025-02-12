package com.westeroscraft.westerosblocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.CommandDispatcher;

import com.westeroscraft.westerosblocks.blocks.WCHalfDoorBlock;
import com.westeroscraft.westerosblocks.commands.NVCommand;
import com.westeroscraft.westerosblocks.commands.PTimeCommand;
import com.westeroscraft.westerosblocks.commands.PWeatherCommand;
import com.westeroscraft.westerosblocks.modelexport.ModelExport;
import com.westeroscraft.westerosblocks.modelexport.ModelExportFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;

import net.minecraftforge.network.NetworkRegistry;

import com.westeroscraft.westerosblocks.network.ClientMessageHandler;
import com.westeroscraft.westerosblocks.network.PWeatherMessage;
import com.westeroscraft.westerosblocks.network.PTimeMessage;
import com.westeroscraft.westerosblocks.network.ServerMessageHandler;

import net.minecraftforge.network.NetworkDirection;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(WesterosBlocks.MOD_ID)
public class WesterosBlocks {
	public static final String MOD_ID = "westerosblocks";

	// Network setup
	public static SimpleChannel simpleChannel;    // used to transmit your network messages
    public static final String CHANNEL = "wbchannel";
    public static final String MESSAGE_PROTOCOL_VERSION = "5.10";
    public static final ResourceLocation simpleChannelRL = new ResourceLocation("westerosblocks", CHANNEL);

	// Directly reference a log4j logger.
	public static final Logger log = LogManager.getLogger();

	public static WesterosBlockConfig customConfig;

	// Says where the client and server 'proxy' code is loaded.
	public static Proxy proxy = DistExecutor.safeRunForDist(() -> ClientProxy::new, () -> Proxy::new);

	public static WesterosBlockDef[] customBlockDefs;

	public static HashMap<String, Block> customBlocksByName;

	public static Block[] customBlocks = new Block[0];

	public static Path modConfigPath;
	
	public static WesterosBlockColorMap[] colorMaps;
	
	public static WesterosItemMenuOverrides[] menuOverrides;

	public WesterosBlocks() {
		// Register the doClientStuff method for modloading
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);
		// Register the setup method for load complete
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::loadComplete);
		// Register the doClientStuff method for modloading
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetupEvent);
		// Register the setup method for tile entities
		WesterosBlockDef.TILE_ENTITY_TYPES.register(FMLJavaModLoadingContext.get().getModEventBus());

		// Register ourselves for server and other game events we are interested in
		MinecraftForge.EVENT_BUS.register(this);

		Path configPath = FMLPaths.CONFIGDIR.get();

		modConfigPath = Paths.get(configPath.toAbsolutePath().toString(), MOD_ID);

		// Create the config folder
		try {
			Files.createDirectory(modConfigPath);
		} catch (FileAlreadyExistsException e) {
			// Do nothing
		} catch (IOException e) {
			log.error("Failed to create westerosblocks config directory", e);
		}

		ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, Config.SPEC,
				MOD_ID + "/" + MOD_ID + ".toml");
	}

	private void doClientStuff(final FMLClientSetupEvent event) {
		// do something that can only be done on the client
		log.info("Got game settings {}", event.description());
	}

	@SubscribeEvent
	public void onRegisterCommandEvent(RegisterCommandsEvent event) {
	    CommandDispatcher<CommandSourceStack> commandDispatcher = event.getDispatcher();
		PTimeCommand.register(commandDispatcher);
		PWeatherCommand.register(commandDispatcher);
		NVCommand.register(commandDispatcher);
	}
	
	private void loadComplete(final FMLLoadCompleteEvent event) // PostRegistrationEven
	{
		// Initialize with standard block IDs
		if (Config.blockDevMode.get()) {
			log.info("Block dev mode enabled : block export processing will be done to " + modConfigPath + "/assets/"
					+ MOD_ID);
		}
		// Do blocks state export here
		if (Config.blockDevMode.get()) {
			// Clean up old export
			deleteDirectory(new File(modConfigPath.toFile(), "assets"));
			deleteDirectory(new File(modConfigPath.toFile(), "data"));
			
			for (int i = 0; i < customBlockDefs.length; i++) {
				if (customBlockDefs[i] == null)
					continue;
				Block blk = customBlocksByName.get(customBlockDefs[i].blockName);
				if (blk != null) {
					ModelExport exp = ModelExportFactory.forBlock(blk, customBlockDefs[i], modConfigPath.toFile());
					if (exp != null) {
						try {
							exp.doBlockStateExport();
							exp.doModelExports();
							// If list, roll through choices as legacyBlockID
							if (customBlockDefs[i].legacyBlockIDList != null) {
								for (String legacyid : customBlockDefs[i].legacyBlockIDList) {
									customBlockDefs[i].legacyBlockID = legacyid;
									exp.doWorldConverterMigrate();									
									ModelExport.addWorldConverterItemMap(legacyid, customBlockDefs[i].blockName);
								}
								customBlockDefs[i].legacyBlockID = null;
							}
							else if (customBlockDefs[i].legacyBlockID != null) {
								exp.doWorldConverterMigrate();
								ModelExport.addWorldConverterItemMap(customBlockDefs[i].legacyBlockID, customBlockDefs[i].blockName);
							}
						} catch (IOException iox) {
							log.warn(String.format("Error exporting block %s - %s", blk.getRegistryName(), iox));
						}
					}
				}
			}
			try {
				ModelExport.writeNLSFile(modConfigPath);
			} catch (IOException iox) {
				log.warn(String.format("Error writing NLS - %s", iox));
			}
			try {
				ModelExport.writeTagDataFiles(modConfigPath);
			} catch (IOException iox) {
				log.warn(String.format("Error writing tag data files - %s", iox));
			}
			try {
				ModelExport.writeCustomTagDataFiles(modConfigPath, customConfig);
			} catch (IOException iox) {
				log.warn(String.format("Error writing custom tag data files - %s", iox));
			}
			try {
				ModelExport.writeWorldConverterFile(modConfigPath);
			} catch (IOException iox) {
				log.warn(String.format("Error writing WorldConfig mapping - %s", iox));
			}
			try {
				ModelExport.writeDynmapOverridesFile(modConfigPath);
			} catch (IOException iox) {
				log.warn(String.format("Error writing Dynmap Overrides - %s", iox));
			}
			try {
				ModelExport.writeWorldConverterItemMapFile(modConfigPath);
			} catch (IOException iox) {
				log.warn(String.format("Error writing WorldConfig item mapping - %s", iox));
			}
		}
		proxy.initRenderRegistry();

	}

	public static void crash(Exception x, String msg) {
		throw new ReportedException(new CrashReport(msg, x));
	}

	public static void crash(String msg) {
		crash(new Exception(), msg);
	}

	public static class Config {
		public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
		public static final ForgeConfigSpec SPEC;
		public static final ForgeConfigSpec.BooleanValue snowInTaiga;
		public static final ForgeConfigSpec.BooleanValue blockDevMode;
		public static final ForgeConfigSpec.IntValue autoRestoreTime;
		public static final ForgeConfigSpec.BooleanValue autoRestoreAllHalfDoors;
		public static final ForgeConfigSpec.BooleanValue doorSurviveAny;
		public static final ForgeConfigSpec.BooleanValue doorNoConnect;
		public static final ForgeConfigSpec.IntValue seaLevelOverride;
		public static final ForgeConfigSpec.IntValue particleEmitterStrengthMax;
		public static final ForgeConfigSpec.IntValue particleEmitterRangeMax;

		static {
			BUILDER.comment("Module options");
			snowInTaiga = BUILDER.comment("Enable snow in taiga").define("snowInTaiga", true);
			blockDevMode = BUILDER.comment("Block development mode").define("blockDevMode", false);
			BUILDER.push("autoRestore");
            autoRestoreTime = BUILDER.comment("Number of seconds before auto-restore").defineInRange("autoRestoreTime", 30, 5, 300);
            autoRestoreAllHalfDoors = BUILDER.comment("Auto restore all half-door blocks").define("autoRestoreAllHalfDoors", true);
            BUILDER.pop();
			BUILDER.push("blockMod");
			doorSurviveAny = BUILDER.comment("Allow door to survive on any surface").define("doorSurviveAny", true);
			doorNoConnect = BUILDER.comment("Avoid doors connecting to walls/panes/etc").define("doorNoConnect", true);
            BUILDER.pop();
            BUILDER.push("worldMod");
            seaLevelOverride = BUILDER.comment("Override sea level (default for Westeros=33, 0=disable override)").defineInRange("seaLevelOverride", 33, 0, 639);
            BUILDER.pop();
			BUILDER.push("particleEmitter");
			particleEmitterStrengthMax = BUILDER.comment("Max value of particle strength for particle emitter blocks").defineInRange("particleEmitterStrengthMax", 10, 1, 100);
			particleEmitterRangeMax = BUILDER.comment("Max value of particle strength for particle emitter blocks").defineInRange("particleEmitterRangeMax", 10, 1, 100);
			BUILDER.pop();

			SPEC = BUILDER.build();
		}
	}

	@Mod.EventBusSubscriber(modid = WesterosBlocks.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
	public class ColorHandler {
	    @SubscribeEvent
	    public static void registerItemColors(ColorHandlerEvent.Item event) {
	        for (Block blk : WesterosBlocks.customBlocks) {
	     	   if (blk instanceof WesterosBlockLifecycle) {
	     		   WesterosBlockDef def = ((WesterosBlockLifecycle)blk).getWBDefinition();
	     		   if (def != null) {
	     			   def.registerItemColorHandler(blk, event.getItemColors());
	     		   }
	     	   }
	        }
	        if (WesterosBlocks.colorMaps != null) {
	     	   WesterosBlocks.log.info("Initializing " + WesterosBlocks.colorMaps.length + " custom color maps");
	     	   for (WesterosBlockColorMap map : WesterosBlocks.colorMaps) {
	     		   for (String bn : map.blockNames) {
	     			   Block blk = WesterosBlocks.findBlockByName(bn);
	     			   if (blk != null) {
	     				   WesterosBlockDef.registerVanillaItemColorHandler(bn, blk, map.colorMult, event.getItemColors());
	     			   }
	     		   }
	     	   }
	        }	    	
	    }
	    @SubscribeEvent
	    public static void registerBlockColors(ColorHandlerEvent.Block event) {
	        for (Block blk : WesterosBlocks.customBlocks) {
	     	   if (blk instanceof WesterosBlockLifecycle) {
	     		   WesterosBlockDef def = ((WesterosBlockLifecycle)blk).getWBDefinition();
	     		   if (def != null) {
	     			   def.registerBlockColorHandler(blk, event.getBlockColors());
	     		   }
	     	   }
	        }
	        if (WesterosBlocks.colorMaps != null) {
	     	   WesterosBlocks.log.info("Initializing " + WesterosBlocks.colorMaps.length + " custom color maps");
	     	   for (WesterosBlockColorMap map : WesterosBlocks.colorMaps) {
	     		   for (String bn : map.blockNames) {
	     			   Block blk = WesterosBlocks.findBlockByName(bn);
	     			   if (blk != null) {
	     				   WesterosBlockDef.registerVanillaBlockColorHandler(bn, blk, map.colorMult, event.getBlockColors());
	     			   }
	     		   }
	     	   }
	        }
	    }
	}
	// You can use EventBusSubscriber to automatically subscribe events on the
	// contained class (this is subscribing to the MOD
	// Event bus for receiving Registry Events)
	@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
	public static class RegistryEvents {

		private static boolean didInit = false;
		public static void initialize() {
			if (didInit) return;
			
			// Initialize
			log.info("initialize start");
			WesterosBlockDef.initialize();
			WesterosBlocksCreativeTab.init();
			// If snow-in-taiga
			if (Config.snowInTaiga.get()) {
				Biome b = ForgeRegistries.BIOMES.getValue(new ResourceLocation("minecraft:taiga"));
				if (b != null) {
					b.climateSettings.temperature = -0.5F; 
					b.climateSettings.precipitation = Biome.Precipitation.SNOW;
				}
				log.info("Enabled snow in TAIGA");
			}

			// Read our block definition resource
			try {
				customConfig = loadBlockConfig("/WesterosBlocks.json");
			} catch (BlockConfigNotFoundException ex) {
				crash("WesterosBlocks couldn't find its block definition resource");
				return;
			} catch (JsonSyntaxException iox) {
				crash(iox, "WesterosBlocks couldn't parse its block definition");
				return;
			} catch (JsonIOException iox) {
				crash(iox, "WesterosBlocks couldn't read its block definition");
				return;
			}
			if (customConfig == null) {
				crash("WesterosBlocks couldn't read its block definition");
				return;
			}

			customBlockDefs = getBlockDefs(customConfig);
			log.info("Loaded " + customBlockDefs.length + " block definitions");

			// Validate custom block definitions against old copy (if one exists)
			if (validateDefs(customBlockDefs) == false) {
				log.error("Some block names or states have been removed relative to oldWesterosBlocks.json");
			}

			if (WesterosBlockDef.sanityCheck(customBlockDefs) == false) {
				crash("WesterosBlocks.json failed sanity check");
				return;
			}
			// Register custom tags
			ModelExport.declareCustomTags(customConfig);

			log.info("initialize done");
		}

		@SubscribeEvent
		public static void onBlocksRegistry(final RegistryEvent.Register<Block> event) {
			log.info("block register start");
			// Do initialization, if needed
			initialize();

			// Construct custom block definitions
			List<Block> blklist = new LinkedList<Block>();
			customBlocksByName = new HashMap<String, Block>();
			HashMap<String, Integer> countsByType = new HashMap<String, Integer>();
			int blockcount = 0;
			for (int i = 0; i < customBlockDefs.length; i++) {
				if (customBlockDefs[i] == null)
					continue;
				Block blk = customBlockDefs[i].createBlock();
				if (blk != null) {
					blklist.add(blk);
					customBlocksByName.put(customBlockDefs[i].blockName, blk);
					// Register sound events
					customBlockDefs[i].registerSoundEvents();
					// Add to counts
					Integer cnt = countsByType.get(customBlockDefs[i].blockType);
					cnt = (cnt == null) ? 1 : (cnt+1);
					countsByType.put(customBlockDefs[i].blockType, cnt);					
					blockcount++;
				} else {
					// crash("Invalid block definition for " + customBlockDefs[i].blockName + " -
					// aborted during load()");
					// return;
				}
			}
			customBlocks = blklist.toArray(new Block[blklist.size()]);
			WesterosBlockDef.dumpBlockPerf();

			// Dump information for external mods
			WesterosBlocksCompatibility.dumpBlockSets(customConfig.blockSets, modConfigPath);
			WesterosBlocksCompatibility.dumpWorldPainterConfig(customBlocks, modConfigPath);

			// Brag on block type counts
			log.info("Count of custom blocks by type:");
			for (String type : countsByType.keySet()) {
				log.info(type + ": " + countsByType.get(type) + " blocks");				
			}
			log.info("TOTAL: " + blockcount + " blocks");

			colorMaps = customConfig.colorMaps;
			menuOverrides = customConfig.menuOverrides;

			log.info("block register done");
		}

		@SubscribeEvent
		public static void onItemsRegistry(final RegistryEvent.Register<Item> event) {
			log.info("item register start");
			if (menuOverrides != null) {
				for (WesterosItemMenuOverrides mo : menuOverrides) {
					if (mo.blockNames != null) {
						for (String bn : mo.blockNames) {
							Item itm = ForgeRegistries.ITEMS.getValue(new ResourceLocation(bn));
							if (itm == null) {
								log.warn("Item for " + bn + " not found - cannot override");
							}
							else {
								CreativeModeTab tab = null;
								if (mo.creativeTab != null) {
									tab = WesterosBlockDef.getCreativeTab(mo.creativeTab);
								}
								itm.category = tab;
								//log.info("Item for " + bn + " set to tab " + mo.creativeTab);
							}
						}
					}
				}
			}
			log.info("item register done");
		}
	}

	public static WesterosBlockConfig loadBlockConfig(String filename) throws BlockConfigNotFoundException, JsonParseException {
		// Read our block definition resource
		WesterosBlockConfig config;
		InputStream in = WesterosBlocks.class.getResourceAsStream(filename);
		if (in == null) {
			throw new BlockConfigNotFoundException();
		}
		BufferedReader rdr = new BufferedReader(new InputStreamReader(in));
		Gson gson = new Gson();
		try {
			config = gson.fromJson(rdr, WesterosBlockConfig.class);
		} catch (JsonParseException iox) {
			throw iox;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException iox) {
				}
				;
				in = null;
			}
			if (rdr != null) {
				try {
					rdr.close();
				} catch (IOException iox) {
				}
				;
				rdr = null;
			}
		}
		return config;
	}

	public static class BlockConfigNotFoundException extends Exception {
		public BlockConfigNotFoundException() {
		}
		public BlockConfigNotFoundException(String message) {
			super(message);
		}
	}

	// Expand block set definitions to obtain the full block definition list
	public static WesterosBlockDef[] getBlockDefs(WesterosBlockConfig config) {
		WesterosBlockSetDef[] blockSetDefs = config.blockSets;
		WesterosBlockDef[] blockDefs = config.blocks;
		List<WesterosBlockDef> expandedBlockDefs = new LinkedList<WesterosBlockDef>(Arrays.asList(blockDefs));
		for (int i = 0; i < blockSetDefs.length; i++) {
			if (blockSetDefs[i] == null)
				continue;
			List<WesterosBlockDef> variantBlockDefs = blockSetDefs[i].generateBlockDefs();
			expandedBlockDefs.addAll(variantBlockDefs);
		}
		return expandedBlockDefs.toArray(new WesterosBlockDef[expandedBlockDefs.size()]);
	}

	public static Block findBlockByName(String blkname) {
		Block blk = customBlocksByName.get(blkname);
		if (blk != null) return blk;
		ResourceLocation rl = new ResourceLocation(blkname);
		if (rl.getNamespace().equals(WesterosBlocks.MOD_ID)) {
			blk = customBlocksByName.get(rl.getPath());
		}			
		if (blk != null) {
			return blk;
		}
		blk = ForgeRegistries.BLOCKS.getValue(rl);
		return blk;
	}

	// Load in oldWesterosBlocks.json, if it exists, and use it as a "source of truth" for
	// validating modifications to WesterosBlocks.json.
	// The important things to check are that all of the block names in oldWesterosBlocks.json
	// are preserved, and that all of the state-related attributes for each block are preserved.
	public static boolean validateDefs(WesterosBlockDef[] defs) {
		WesterosBlockDef[] validationDefs;
		try {
			validationDefs = getBlockDefs(loadBlockConfig("/oldWesterosBlocks.json"));
		} catch (BlockConfigNotFoundException ex) {
			log.warn("no oldWesterosBlocks.json found for validation; skipping");
			return true;
		} catch (JsonParseException ex) {
			log.warn("couldn't read oldWesterosBlocks.json block definition; skipping");
			return true;
		}
		if (validationDefs == null) {
			log.warn("couldn't read oldWesterosBlocks.json block definition; skipping");
			return true;
		}

		return WesterosBlockDef.compareBlockDefs(defs, validationDefs);
	}

	private static HashMap<String, SoundEvent> registered_sounds = new HashMap<String, SoundEvent>();

	public static SoundEvent registerSound(String soundName) {
		SoundEvent event = registered_sounds.get(soundName);
		if (event == null) {
			ResourceLocation location = new ResourceLocation(WesterosBlocks.MOD_ID, soundName);
			event = new SoundEvent(location);
			event.setRegistryName(location);
			ForgeRegistries.SOUND_EVENTS.register(event);
			registered_sounds.put(soundName, event);
		}
		return event;
	}

	public static SoundEvent getRegisteredSound(String soundName) {
		return registered_sounds.get(soundName);
	}
	
    @SubscribeEvent
    public void onCommonSetupEvent(FMLCommonSetupEvent event) {
	    simpleChannel = NetworkRegistry.newSimpleChannel(simpleChannelRL, () -> MESSAGE_PROTOCOL_VERSION,
	            ClientMessageHandler::isThisProtocolAcceptedByClient,
	            ServerMessageHandler::isThisProtocolAcceptedByServer);

	    simpleChannel.registerMessage(PTimeMessage.PTIME_MSGID, PTimeMessage.class,
	    		PTimeMessage::encode, PTimeMessage::decode,
	            ClientMessageHandler::onPTimeMessageReceived,
	            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
	    simpleChannel.registerMessage(PWeatherMessage.PWEATHER_MSGID, PWeatherMessage.class,
	    		PWeatherMessage::encode, PWeatherMessage::decode,
	            ClientMessageHandler::onPWeatherMessageReceived,
	            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
	  }
    
    // Directory tree delete
    public static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }
    
	private static class PendingRestore {
		BlockPos pos;
		Level world;
		PendingRestore(Level lvl, BlockPos p) {
			this.world = lvl;
			this.pos = p;
		}
		@Override
		public int hashCode() {
			return pos.hashCode() ^ world.hashCode();
		}
		@Override
		public boolean equals(Object o) {
			if (o instanceof PendingRestore) {
				PendingRestore pdo = (PendingRestore) o;
				return (pdo.world == this.world) && (pdo.pos.asLong() == this.pos.asLong()); 
			}
			return false;
		}
	};
	private static class RestoreInfo {
		long secCount;
		Boolean open;
	};
	private static Map<PendingRestore, RestoreInfo> pendingHalfDoorRestore = new HashMap<PendingRestore, RestoreInfo>();
	private static int ticks = 0;
	private static long secCount = 0;

    public static boolean isAutoRestoreHalfDoor(Block blk) {
    	if (Config.autoRestoreAllHalfDoors.get()) return true;
    	return false;
    }
    public static void setPendingHalfDoorRestore(Level world, BlockPos pos, boolean isOpen, boolean isCreative) {
    	PendingRestore pdc = new PendingRestore(world, pos);
    	RestoreInfo ri = pendingHalfDoorRestore.get(pdc);
    	if ((ri == null) && (!isCreative)) {	// New one, and not creative mode, add record
    		ri = new RestoreInfo();
    		ri.open = isOpen; ri.secCount = secCount + Config.autoRestoreTime.get();
    		pendingHalfDoorRestore.put(pdc, ri);
    	}
    	// Else, if restore record pending, but creative change, drop it
    	else if (ri != null) {
    		if (isCreative) {
    			pendingHalfDoorRestore.remove(pdc);
    		}
    		else {	// Else, reset restore time
    			ri.secCount = secCount + Config.autoRestoreTime.get();
    		}
    	}
    }
    public static void handlePendingHalfDoorRestores(boolean now) {
    	// Handle pending door close checks
    	Set<Entry<PendingRestore, RestoreInfo>> kvset = pendingHalfDoorRestore.entrySet();
    	Iterator<Entry<PendingRestore, RestoreInfo>> iter = kvset.iterator();	// So that we can remove during iteration
    	while (iter.hasNext()) {
    		Entry<PendingRestore, RestoreInfo> kv = iter.next();
			PendingRestore pdc = kv.getKey();
    		RestoreInfo ri = kv.getValue();
    		if (now || (ri.secCount <= secCount)) {
    			BlockState bs = pdc.world.getBlockState(pdc.pos);	// Get the block state
    			if (bs != null) {
    				Block blk = bs.getBlock();
    				if ((blk instanceof WCHalfDoorBlock) && isAutoRestoreHalfDoor(blk)) {	// Still right type of door
    					if (bs.getValue(DoorBlock.OPEN) != ri.open) {	// And still wrong state?
    						WCHalfDoorBlock dblk = (WCHalfDoorBlock)blk;
    						dblk.setOpen(null, pdc.world, bs, pdc.pos, ri.open);
    					}
    				}
    			}
    			iter.remove();	// And remove it from the set
    		}	
    	}
    }

	@SubscribeEvent
    public void countTicks(ServerTickEvent event){
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        if (ticks >= 20) {
        	secCount++;
        	// Handle any pending door restores
        	handlePendingHalfDoorRestores(false);
        	
        	ticks = 0;
        }
    }
	@SubscribeEvent
	public void serverStopping(ServerStoppingEvent event) {
    	// Handle any pending door restores (force immediate)
    	handlePendingHalfDoorRestores(true);
	}

}
